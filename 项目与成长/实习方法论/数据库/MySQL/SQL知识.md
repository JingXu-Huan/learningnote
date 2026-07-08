# 积累SQL学到的新知识

## 关于 **单引号之内**不能使用 `‘#{}’` 这种写法的解决方案：

  ```mysql
  -- 解决方案
  -- 1. 使用 ${} 来替代 #{} -> 不安全 采用字符串拼接 会有SQL注入的风险。不采用。
  -- 2. 使用 mysql 的字符串拼接函数,推荐此方案。代码如下:
  
  select concat ('%','#{}','%');
  -- 执行结果: %#{}%
  ```

  **为什么不能在引号之内这样写？**

  ​	在 MyBatis 的 XML 或注解 SQL 里，`#{…}` 占位符会被预编译为 `?`，所以如果你直接写在单引号里面, 就会是这样`'?'`，MyBatis就会把它当作字面文本（或导致语法错误），无法完成你想要的动态匹配。

  **如果你把 `#{…}` 包在单引号里**

  ```mysql
  WHERE name = '#{name}'
  ```

   那在 MyBatis 预编译阶段，它依旧会把 `#{name}` 换成 `?`，但**单引号不动**，结果就是：

  ```mysql
  WHERE name = '?'
  ```

  换句话说，数据库会去匹配 **文本** `"?"`，而不是把 `?` 识别为占位符。

* **在插入数据之后，我们如何获取到它的主键值？**

  解决方案：使用`@Options`注解。主键返回。

  ```java
  // keyProperty = "id" 把数据库自动生成的主键值，设置回 Java 对象中的 id 属性。
  @Options(useGeneratedKeys = true,keyProperty = "id")
  ```

## `MySQL`的流程控制函数  `case when then else end`

```mysql
-- 方式一
select (case emp.job
            when 1 then '班主任'
            when 2 then '讲师'
            when 3 then '学工主管'
            when 4 then '教研主管'
            when 5 then '咨询师'
            else '其他' end) pos,
       count(*)              num
from emp
group by job order by num;
```

```mysql
--方式二
select (case
            when job = 1 then '班主任'
            when job = 2 then '讲师'
            when job = 3 then '学工主管'
            when job = 4 then '教研主管'
            when job = 5 then '咨询师'
            else '其他' end) pos,
       count(*)              num
from emp
group by job
order by num;
```

## MySQL的`<if>`标签

### 1️⃣ `IF` **函数**（用于 SQL 语句中）

**语法：**

```mysql
IF(expr, value_if_true, value_if_false)
```

- **expr**：判断条件（TRUE 或 FALSE）
- **value_if_true**：条件成立时返回的值
- **value_if_false**：条件不成立时返回的值

**示例：**

```mysql
SELECT name,
       score,
       IF(score >= 60, '及格', '不及格') AS result
FROM students;
```

📌 功能类似 Excel 的 `IF`。

------

### 2️⃣ `IF ... THEN ...` **语句**（用于存储过程/BEGIN...END 中）

**语法：**

```mysql
IF condition THEN
    statements;
ELSEIF condition THEN
    statements;
ELSE
    statements;
END IF;
```

- 用在 **存储过程、触发器、函数**等流程控制中
- 需要配合 `END IF` 结束

**示例：**

```mysql
BEGIN
    IF score >= 85 THEN
        SET grade = '优秀';
    ELSEIF score >= 60 THEN
        SET grade = '及格';
    ELSE
        SET grade = '不及格';
    END IF;
END;
```

------

### 3️⃣ 区别

| 类型               | 用途         | 位置                      |
| ------------------ | ------------ | ------------------------- |
| `IF()` 函数        | 返回一个值   | SELECT、WHERE 等 SQL 语句 |
| `IF ... THEN` 语句 | 执行一段流程 | 存储过程、BEGIN...END     |

### 示例：

```xml
<mapper namespace="com.jingxu.tlias.mapper.EmpMapper">
    <update id="update">
        update emp
        <set>
            <if test="username != null and username != ''">
                username = #{username},
            </if>
            <if test="password != null and password != ''">
                password = #{password},
            </if>
            <if test="name != null and name != ''">
                name = #{name},
            </if>
            <if test="gender != null">
                gender = #{gender},
            </if>
            <if test="phone != null and phone != ''">
                phone = #{phone},
            </if>
            <if test="job != null">
                job = #{job},
            </if>
            <if test="salary != null">
                salary = #{salary},
            </if>
            <if test="image != null and image != ''">
                image = #{image},
            </if>
            <if test="entryDate != null">
                entry_date = #{entryDate},
            </if>
            <if test="deptId != null">
                dept_id = #{deptId},
            </if>
            <if test="updateTime != null">
                update_time = #{updateTime},
            </if>
        </set>
        where id = #{id}
    </update>
  
```
#### `set` 的功能

- 用来生成 `UPDATE` 语句中 **SET 子句** 的字段列表
- 会**自动去掉多余的逗号**（避免 SQL 报错）

---

## 🔗 相关笔记

- [[动态SQL]] —— MyBatis `<if>` / `<where>` / `<foreach>` 动态拼接
- [[窗口函数]] —— 流程控制函数（IF / CASE）在窗口函数中的应用
- [[MySQL索引/索引和索引下推]] —— SQL 写法对索引命中的影响
- [[MySQL常用函数]] —— MySQL 内置函数速查
- [[多表查询]] —— JOIN 查询基础
