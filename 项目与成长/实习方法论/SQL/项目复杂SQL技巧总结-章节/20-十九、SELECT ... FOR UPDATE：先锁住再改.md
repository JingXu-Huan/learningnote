# 十九、SELECT ... FOR UPDATE：先锁住再改

## 19.1 场景

项目里做扣库存、改余额、抢单、状态流转时，经常不是“直接 update”，而是先把目标行锁住，再做判断和更新，避免并发事务同时改同一条数据。

## 19.2 演示表

```sql
seat_stock(train_id, seat_type, remain_count)
```

## 19.3 示例 SQL

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

## 19.4 学习重点

- `FOR UPDATE` 的核心不是“查”，而是**在事务里加排他锁**。
- 常见用途是：扣库存、改余额、抢单、防重复处理。
- 它依赖 MySQL 的**行锁 + 事务 + 当前读**语义，所以要放在事务里看。

## 19.5 一个很重要的迁移提醒

如果你后面学 ClickHouse，要特别注意：

- `SELECT ... FOR UPDATE` 这类 SQL **不能按原思路迁到 ClickHouse**
- 因为它背后依赖的是事务型数据库的并发控制能力
- 所以凡是大量依赖 `FOR UPDATE` 的业务逻辑，通常都更适合继续放在 MySQL

一句话：

**报表统计可以迁 ClickHouse，但扣库存、改余额、抢单这种依赖 `FOR UPDATE` 的核心事务逻辑，别迁。**

---

