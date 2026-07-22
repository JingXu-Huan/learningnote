# 五、GROUP_CONCAT：把多行拼成一列

## 5.1 场景

一笔订单可能有多个出票号，列表页想直接展示成一行字符串。

## 5.2 演示表

```sql
ticket_info(id, order_id, route_order_no)
```

## 5.3 示例 SQL

```sql
SELECT order_id,
       GROUP_CONCAT(route_order_no ORDER BY id SEPARATOR ' / ') AS route_order_nos
FROM ticket_info
GROUP BY order_id;
```

## 5.4 学习重点

- `GROUP_CONCAT` 是“多行转一列”的常见手法。
- 可以配 `ORDER BY` 控制拼接顺序。
- 很适合列表页展示摘要信息。

---

