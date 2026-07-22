# 一、CTE 是什么？

CTE（Common Table Expression）即公用表表达式，用 `WITH` 关键字定义一个临时命名结果集，后续查询可以直接引用它。

**基本语法**：

```sql
WITH cte_name AS (
    SELECT ...
)
SELECT * FROM cte_name;
```

**等价于**：把子查询抽出来起了个名字，让 SQL 像读文章一样从上往下看懂。

## 没有 CTE 之前的写法（嵌套子查询）

```sql
SELECT ...
FROM (
    SELECT ...
    FROM (
        SELECT ...
        FROM table_a
        WHERE ...
    ) AS inner_query
    JOIN table_b ON ...
) AS outer_query
WHERE ...
```

## 用 CTE 改写后

```sql
WITH step1 AS (
    SELECT ... FROM table_a WHERE ...
),
step2 AS (
    SELECT ... FROM step1 JOIN table_b ON ...
)
SELECT ... FROM step2 WHERE ...
```

**核心优势**：可读性、可维护性、可复用性。

---

