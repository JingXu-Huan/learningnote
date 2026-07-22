# 十五、正确编写业务 SQL

## 1. 查询订单详情

推荐：

```sql
SELECT *
FROM t_order
WHERE user_id = #{userId}
  AND order_id = #{orderId};
```

不推荐：

```sql
SELECT *
FROM t_order
WHERE order_id = #{orderId};
```

前者可以路由到一个数据库和一张表；后者需要访问两个数据库。

---

## 2. 更新订单

推荐：

```sql
UPDATE t_order
SET order_status = #{status},
    update_time = NOW()
WHERE user_id = #{userId}
  AND order_id = #{orderId};
```

更新和删除 SQL 必须尽可能携带完整分片键。

尤其不要执行：

```sql
UPDATE t_order
SET order_status = 'CLOSED'
WHERE order_status = 'UNPAID';
```

这种 SQL 可能被发送到所有订单分片。

---

## 3. 查询用户订单列表

```sql
SELECT order_id,
       user_id,
       order_status,
       total_amount,
       create_time
FROM t_order
WHERE user_id = #{userId}
  AND create_time < #{lastCreateTime}
ORDER BY create_time DESC, order_id DESC
LIMIT 20;
```

由于携带了 `user_id`，查询只访问一个数据库。

因为没有携带 `order_id`，该数据库中的 4 张订单表都可能被访问，并进行排序归并。

---

