# 三、WITH RECURSIVE（递归 CTE）

## 原理

```sql
WITH RECURSIVE cte_name(col) AS (
    -- 锚定部分（初始行）
    SELECT initial_value
    UNION ALL
    -- 递归部分（引用自身）
    SELECT col + step FROM cte_name WHERE col < limit
)
SELECT * FROM cte_name;
```

MySQL 会：
1. 执行锚定部分，得到第 1 行
2. 用第 1 行执行递归部分，得到第 2 行
3. 用第 2 行执行递归部分，得到第 3 行
4. ... 直到 WHERE 条件不满足

## 用法 1：生成 0~23 小时序列

```sql
WITH RECURSIVE hours(hour) AS (
    SELECT 0
    UNION ALL
    SELECT hour + 1 FROM hours WHERE hour < 23
)
SELECT h.hour,
       DATE_FORMAT(STR_TO_DATE(h.hour, '%H'), '%H:00') AS timeLabel,
       COALESCE(r.successful_orders, 0) AS successCount
FROM hours h
LEFT JOIN (
    SELECT stat_hour, SUM(successful_orders) AS successful_orders
    FROM home_stat_report_result
    WHERE stat_date = #{statDate} AND business_type = 1
    GROUP BY stat_hour
) r ON r.stat_hour = h.hour
ORDER BY h.hour;
```

> **来源**：`HomeStatReportMapper.xml#selectIssueTrendHourly`

**效果**：即使某些小时没有数据，图表也会显示该小时（值为 0）。

```
hour | timeLabel | successCount
-----|-----------|-------------
0    | 00:00     | 0
1    | 01:00     | 0
...
9    | 09:00     | 156
10   | 10:00     | 203
...
23   | 23:00     | 12
```

## 用法 2：生成日期序列 + NOT EXISTS 检查缺失天数

```sql
WITH RECURSIVE dates(stat_date) AS (
    SELECT #{startDate}
    UNION ALL
    SELECT DATE_ADD(stat_date, INTERVAL 1 DAY)
    FROM dates
    WHERE stat_date < #{endDate}
)
SELECT COUNT(1) AS missing_days
FROM dates d
WHERE NOT EXISTS (
    SELECT 1
    FROM home_stat_report_batch b
    WHERE b.status = 2
      AND b.start_date <= d.stat_date
      AND b.end_date >= d.stat_date
);
```

> **来源**：`BusinessArchiveMapper.xml#countMissingReportCoverage`

**业务含义**：统计某日期范围内有多少天"没有成功生成统计报表"。

## 用法 3：生成日期序列 + EXISTS 检查覆盖天数

```sql
WITH RECURSIVE days(stat_date) AS (
    SELECT #{startDate}
    UNION ALL
    SELECT stat_date + INTERVAL 1 DAY FROM days WHERE stat_date < #{endDate}
)
SELECT COUNT(1) AS covered_days
FROM days d
WHERE EXISTS (
    SELECT 1
    FROM home_stat_report_result r
    WHERE r.stat_date = d.stat_date
      AND r.business_type IN (1, 2, 3)
);
```

> **来源**：`HomeStatReportMapper.xml#countCoveredDays`

---

