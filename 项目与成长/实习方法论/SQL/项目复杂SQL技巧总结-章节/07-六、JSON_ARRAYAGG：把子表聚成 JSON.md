# 六、JSON_ARRAYAGG：把子表聚成 JSON

## 6.1 场景

项目里有些接口希望直接返回订单下的票信息数组，前端就不用再二次组装。

## 6.2 演示表

```sql
ticket_info(id, order_id, train_no, from_station, to_station, ticket_count)
```

## 6.3 示例 SQL

```sql
SELECT order_id,
       JSON_ARRAYAGG(
           JSON_OBJECT(
               'trainNo', train_no,
               'from', from_station,
               'to', to_station,
               'count', ticket_count
           )
       ) AS ticket_list
FROM ticket_info
GROUP BY order_id;
```

## 6.4 学习重点

- `JSON_ARRAYAGG + JSON_OBJECT` 适合直接给接口层用。
- 当一对多信息需要原样返回时，比字符串拼接更规范。

---

