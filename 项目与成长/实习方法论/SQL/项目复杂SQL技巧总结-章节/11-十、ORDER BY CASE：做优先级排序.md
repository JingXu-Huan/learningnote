# 十、ORDER BY CASE：做优先级排序

## 10.1 场景

项目里选“最重要的一条日志”时，不是简单按时间，而是先看是否成功、是否超时，再看时间。

## 10.2 演示表

```sql
order_log(id, order_id, is_success, is_timeout, create_time)
```

## 10.3 示例 SQL

```sql
SELECT id, order_id, is_success, is_timeout
FROM order_log
WHERE order_id = 1001
ORDER BY CASE
             WHEN is_success = 1 THEN 0
             WHEN is_timeout = 1 THEN 1
             ELSE 2
         END,
         create_time DESC,
         id DESC
LIMIT 1;
```

## 10.4 学习重点

- `ORDER BY CASE` 本质上是“自定义排序规则”。
- 特别适合处理业务优先级，而不是单纯时间顺序。

---

