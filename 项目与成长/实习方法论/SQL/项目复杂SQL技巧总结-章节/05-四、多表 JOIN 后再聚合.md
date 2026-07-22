# 四、多表 JOIN 后再聚合

## 4.1 场景

项目里的订单列表，经常要同时展示订单主信息、票数、总金额。

## 4.2 演示表

```sql
order_detail(id, partner_order_no, status)
ticket_info(id, order_id, ticket_count)
passenger_info(id, order_id, ticket_price)
```

## 4.3 示例 SQL

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

## 4.4 学习重点

- 多表关联之后，通常要配合 `GROUP BY` 才能回到“一条订单一行”。
- 项目里真正复杂的点，不是 JOIN 本身，而是 JOIN 后如何避免重复统计。

---

