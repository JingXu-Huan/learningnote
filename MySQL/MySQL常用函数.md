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