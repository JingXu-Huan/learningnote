# 十四、INLINE 算法的范围查询问题

ShardingSphere 的 `INLINE` 算法适合简单的单分片键 `=` 和 `IN` 路由。

例如：

```sql
SELECT *
FROM t_order
WHERE order_id = 10008;
```

或者：

```sql
SELECT *
FROM t_order
WHERE order_id IN (10008, 10009);
```

但是以下范围查询无法通过简单取模直接确定有限表范围：

```sql
SELECT *
FROM t_order
WHERE order_id BETWEEN 10000 AND 20000;
```

官方文档说明，INLINE 算法默认不允许范围查询；即使开启范围查询支持，也会忽略 INLINE 分片策略并进行全路由。

因此，如果核心业务大量依赖时间或 ID 范围查询，应考虑：

* 时间分片；
* 范围分片；
* 自定义分片算法；
* 将范围查询转移到搜索或分析系统。

---

