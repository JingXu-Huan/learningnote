# 十三、DELETE ... JOIN：按关联关系删数据

## 13.1 场景

项目归档后，要删除已经迁走的推送记录，但删除条件来自订单表。

## 13.2 演示表

```sql
push_order(id, order_id)
archive_order(id)
```

## 13.3 示例 SQL

```sql
DELETE po
FROM push_order po
JOIN archive_order ao ON ao.id = po.order_id;
```

## 13.4 学习重点

- MySQL 支持 `DELETE 表别名 FROM ... JOIN ...`。
- 适合“删 A 表，但条件来自 B 表”的场景。

---

