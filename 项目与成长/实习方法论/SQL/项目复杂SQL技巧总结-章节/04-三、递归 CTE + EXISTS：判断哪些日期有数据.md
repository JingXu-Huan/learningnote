# 三、递归 CTE + EXISTS：判断哪些日期有数据

## 3.1 场景

项目里经常要判断一个日期区间内，哪些天已经生成过统计结果。

## 3.2 演示表

```sql
daily_report(stat_date, report_type)
```

## 3.3 示例 SQL
>INTERVAL 是 SQL 里表示时间间隔的关键字，常用于对日期/时间做加减运算。 
>语法格式：INTERVAL 数值 时间单位。
```sql
WITH RECURSIVE days AS (
    SELECT DATE('2026-07-01') AS d
    UNION ALL
    SELECT d + INTERVAL 1 DAY
    FROM days
    WHERE d < DATE('2026-07-07')
)
SELECT d
FROM days
WHERE EXISTS (
    SELECT 1
    FROM daily_report r
    WHERE r.stat_date = d
      AND r.report_type = 'ISSUE'
);
```

## 3.4 学习重点

- 先生成日期序列，再逐天判断。
- `EXISTS` 比“查出来再回 Java 循环”更直接。
- 适合做覆盖率、补数检查、对账检查。

---

