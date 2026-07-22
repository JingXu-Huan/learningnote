# 一、CTE：把大查询拆成几层小步骤

## 1.1 场景

项目里做首页日报统计时，不是一次性把所有逻辑写进一个 `SELECT`，而是先筛基础订单，再聚合票数，再聚合金额，最后统一汇总。

## 1.2 演示表

```sql
order_detail(id, supplier_id, status, create_time)
ticket_info(id, order_id, ticket_count)
passenger_info(id, order_id, ticket_price)
```

## 1.3 示例 SQL

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

## 1.4 学习重点

- `WITH` 适合把“筛选”“聚合”“汇总”拆开写。
- 每一层只做一件事，可读性比一条超长 SQL 好很多。
- 在项目里，CTE 常用于报表、统计、批量汇总。

---

