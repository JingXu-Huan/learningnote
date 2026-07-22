# 九、UPDATE ... JOIN：用查询结果更新主表

## 9.1 场景

项目里会定时刷新订单的“最新日志快照”，避免每次查询列表都现算。

## 9.2 演示表

```sql
order_detail(id, latest_log_id, latest_result)
order_log(id, order_id, result, create_time)
```

## 9.3 示例 SQL

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

## 9.4 学习重点

- `UPDATE ... JOIN` 可以把查询结果直接写回主表。
- 适合做快照字段、冗余字段、汇总字段刷新。

---

