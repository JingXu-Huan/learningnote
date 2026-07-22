# 二、CTE 的六大使用场景

## 场景 1：先圈范围，再聚合（scoped_order 模式）

> 项目中出现频率最高的 CTE 模式

**业务需求**：统计今天的出票订单数、票数、成功票数、金额。

**思路**：
1. 先筛出"今天有出票动作的订单 ID 集合" → `scoped_order`
2. 用这个 ID 集合去关联票数表 → `ti_scope`
3. 用这个 ID 集合去关联金额表 → `pi_scope`
4. 最终 JOIN 起来做条件聚合

```sql
-- 第一步：圈出今天的订单ID
WITH scoped_order AS (
    SELECT od.id AS order_detail_id
    FROM order_detail AS od
    WHERE od.is_delete = 0
      AND od.latest_order_log_customer_service_id IS NOT NULL
      AND od.ticket_order_issue_time >= TIMESTAMP(DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR))
      AND od.ticket_order_issue_time <  TIMESTAMP(DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR) + INTERVAL 1 DAY)
),
-- 第二步：用订单ID聚合票数
ti_scope AS (
    SELECT ti.order_id, SUM(IFNULL(ti.ticket_count, 0)) AS ticket_count
    FROM ticket_info AS ti
    JOIN scoped_order AS so ON so.order_detail_id = ti.order_id
    GROUP BY ti.order_id
),
-- 第三步：用订单ID聚合金额
pi_scope AS (
    SELECT pi.order_id, SUM(IFNULL(pi.real_ticket_price, 0)) AS order_amount
    FROM passenger_info AS pi
    JOIN scoped_order AS so ON so.order_detail_id = pi.order_id
    GROUP BY pi.order_id
)
-- 第四步：最终统计
SELECT
    COUNT(1) AS orderCount,
    SUM(COALESCE(ti_scope.ticket_count, 0)) AS ticketCount,
    SUM(CASE WHEN od.ticket_order_query_status = 1
             THEN COALESCE(ti_scope.ticket_count, 0) ELSE 0 END) AS successTicketCount,
    SUM(CASE WHEN od.ticket_order_query_status = 1
             THEN COALESCE(pi_scope.order_amount, 0) ELSE 0 END) AS orderAmount
FROM scoped_order AS so
JOIN order_detail AS od ON od.id = so.order_detail_id
LEFT JOIN ti_scope ON ti_scope.order_id = od.id
LEFT JOIN pi_scope ON pi_scope.order_id = od.id;
```

> **来源**：`TicketOrderMapper.xml#getTodayTicketOrderStat`

**为什么用 CTE 而不是直接 JOIN？**
- `scoped_order` 只扫一次 order_detail 的索引，后续 `ti_scope`、`pi_scope` 直接用 ID 集合关联，避免在大表上反复扫描
- 如果把 ticket_info 和 passenger_info 都直接 JOIN 到 order_detail，会产生笛卡尔积（一个订单有多张票、多个乘客），导致 COUNT 和 SUM 膨胀

---

## 场景 2：多层 CTE 递进加工（流水线模式）

> 每一层 CTE 在前一层基础上加工，像流水线一样逐步构建最终数据

**业务需求**：首页出票日报——从原始订单中提炼基础日志，再关联辅助表，最终按三维度展开。

```sql
INSERT INTO home_stat_report_result (...)

-- 层1：从订单明细提取基础日志（含超时判定逻辑）
WITH base_logs AS (
    SELECT od.id,
           od.latest_order_log_supplier_info_id AS supplier_info_id,
           od.latest_order_log_customer_service_id AS customer_service_id,
           od.latest_order_log_is_success AS is_success,
           -- 超时判定：状态=4 且反馈失败 且过期时间已过 → 算超时
           CASE
               WHEN od.ticket_order_query_status = 2 THEN 1
               WHEN od.ticket_order_query_status = 4
                    AND COALESCE(od.ret_feedback_result_status, -1) = 0
                    AND od.ttl_expect_expire_time <= CURRENT_TIMESTAMP
               THEN 1
               ELSE 0
           END AS timeout_event,
           -- 统计时间：超时单用过期时间，正常单用出票时间
           CASE
               WHEN od.ticket_order_query_status = 4
                    AND COALESCE(od.ret_feedback_result_status, -1) = 0
                    AND od.ttl_expect_expire_time <= CURRENT_TIMESTAMP
               THEN od.ttl_expect_expire_time
               ELSE od.ticket_order_issue_time
           END AS stat_time
    FROM order_detail od
    LEFT JOIN order_log latest_log ON latest_log.id = od.latest_order_log_id
    WHERE od.is_delete = 0
      AND od.latest_order_log_id IS NOT NULL
      AND od.ticket_order_issue_time BETWEEN #{startTime} AND #{endTime}
),

-- 层2：从 base_logs 提取去重订单ID
scoped_orders AS (
    SELECT DISTINCT order_detail_id
    FROM base_logs
),

-- 层3：关联票数表
ticket_scope AS (
    SELECT ti.order_id, SUM(IFNULL(ti.ticket_count, 0)) AS ticket_count
    FROM ticket_info ti
    JOIN scoped_orders so ON so.order_detail_id = ti.order_id
    GROUP BY ti.order_id
),

-- 层4：关联金额表
passenger_scope AS (
    SELECT pi.order_id, SUM(IFNULL(pi.real_ticket_price, 0)) AS order_amount
    FROM passenger_info pi
    JOIN scoped_orders so ON so.order_detail_id = pi.order_id
    GROUP BY pi.order_id
),

-- 层5：汇总所有维度，LEFT JOIN 辅助表补全名称
src AS (
    SELECT DATE(bl.stat_time) AS stat_date,
           bl.supplier_info_id,
           bl.customer_service_id,
           COALESCE(NULLIF(si.company, ''), NULLIF(si.contacts, ''),
                    CONCAT('代售点', bl.supplier_info_id)) AS supplier_name,
           COALESCE(NULLIF(cs.name, ''), NULLIF(cs.sys_user_user_name, ''),
                    CONCAT('客服', bl.customer_service_id)) AS customer_name,
           bl.id AS log_id,
           IFNULL(ts.ticket_count, 0) AS ticket_count,
           IFNULL(ps.order_amount, 0) AS order_amount,
           bl.is_success, bl.timeout_event
    FROM base_logs bl
    LEFT JOIN ticket_scope ts ON ts.order_id = bl.order_detail_id
    LEFT JOIN passenger_scope ps ON ps.order_id = bl.order_detail_id
    LEFT JOIN supplier_info si ON si.id = bl.supplier_info_id
    LEFT JOIN customer_service cs ON cs.id = bl.customer_service_id
)

-- 最终：三维度展开 + 条件聚合
SELECT stat_date, -1, 1, object_type, object_id, ...,
       COUNT(log_id),
       COALESCE(SUM(ticket_count), 0),
       COALESCE(SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END), 0),
       COALESCE(SUM(CASE WHEN is_success = 1 THEN ticket_count ELSE 0 END), 0),
       COALESCE(SUM(CASE WHEN timeout_event = 1 THEN 1 ELSE 0 END), 0),
       ...
FROM (
    -- 维度0：全局汇总
    SELECT stat_date, 0 AS object_type, 0 AS object_id,
           NULL AS supplier_info_id, NULL AS customer_service_id, ...
    FROM src
    UNION ALL
    -- 维度1：按供应商
    SELECT stat_date, 1, supplier_info_id,
           supplier_info_id, NULL, ...
    FROM src WHERE supplier_info_id IS NOT NULL
    UNION ALL
    -- 维度2：按客服
    SELECT stat_date, 2, customer_service_id,
           supplier_info_id, customer_service_id, ...
    FROM src WHERE customer_service_id IS NOT NULL
) x
GROUP BY stat_date, object_type, object_id, ...;
```

> **来源**：`HomeStatReportMapper.xml#insertIssueDaily`

**5 层 CTE 的数据流向**：

```
order_detail → base_logs → scoped_orders → ticket_scope  ─┐
                                    ↓                      ├→ src → 三维度展开 → GROUP BY
                               passenger_scope ────────────┘
```

---

## 场景 3：CTE 内 UNION ALL 合并多路数据源

> 当数据来源有多个"时间口径"时，用 CTE + UNION ALL 统一

**业务需求**：统计出票成功趋势，需兼容三种时间字段（反馈时间 > 更新时间 > 创建时间）。

```sql
-- 路1：有 ret_feedback_time 的订单（最精确）
WITH filtered_logs AS (
    SELECT ol.order_detail_id,
           od.ret_feedback_time AS stat_time
    FROM order_detail od
    JOIN order_log ol ON ol.order_detail_id = od.id
    WHERE od.ret_feedback_time IS NOT NULL
      AND od.ret_feedback_time BETWEEN #{startTime} AND #{endTime}

    UNION ALL

    -- 路2：无 ret_feedback_time，用 order_log.update_time
    SELECT ol.order_detail_id,
           ol.update_time AS stat_time
    FROM order_log ol
    LEFT JOIN order_detail od ON od.id = ol.order_detail_id
    WHERE od.ret_feedback_time IS NULL
      AND ol.update_time BETWEEN #{startTime} AND #{endTime}

    UNION ALL

    -- 路3：无 update_time，用 order_log.create_time
    SELECT ol.order_detail_id,
           ol.create_time AS stat_time
    FROM order_log ol
    LEFT JOIN order_detail od ON od.id = ol.order_detail_id
    WHERE od.ret_feedback_time IS NULL
      AND ol.update_time IS NULL
      AND ol.create_time BETWEEN #{startTime} AND #{endTime}
),
scoped_orders AS (
    SELECT DISTINCT fl.order_detail_id
    FROM filtered_logs fl
),
ticket_scope AS (
    SELECT ti.order_id, SUM(IFNULL(ti.ticket_count, 0)) AS ticket_count
    FROM ticket_info ti
    JOIN scoped_orders so ON so.order_detail_id = ti.order_id
    GROUP BY ti.order_id
)
SELECT HOUR(fl.stat_time) AS hour,
       COUNT(1) AS successCount,
       COALESCE(SUM(COALESCE(ticket_scope.ticket_count, 0)), 0) AS successTicketCount
FROM filtered_logs fl
LEFT JOIN ticket_scope ON ticket_scope.order_id = fl.order_detail_id
GROUP BY HOUR(fl.stat_time)
ORDER BY HOUR(fl.stat_time);
```

> **来源**：`OrderLogMapper.xml#getTicketSuccessTrend`

**三路回退逻辑**：

```
优先用 ret_feedback_time（精确出票反馈时间）
  ↓ 为 NULL 时
退而用 order_log.update_time
  ↓ 也为 NULL 时
最后用 order_log.create_time
```

**关键点**：三路 WHERE 条件互斥（`IS NOT NULL` / `IS NULL`），保证同一个订单不会重复出现。

---

## 场景 4：CTE + 三层 CTE 逐级聚合（ticket_metrics 模式）

> 当需要"先按订单聚合，再按供应商/客服聚合"两级聚合时

**业务需求**：按供应商维度统计出票票数指标（总票数、成功票数、超时票数）。

```sql
-- 层1：按供应商+订单分组，计算每个订单的日志指标
WITH grouped_logs AS (
    SELECT
        ol.supplier_info_id AS supplierInfoId,
        ol.order_detail_id,
        COUNT(ol.id) AS totalLogCount,
        SUM(CASE WHEN ol.is_success = 1 THEN 1 ELSE 0 END) AS successLogCount,
        SUM(CASE WHEN ol.processing_status = -1 THEN 1 ELSE 0 END) AS timeoutLogCount
    FROM order_log AS ol
    WHERE ol.is_delete = 0 AND ol.create_time BETWEEN #{start} AND #{end}
    GROUP BY ol.supplier_info_id, ol.order_detail_id
),
-- 层2：订单维度的票数聚合
ticket_scope AS (
    SELECT ti.order_id, SUM(IFNULL(ti.ticket_count, 0)) AS ticket_count
    FROM ticket_info ti
    GROUP BY ti.order_id
),
-- 层3：将票数 × 日志数 做加权聚合
ticket_metrics AS (
    SELECT
        gl.supplierInfoId,
        SUM(COALESCE(ts.ticket_count, 0) * gl.totalLogCount) AS totalTickets,
        SUM(COALESCE(ts.ticket_count, 0) * gl.successLogCount) AS successfulTickets,
        SUM(COALESCE(ts.ticket_count, 0) * gl.timeoutLogCount) AS timeoutTickets
    FROM grouped_logs AS gl
    LEFT JOIN ticket_scope AS ts ON ts.order_id = gl.order_detail_id
    GROUP BY gl.supplierInfoId
)
SELECT tm.supplierInfoId,
       si.contacts AS supplierName,
       tm.totalTickets, tm.successfulTickets, tm.timeoutTickets
FROM ticket_metrics AS tm
LEFT JOIN supplier_info AS si ON si.id = tm.supplierInfoId;
```

> **来源**：`OrderLogMapper.xml#getVSupplierCustomerOrderStatTicketMetrics`

**三层聚合的逻辑**：

```
order_log（原始日志）
    ↓ GROUP BY supplier + order
grouped_logs（每订单的日志统计）
    ↓ LEFT JOIN ticket_scope（票数）
    ↓ 加权计算：票数 × 日志数
ticket_metrics（每供应商的票数指标）
    ↓ LEFT JOIN supplier_info（名称）
最终结果
```

**为什么用加权计算 `ticket_count * logCount`？**
- 因为一个订单可能有 N 条日志（重复出票），每条日志对应的票数应该算 N 份
- 直接 SUM(ticket_count) 会丢失"重复出票"的信息

---

## 场景 5：CTE 做"先聚合，后关联"（order_metrics 模式）

> 避免在主查询中做复杂的 GROUP BY + LEFT JOIN 维度表

**业务需求**：按供应商统计订单指标（总单数、成功数、超时数、平均处理时长、成功率）。

```sql
WITH order_metrics AS (
    -- 先在 order_log 上完成所有聚合计算
    SELECT
        ol.supplier_info_id AS supplierInfoId,
        COUNT(ol.id) AS totalOrders,
        SUM(CASE WHEN ol.is_success = 1 THEN 1 ELSE 0 END) AS successfulOrders,
        SUM(CASE WHEN ol.processing_status = -1 THEN 1 ELSE 0 END) AS timeoutOrders,
        ROUND(IFNULL(AVG(CASE WHEN ol.is_success = 1 THEN ol.processing_time END), 0), 1) AS avgProcessingTime,
        ROUND(
            IFNULL(SUM(CASE WHEN ol.is_success = 1 THEN 1 ELSE 0 END) / NULLIF(COUNT(ol.id), 0) * 100, 0),
            2
        ) AS successRate
    FROM order_log AS ol
    WHERE ol.is_delete = 0 AND ol.create_time BETWEEN #{start} AND #{end}
    GROUP BY ol.supplier_info_id
)
-- 聚合完成后再关联维度表取名称
SELECT
    om.supplierInfoId,
    si.contacts AS supplierName,
    om.totalOrders, om.successfulOrders, om.timeoutOrders,
    om.avgProcessingTime, om.successRate
FROM order_metrics AS om
LEFT JOIN supplier_info AS si ON si.id = om.supplierInfoId
ORDER BY om.successfulOrders DESC;
```

> **来源**：`OrderLogMapper.xml#getVSupplierCustomerOrderStatOrderMetrics`

**对比：不用 CTE 的写法**

```sql
-- 直接 JOIN 维度表再 GROUP BY → 需要在 GROUP BY 中包含维度表字段
SELECT
    ol.supplier_info_id,
    si.contacts,  -- 要放进 GROUP BY
    COUNT(ol.id), ...
FROM order_log ol
LEFT JOIN supplier_info si ON si.id = ol.supplier_info_id
GROUP BY ol.supplier_info_id, si.contacts  -- 多了维度字段
```

**CTE 的优势**：先用纯业务表聚合，再 JOIN 维度表——GROUP BY 更干净，且维度表只需查一次。

---

## 场景 6：CTE + 动态桶索引（图表分组模式）

> 同一个查询按不同 periodType 使用不同的分组函数

**业务需求**：统计图表支持按小时/按周/按月三种粒度展示。

```sql
WITH scoped_orders AS (
    SELECT od.id AS order_detail_id,
           od.latest_order_log_supplier_info_id AS supplier_info_id,
           od.latest_order_log_customer_service_id AS customer_service_id,
           od.ticket_order_issue_time AS stat_time
    FROM order_detail od
    WHERE od.is_delete = 0
      AND od.latest_order_log_is_success = 1
      AND od.ticket_order_issue_time BETWEEN #{startTime} AND #{endTime}
),
ticket_scope AS (
    SELECT ti.order_id, SUM(IFNULL(ti.ticket_count, 0)) AS ticket_count
    FROM ticket_info ti
    JOIN scoped_orders so ON so.order_detail_id = ti.order_id
    GROUP BY ti.order_id
)
SELECT
    -- 桶索引：根据 periodType 动态选择分组函数
    CASE
        WHEN #{periodType} = 'WEEK'  THEN WEEKDAY(src.stat_time) + 1   -- 1~7 (周一~周日)
        WHEN #{periodType} = 'MONTH' THEN DAYOFMONTH(src.stat_time)    -- 1~31
        ELSE HOUR(src.stat_time)                                         -- 0~23
    END AS bucket_index,
    src.supplier_info_id,
    src.customer_service_id,
    COALESCE(SUM(src.ticket_count), 0) AS metric_value,
    COUNT(1) AS source_row_count
FROM (
    SELECT scoped_orders.*, COALESCE(ticket_scope.ticket_count, 0) AS ticket_count
    FROM scoped_orders
    LEFT JOIN ticket_scope ON ticket_scope.order_id = scoped_orders.order_detail_id
) src
GROUP BY bucket_index, src.supplier_info_id, src.customer_service_id;
```

> **来源**：`TicketStatChartMapper.xml#selectIssueRows`

**核心技巧**：`CASE WHEN` 在 `GROUP BY` 的列上动态切换分组函数，一套 SQL 支持三种图表。

---

