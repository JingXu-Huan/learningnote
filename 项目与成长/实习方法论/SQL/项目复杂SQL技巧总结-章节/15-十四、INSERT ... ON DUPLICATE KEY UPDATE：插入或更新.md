# 十四、INSERT ... ON DUPLICATE KEY UPDATE：插入或更新

## 14.1 场景

项目统计表、汇总表、对账表，经常要做 UPSERT。

## 14.2 演示表

```sql
daily_supplier_stat(stat_date, supplier_id, order_count)
UNIQUE KEY uk_stat(stat_date, supplier_id)
```

## 14.3 示例 SQL

```sql
INSERT INTO daily_supplier_stat(stat_date, supplier_id, order_count)
VALUES ('2026-07-08', 101, 20)
ON DUPLICATE KEY UPDATE
    order_count = order_count + VALUES(order_count);
```

## 14.4 学习重点

- 有则更新，无则插入。
- 非常适合日报、库存、汇总计数。

---

