# 十一、UNION ALL：合并多种来源的数据

## 11.1 场景

项目里统计成功金额时，可能要兼容多个时间来源字段，不能只看一种。

## 11.2 演示表

```sql
order_success_by_feedback(order_id, amount)
order_success_by_update(order_id, amount)
order_success_by_create(order_id, amount)
```

## 11.3 示例 SQL

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

## 11.4 学习重点

- `UNION ALL` 是“多路数据源合并”的标准写法。
- 如果数据源本身可能重复，再额外考虑去重。

---

