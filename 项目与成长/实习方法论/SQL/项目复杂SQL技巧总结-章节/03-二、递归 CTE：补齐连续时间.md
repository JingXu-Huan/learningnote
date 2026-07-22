# 二、递归 CTE：补齐连续时间

## 2.1 场景

项目里做小时趋势图时，必须把 `0~23` 点全部返回，哪怕某些小时没有数据，也要补 0。

## 2.2 演示表

```sql
issue_hour_stat(stat_hour, success_count)
```

## 2.3 示例 SQL

```sql
WITH RECURSIVE hours AS (
    SELECT 0 AS hour
    UNION ALL
    SELECT hour + 1
    FROM hours
    WHERE hour < 23
)
SELECT h.hour,
       COALESCE(s.success_count, 0) AS success_count
FROM hours h
LEFT JOIN issue_hour_stat s ON s.stat_hour = h.hour
ORDER BY h.hour;
```

## 2.4 学习重点

- `WITH RECURSIVE` 可以生成连续序列。
- `LEFT JOIN + COALESCE` 用来补空数据。
- 这类写法很适合图表、日报、月报。

---

