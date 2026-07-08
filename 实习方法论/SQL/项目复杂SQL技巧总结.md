# 项目复杂 SQL 技巧总结

> 来源：火车票务系统（MyBatis + MySQL 8.0）
> 整理时间：2026-07-08
> 改写原则：保留项目中的“思路”，但示例 SQL 使用更小的演示表，避免一上来就看超长生产语句。

---

## 怎么看这篇总结

这篇文档不再直接贴项目里的超长 SQL，而是按下面的方式整理：

1. 先说这个技巧在项目里解决什么问题。
2. 再给一组更小的演示表。
3. 最后写一条可以独立看懂的短 SQL。

这样学的时候，更容易先掌握模式，再回头看项目 SQL。

---

## 一、CTE：把大查询拆成几层小步骤

### 1.1 场景

项目里做首页日报统计时，不是一次性把所有逻辑写进一个 `SELECT`，而是先筛基础订单，再聚合票数，再聚合金额，最后统一汇总。

### 1.2 演示表

```sql
order_detail(id, supplier_id, status, create_time)
ticket_info(id, order_id, ticket_count)
passenger_info(id, order_id, ticket_price)
```

### 1.3 示例 SQL

```sql
WITH base_order AS (
    SELECT id, supplier_id
    FROM order_detail
    WHERE status = 'SUCCESS'
),
ticket_sum AS (
    SELECT order_id, SUM(ticket_count) AS ticket_count
    FROM ticket_info
    GROUP BY order_id
),
amount_sum AS (
    SELECT order_id, SUM(ticket_price) AS amount
    FROM passenger_info
    GROUP BY order_id
)
SELECT bo.supplier_id,
       COUNT(*) AS order_count,
       COALESCE(SUM(ts.ticket_count), 0) AS ticket_count,
       COALESCE(SUM(a.amount), 0) AS total_amount
FROM base_order bo
LEFT JOIN ticket_sum ts ON ts.order_id = bo.id
LEFT JOIN amount_sum a ON a.order_id = bo.id
GROUP BY bo.supplier_id;
```

### 1.4 学习重点

- `WITH` 适合把“筛选”“聚合”“汇总”拆开写。
- 每一层只做一件事，可读性比一条超长 SQL 好很多。
- 在项目里，CTE 常用于报表、统计、批量汇总。

---

## 二、递归 CTE：补齐连续时间

### 2.1 场景

项目里做小时趋势图时，必须把 `0~23` 点全部返回，哪怕某些小时没有数据，也要补 0。

### 2.2 演示表

```sql
issue_hour_stat(stat_hour, success_count)
```

### 2.3 示例 SQL

```sql
WITH RECURSIVE hours AS (
    SELECT 0 AS hour
    UNION ALL
    SELECT hour + 1
    FROM hours
    WHERE hour < 23
)
SELECT h.hour,
       COALESCE(s.success_count, 0) AS success_count
FROM hours h
LEFT JOIN issue_hour_stat s ON s.stat_hour = h.hour
ORDER BY h.hour;
```

### 2.4 学习重点

- `WITH RECURSIVE` 可以生成连续序列。
- `LEFT JOIN + COALESCE` 用来补空数据。
- 这类写法很适合图表、日报、月报。

---

## 三、递归 CTE + EXISTS：判断哪些日期有数据

### 3.1 场景

项目里经常要判断一个日期区间内，哪些天已经生成过统计结果。

### 3.2 演示表

```sql
daily_report(stat_date, report_type)
```

### 3.3 示例 SQL
>INTERVAL 是 SQL 里表示时间间隔的关键字，常用于对日期/时间做加减运算。 
>语法格式：INTERVAL 数值 时间单位。
```sql
WITH RECURSIVE days AS (
    SELECT DATE('2026-07-01') AS d
    UNION ALL
    SELECT d + INTERVAL 1 DAY
    FROM days
    WHERE d < DATE('2026-07-07')
)
SELECT d
FROM days
WHERE EXISTS (
    SELECT 1
    FROM daily_report r
    WHERE r.stat_date = d
      AND r.report_type = 'ISSUE'
);
```

### 3.4 学习重点

- 先生成日期序列，再逐天判断。
- `EXISTS` 比“查出来再回 Java 循环”更直接。
- 适合做覆盖率、补数检查、对账检查。

---

## 四、多表 JOIN 后再聚合

### 4.1 场景

项目里的订单列表，经常要同时展示订单主信息、票数、总金额。

### 4.2 演示表

```sql
order_detail(id, partner_order_no, status)
ticket_info(id, order_id, ticket_count)
passenger_info(id, order_id, ticket_price)
```

### 4.3 示例 SQL

```sql
SELECT od.id,
       od.partner_order_no,
       SUM(ti.ticket_count) AS ticket_count,
       SUM(pi.ticket_price) AS total_amount
FROM order_detail od
LEFT JOIN ticket_info ti ON ti.order_id = od.id
LEFT JOIN passenger_info pi ON pi.order_id = od.id
WHERE od.status = 'SUCCESS'
GROUP BY od.id, od.partner_order_no;
```

### 4.4 学习重点

- 多表关联之后，通常要配合 `GROUP BY` 才能回到“一条订单一行”。
- 项目里真正复杂的点，不是 JOIN 本身，而是 JOIN 后如何避免重复统计。

---

## 五、GROUP_CONCAT：把多行拼成一列

### 5.1 场景

一笔订单可能有多个出票号，列表页想直接展示成一行字符串。

### 5.2 演示表

```sql
ticket_info(id, order_id, route_order_no)
```

### 5.3 示例 SQL

```sql
SELECT order_id,
       GROUP_CONCAT(route_order_no ORDER BY id SEPARATOR ' / ') AS route_order_nos
FROM ticket_info
GROUP BY order_id;
```

### 5.4 学习重点

- `GROUP_CONCAT` 是“多行转一列”的常见手法。
- 可以配 `ORDER BY` 控制拼接顺序。
- 很适合列表页展示摘要信息。

---

## 六、JSON_ARRAYAGG：把子表聚成 JSON

### 6.1 场景

项目里有些接口希望直接返回订单下的票信息数组，前端就不用再二次组装。

### 6.2 演示表

```sql
ticket_info(id, order_id, train_no, from_station, to_station, ticket_count)
```

### 6.3 示例 SQL

```sql
SELECT order_id,
       JSON_ARRAYAGG(
           JSON_OBJECT(
               'trainNo', train_no,
               'from', from_station,
               'to', to_station,
               'count', ticket_count
           )
       ) AS ticket_list
FROM ticket_info
GROUP BY order_id;
```

### 6.4 学习重点

- `JSON_ARRAYAGG + JSON_OBJECT` 适合直接给接口层用。
- 当一对多信息需要原样返回时，比字符串拼接更规范。

---

## 七、CASE WHEN：按条件切换字段或规则

### 7.1 场景

项目里同一张通知表，可能同时包含出票、改签、退票三种业务，展示字段取值规则不同。

### 7.2 演示表

```sql
notice(id, notice_type, issue_agent, change_agent, refund_agent)
```

### 7.3 示例 SQL

```sql
SELECT id,
       notice_type,
       CASE notice_type
           WHEN 'ISSUE' THEN issue_agent
           WHEN 'CHANGE' THEN change_agent
           WHEN 'REFUND' THEN refund_agent
       END AS agent_code
FROM notice;
```

### 7.4 学习重点

- `CASE WHEN` 适合做“同一列，不同场景取不同值”。
- 在项目里，常见于状态映射、字段回退、优先级排序。

---

## 八、子查询里取“最新一条”

### 8.1 场景

订单有多条日志，但主表只想挂住“最近一次处理结果”。

### 8.2 演示表

```sql
order_detail(id, latest_log_id)
order_log(id, order_id, result, create_time)
```

### 8.3 示例 SQL

```sql
SELECT od.id,
       (
           SELECT ol.result
           FROM order_log ol
           WHERE ol.order_id = od.id
           ORDER BY ol.create_time DESC, ol.id DESC
           LIMIT 1
       ) AS latest_result
FROM order_detail od;
```

### 8.4 学习重点

- 相关子查询常用来拿“每个主记录的最新一条子记录”。
- 如果量很大，要考虑索引和改写方式。

---

## 九、UPDATE ... JOIN：用查询结果更新主表

### 9.1 场景

项目里会定时刷新订单的“最新日志快照”，避免每次查询列表都现算。

### 9.2 演示表

```sql
order_detail(id, latest_log_id, latest_result)
order_log(id, order_id, result, create_time)
```

### 9.3 示例 SQL

```sql
UPDATE order_detail od
JOIN (
    SELECT ol1.order_id, ol1.id, ol1.result
    FROM order_log ol1
    JOIN (
        SELECT order_id, MAX(id) AS max_id
        FROM order_log
        GROUP BY order_id
    ) t ON t.max_id = ol1.id
) latest ON latest.order_id = od.id
SET od.latest_log_id = latest.id,
    od.latest_result = latest.result;
```

### 9.4 学习重点

- `UPDATE ... JOIN` 可以把查询结果直接写回主表。
- 适合做快照字段、冗余字段、汇总字段刷新。

---

## 十、ORDER BY CASE：做优先级排序

### 10.1 场景

项目里选“最重要的一条日志”时，不是简单按时间，而是先看是否成功、是否超时，再看时间。

### 10.2 演示表

```sql
order_log(id, order_id, is_success, is_timeout, create_time)
```

### 10.3 示例 SQL

```sql
SELECT id, order_id, is_success, is_timeout
FROM order_log
WHERE order_id = 1001
ORDER BY CASE
             WHEN is_success = 1 THEN 0
             WHEN is_timeout = 1 THEN 1
             ELSE 2
         END,
         create_time DESC,
         id DESC
LIMIT 1;
```

### 10.4 学习重点

- `ORDER BY CASE` 本质上是“自定义排序规则”。
- 特别适合处理业务优先级，而不是单纯时间顺序。

---

## 十一、UNION ALL：合并多种来源的数据

### 11.1 场景

项目里统计成功金额时，可能要兼容多个时间来源字段，不能只看一种。

### 11.2 演示表

```sql
order_success_by_feedback(order_id, amount)
order_success_by_update(order_id, amount)
order_success_by_create(order_id, amount)
```

### 11.3 示例 SQL

```sql
SELECT SUM(amount) AS total_amount
FROM (
    SELECT order_id, amount FROM order_success_by_feedback
    UNION ALL
    SELECT order_id, amount FROM order_success_by_update
    UNION ALL
    SELECT order_id, amount FROM order_success_by_create
) t;
```

### 11.4 学习重点

- `UNION ALL` 是“多路数据源合并”的标准写法。
- 如果数据源本身可能重复，再额外考虑去重。

---

## 十二、分页时先分组再取详情

### 12.1 场景

项目里有些列表是一对多结构，不能直接 JOIN 后分页，否则一条业务数据会被拆成多行。

### 12.2 演示表

```sql
change_pool(id, order_no, create_time)
change_record(id, order_no, seat_type)
```

### 12.3 示例 SQL

先取本页业务主键：

```sql
SELECT order_no
FROM change_pool
GROUP BY order_no
ORDER BY MAX(id) DESC
LIMIT 0, 10;
```

再按这批主键查详情：

```sql
SELECT cp.order_no, cr.seat_type
FROM change_pool cp
LEFT JOIN change_record cr ON cr.order_no = cp.order_no
WHERE cp.order_no IN ('A001', 'A002', 'A003');
```

### 12.4 学习重点

- 先按业务键分页，再回查详情，是项目里很常见的写法。
- 适合一对多列表、池子列表、通知列表。

---

## 十三、DELETE ... JOIN：按关联关系删数据

### 13.1 场景

项目归档后，要删除已经迁走的推送记录，但删除条件来自订单表。

### 13.2 演示表

```sql
push_order(id, order_id)
archive_order(id)
```

### 13.3 示例 SQL

```sql
DELETE po
FROM push_order po
JOIN archive_order ao ON ao.id = po.order_id;
```

### 13.4 学习重点

- MySQL 支持 `DELETE 表别名 FROM ... JOIN ...`。
- 适合“删 A 表，但条件来自 B 表”的场景。

---

## 十四、INSERT ... ON DUPLICATE KEY UPDATE：插入或更新

### 14.1 场景

项目统计表、汇总表、对账表，经常要做 UPSERT。

### 14.2 演示表

```sql
daily_supplier_stat(stat_date, supplier_id, order_count)
UNIQUE KEY uk_stat(stat_date, supplier_id)
```

### 14.3 示例 SQL

```sql
INSERT INTO daily_supplier_stat(stat_date, supplier_id, order_count)
VALUES ('2026-07-08', 101, 20)
ON DUPLICATE KEY UPDATE
    order_count = order_count + VALUES(order_count);
```

### 14.4 学习重点

- 有则更新，无则插入。
- 非常适合日报、库存、汇总计数。

---

## 十五、CASE WHEN 批量更新

### 15.1 场景

项目里偶尔需要一条 SQL 更新多条记录，但每条记录的值不一样。

### 15.2 演示表

```sql
tk_pool(id, status, handler_name)
```

### 15.3 示例 SQL

```sql
UPDATE tk_pool
SET status = CASE id
                 WHEN 1 THEN 2
                 WHEN 2 THEN 3
                 WHEN 3 THEN 4
             END,
    handler_name = CASE id
                       WHEN 1 THEN '张三'
                       WHEN 2 THEN '李四'
                       WHEN 3 THEN '王五'
                   END
WHERE id IN (1, 2, 3);
```

### 15.4 学习重点

- 一条 SQL 批量更新不同值。
- 适合中等批量，不适合无限堆大。

---

## 十六、COALESCE / NULLIF：做字段回退

### 16.1 场景

项目里经常遇到字段可能是 `NULL`，也可能是空字符串，要统一回退。

### 16.2 演示表

```sql
supplier(id, company_name, contact_name)
```

### 16.3 示例 SQL

```sql
SELECT id,
       COALESCE(NULLIF(company_name, ''), contact_name, '未知供应商') AS display_name
FROM supplier;
```

### 16.4 学习重点

- `NULLIF(company_name, '')` 先把空串转成 `NULL`。
- `COALESCE` 再按顺序找第一个可用值。
- 这是项目里非常高频的写法。

---

## 十七、时间边界：用“>= 今天零点 且 < 明天零点”

### 17.1 场景

项目里查“今天的数据”时，通常不用 `23:59:59`，而是直接写成左闭右开区间。

### 17.2 演示表

```sql
order_detail(id, ret_feedback_time)
```

### 17.3 示例 SQL

```sql
SELECT *
FROM order_detail
WHERE ret_feedback_time >= '2026-07-08 00:00:00'
  AND ret_feedback_time < '2026-07-09 00:00:00';
```

### 17.4 学习重点

- 这是时间查询里最稳的一种写法。
- 能避免毫秒精度、时分秒边界漏数。

---

## 十八、函数索引：让不同业务类型走不同唯一规则

### 18.1 场景

项目里有些对账表会按不同 `type` 使用不同唯一键，不想拆多张表。

### 18.2 演示表

```sql
trade_compare(
    id,
    group_type,
    account_no,
    refund_no,
    trade_no
)
```

### 18.3 示例 SQL

```sql
CREATE TABLE trade_compare (
    id BIGINT PRIMARY KEY,
    group_type TINYINT NOT NULL,
    account_no VARCHAR(64),
    refund_no VARCHAR(64),
    trade_no VARCHAR(64),
    UNIQUE KEY uk_refund (
        (CASE WHEN group_type = 2 THEN account_no ELSE NULL END),
        (CASE WHEN group_type = 2 THEN refund_no ELSE NULL END)
    )
);
```

### 18.4 学习重点

- MySQL 8.0 支持表达式索引。
- `ELSE NULL` 的意思是：不满足条件的行不参与这组唯一约束。

---

## 十九、SELECT ... FOR UPDATE：先锁住再改

### 19.1 场景

项目里做扣库存、改余额、抢单、状态流转时，经常不是“直接 update”，而是先把目标行锁住，再做判断和更新，避免并发事务同时改同一条数据。

### 19.2 演示表

```sql
seat_stock(train_id, seat_type, remain_count)
```

### 19.3 示例 SQL

```sql
BEGIN;

SELECT train_id, seat_type, remain_count
FROM seat_stock
WHERE train_id = 1001
  AND seat_type = 'SECOND_CLASS'
FOR UPDATE;

UPDATE seat_stock
SET remain_count = remain_count - 1
WHERE train_id = 1001
  AND seat_type = 'SECOND_CLASS'
  AND remain_count > 0;

COMMIT;
```

### 19.4 学习重点

- `FOR UPDATE` 的核心不是“查”，而是**在事务里加排他锁**。
- 常见用途是：扣库存、改余额、抢单、防重复处理。
- 它依赖 MySQL 的**行锁 + 事务 + 当前读**语义，所以要放在事务里看。

### 19.5 一个很重要的迁移提醒

如果你后面学 ClickHouse，要特别注意：

- `SELECT ... FOR UPDATE` 这类 SQL **不能按原思路迁到 ClickHouse**
- 因为它背后依赖的是事务型数据库的并发控制能力
- 所以凡是大量依赖 `FOR UPDATE` 的业务逻辑，通常都更适合继续放在 MySQL

一句话：

**报表统计可以迁 ClickHouse，但扣库存、改余额、抢单这种依赖 `FOR UPDATE` 的核心事务逻辑，别迁。**

---

## 二十、存储过程 + information_schema：幂等加索引

### 20.1 场景

项目做归档表初始化时，希望脚本重复执行也不报错。

### 20.2 示例 SQL

```sql
DROP PROCEDURE IF EXISTS add_idx;
DELIMITER $$
CREATE PROCEDURE add_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'order_detail_his'
          AND index_name = 'idx_ret_time'
    ) THEN
        ALTER TABLE order_detail_his
        ADD INDEX idx_ret_time(ret_feedback_time);
    END IF;
END$$
DELIMITER ;
```

### 20.3 学习重点

- 用 `information_schema.statistics` 判断索引是否存在。
- 适合部署脚本、初始化脚本、归档脚本。

---

## 二十一、最值得你优先掌握的 8 个模式

如果你是为了实习、面试、接项目需求，优先掌握下面这 8 个：

1. `WITH / CTE`：拆复杂查询。
2. `WITH RECURSIVE`：补时间序列、补日期序列。
3. `LEFT JOIN + GROUP BY`：多表汇总。
4. `GROUP_CONCAT / JSON_ARRAYAGG`：一对多结果聚合。
5. `CASE WHEN`：字段切换、优先级排序、批量更新。
6. `UPDATE ... JOIN`：按查询结果回写主表。
7. `UNION ALL`：多路来源合并。
8. `INSERT ... ON DUPLICATE KEY UPDATE`：统计表 UPSERT。

---

## 附录：项目里这些技巧大概落在哪些表

为了以后你回项目源码时不迷路，可以按这个关系去找：

```text
order_detail（订单主表）
  ├─ order_log（订单日志）
  ├─ ticket_info（车票信息）
  ├─ passenger_info（乘客信息）
  └─ push_order_info（推送信息）

tk_pool（退票池）
  └─ refund_ticket_info（退票详情）

tk_tc_pool（改签池）
  └─ change_ticket_info（改签详情）
```

你真正要学的不是“把项目 SQL 背下来”，而是识别它属于哪一种模式。模式认出来了，长 SQL 只是这些模式的组合。
