# MySQL常用函数总结

## 1. 字符串函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `CONCAT(s1,s2,...)` | 拼接字符串 | `CONCAT('Hello', 'World')` → HelloWorld |
| `CONCAT_WS(分隔符, s1, s2,...)` | 用分隔符拼接 | `CONCAT_WS('-', 'a', 'b')` → a-b |
| `LENGTH(str)` | 获取字节长度 | |
| `CHAR_LENGTH(str)` | 获取字符长度 | |
| `SUBSTRING(str, pos, len)` | 截取字符串 | |
| `UPPER(str)` / `LOWER(str)` | 转大小写 | |
| `TRIM(str)` / `LTRIM(str)` / `RTRIM(str)` | 去除空格 | |
| `REPLACE(str, old, new)` | 替换字符串 | |
| `LEFT(str, n)` / `RIGHT(str, n)` | 取左/右n个字符 | |

## 2. 数值函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `ABS(x)` | 绝对值 | |
| `CEIL(x)` / `FLOOR(x)` | 向上/下取整 | |
| `ROUND(x, d)` | 四舍五入，d为小数位 | |
| `MOD(x, y)` | 取余 | |
| `POWER(x, y)` / `SQRT(x)` | 幂运算/平方根 | |
| `TRUNCATE(x, d)` | 截断小数 | |

## 3. 日期时间函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `NOW()` / `SYSDATE()` | 当前日期时间 | |
| `CURDATE()` / `CURRENT_DATE()` | 当前日期 | |
| `CURTIME()` / `CURRENT_TIME()` | 当前时间 | |
| `YEAR(date)` / `MONTH(date)` / `DAY(date)` | 提取年月日 | |
| `DATE_FORMAT(date, format)` | 格式化日期 | `DATE_FORMAT(NOW(), '%Y-%m-%d')` |
| `DATE_ADD(date, INTERVAL expr unit)` | 日期加运算 | |
| `DATEDIFF(date1, date2)` | 日期差（天） | |
| `TIMESTAMPDIFF(unit, date1, date2)` | 指定单位差 | |
| `UNIX_TIMESTAMP()` | 转Unix时间戳 | |

## 4. 流程控制函数

### CASE WHEN THEN

```mysql
-- 方式一：简单表达式
SELECT CASE job
    WHEN 1 THEN '班主任'
    WHEN 2 THEN '讲师'
    ELSE '其他' END AS pos
FROM emp;

-- 方式二：搜索表达式
SELECT CASE
    WHEN score >= 85 THEN '优秀'
    WHEN score >= 60 THEN '及格'
    ELSE '不及格' END AS result
FROM students;
```

### IF函数

```mysql
IF(expr, value_if_true, value_if_false)
-- 示例
SELECT name, IF(score >= 60, '及格', '不及格') AS result FROM students;
```

### IFNULL / COALESCE

```mysql
IFNULL(expr1, expr2)        -- expr1为NULL时返回expr2
COALESCE(expr1, expr2, ...) -- 返回第一个非NULL值
```

## 5. 聚合函数

| 函数 | 说明 |
|------|------|
| `COUNT(expr)` | 计数 |
| `SUM(expr)` | 求和 |
| `AVG(expr)` | 平均值 |
| `MAX(expr)` | 最大值 |
| `MIN(expr)` | 最小值 |
| `GROUP_CONCAT(expr)` | 字符串拼接聚合 |

## 6. 条件判断函数

| 函数 | 说明 |
|------|------|
| `IFNULL(expr1, expr2)` | NULL替换 |
| `NULLIF(expr1, expr2)` | 相等返回NULL |
| `IF(expr1, expr2, expr3)` | 条件判断 |

## 7. 类型转换函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `CAST(expr AS type)` | 类型转换 | `CAST('123' AS SIGNED)` |
| `CONVERT(expr, type)` | 类型转换 | `CONVERT('123', SIGNED)` |

## 8. 其他常用函数

| 函数 | 说明 |
|------|------|
| `ISNULL(expr)` | 判断是否为NULL |
| `DISTINCT` | 去重 |
| `LIMIT offset, count` | 分页限制 |
| `COALESCE(v1,v2,...)` | 返回第一个非NULL值 |

## 9. 条件查询示例

```mysql
-- IF + GROUP BY 统计及格/不及格人数
SELECT IF(score >= 60, '及格', '不及格') AS result, COUNT(*) AS num
FROM students
GROUP BY result;

-- CASE WHEN THEN + GROUP BY 多条件统计
SELECT CASE
    WHEN job = 1 THEN '班主任'
    WHEN job = 2 THEN '讲师'
    WHEN job = 3 THEN '学工主管'
    ELSE '其他' END AS pos, COUNT(*) AS num
FROM emp
GROUP BY job
ORDER BY num;
```

## 10. 窗口函数（MySQL 8.0+）😎😎😎

窗口函数（Window Function）又称**分析函数**，能在保留每行明细的同时，对一组相关行进行聚合或排名计算。**不会把多行合并为一行**，这是它与 `GROUP BY` 聚合的关键区别。

### 10.1 语法

```mysql
函数名([参数]) OVER (
    [PARTITION BY 分组列]    -- 可选，按谁分组（类似GROUP BY）
    [ORDER BY 排序列 [ASC|DESC]]   -- 可选，窗口内排序
    [ROWS/RANGE 窗口帧]      -- 可选，限定计算范围
)
```

- `PARTITION BY`：把数据分成多个"窗口"，各窗口独立计算。
- `ORDER BY`：决定窗口内行的顺序（排名、累计等依赖此顺序）。
- 不写 `PARTITION BY` 则整张表视为一个窗口。

### 10.2 排名函数

| 函数 | 说明 | 排名特点 |
|------|------|----------|
| `ROW_NUMBER()` | 连续编号 | 1,2,3,4,5（不重复） |
| `RANK()` | 跳跃排名 | 1,1,3,4,5（同分同名次跳号） |
| `DENSE_RANK()` | 连续排名 | 1,1,2,3,4（同分同名次不跳号） |
| `NTILE(n)` | 分桶编号 | 把结果分成 n 桶，返回桶号 |

```mysql
-- 按部门分组，按工资降序排名
SELECT
    name,
    dept,
    salary,
    ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn,
    RANK()       OVER (PARTITION BY dept ORDER BY salary DESC) AS rk,
    DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS drk
FROM emp;

-- 取每个部门工资前3的员工
SELECT * FROM (
    SELECT
        name, dept, salary,
        ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn
    FROM emp
) t
WHERE rn <= 3;
```

### 10.3 聚合类窗口函数

普通聚合函数加 `OVER()` 后就变成了窗口函数，**不会减少行数**：

| 函数 | 窗口场景 |
|------|----------|
| `SUM(expr) OVER(...)` | 累计求和 / 分组内总和 |
| `AVG(expr) OVER(...)` | 移动平均 / 分组内平均 |
| `COUNT(expr) OVER(...)` | 累计计数 |
| `MAX(expr) OVER(...)` / `MIN(expr) OVER(...)` | 移动最值 |

```mysql
-- 累计求和
SELECT
    id, name, salary,
    SUM(salary) OVER (ORDER BY id) AS cumulative_sum,
    SUM(salary) OVER (PARTITION BY dept ORDER BY id) AS dept_cumulative
FROM emp;

-- 移动平均（当前行 + 前1行 + 后1行）
SELECT
    id, salary,
    AVG(salary) OVER (
        ORDER BY id
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS moving_avg
FROM emp;
```

### 10.4 取值函数

| 函数 | 说明 |
|------|------|
| `LAG(expr, n, default)` | 取当前行**之前**第 n 行的值（默认 n=1） |
| `LEAD(expr, n, default)` | 取当前行**之后**第 n 行的值 |
| `FIRST_VALUE(expr)` | 窗口内第一行的值 |
| `LAST_VALUE(expr)` | 窗口内最后一行的值（注意窗口帧） |
| `NTH_VALUE(expr, n)` | 窗口内第 n 行的值 |

```mysql
-- 计算环比（上个月和当前月的差值）
SELECT
    month, sales,
    LAG(sales, 1, 0) OVER (ORDER BY month)         AS last_month,
    LEAD(sales, 1, 0) OVER (ORDER BY month)        AS next_month,
    sales - LAG(sales, 1, 0) OVER (ORDER BY month) AS diff
FROM monthly_sales;

-- 取每个部门工资最高/最低的人
SELECT name, dept, salary
FROM (
    SELECT
        name, dept, salary,
        FIRST_VALUE(name) OVER (PARTITION BY dept ORDER BY salary DESC) AS top_name
    FROM emp
) t
WHERE name = top_name;
```

### 10.5 窗口帧（ROWS / RANGE）

通过 `ROWS` 或 `RANGE` 精确控制参与计算的行的范围：

| 关键字 | 含义 |
|--------|------|
| `UNBOUNDED PRECEDING` | 分区第一行 |
| `n PRECEDING` | 当前行往前 n 行 |
| `CURRENT ROW` | 当前行 |
| `n FOLLOWING` | 当前行往后 n 行 |
| `UNBOUNDED FOLLOWING` | 分区最后一行 |

```mysql
-- 常见组合：累计求和
SUM(salary) OVER (PARTITION BY dept ORDER BY id
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)

-- 从当前行到分区末尾
SUM(salary) OVER (PARTITION BY dept ORDER BY id
    ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING)
```

### 10.6 窗口函数 vs GROUP BY

| 维度 | GROUP BY + 聚合 | 窗口函数 |
|------|-----------------|----------|
| 结果行数 | 每组一行 | 保留每行明细 |
| 同时显示明细和聚合 | 不行（需要子查询/JOIN） | 一条 SQL 搞定 |
| 排序支持 | 需再 `ORDER BY` | 内置 `OVER(ORDER BY ...)` |
| 排名/取上下行 | 难以实现 | 内置支持 |

### 10.7 一句话速记

> **分组用 `PARTITION BY`，排序用 `ORDER BY`，要聚合但保留行就用窗口函数。** 排名三剑客：`ROW_NUMBER`（连续）/ `RANK`（跳号）/ `DENSE_RANK`（不跳号），上下行比对用 `LAG` / `LEAD`。

------