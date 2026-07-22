# 十五、CASE WHEN 批量更新

## 15.1 场景

项目里偶尔需要一条 SQL 更新多条记录，但每条记录的值不一样。

## 15.2 演示表

```sql
tk_pool(id, status, handler_name)
```

## 15.3 示例 SQL

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

## 15.4 学习重点

- 一条 SQL 批量更新不同值。
- 适合中等批量，不适合无限堆大。

---

