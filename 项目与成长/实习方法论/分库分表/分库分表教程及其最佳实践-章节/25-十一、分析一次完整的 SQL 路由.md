# 十一、分析一次完整的 SQL 路由

假设执行：

```sql
SELECT *
FROM t_order
WHERE user_id = 101
  AND order_id = 10008;
```

## 1. 分库计算

```text
101 % 2 = 1
```

目标数据库：

```text
ds_1
```

## 2. 分表计算

```text
10008 % 4 = 0
```

目标表：

```text
t_order_0
```

## 3. 真实 SQL

逻辑 SQL：

```sql
SELECT *
FROM t_order
WHERE user_id = 101
  AND order_id = 10008;
```

经过解析、路由和改写后，发送到数据库的真实 SQL类似于：

```sql
SELECT *
FROM t_order_0
WHERE user_id = 101
  AND order_id = 10008;
```

执行节点：

```text
ds_1.t_order_0
```

ShardingSphere 的分片执行过程主要包括 SQL 解析、路由、改写、执行和结果归并。

---

