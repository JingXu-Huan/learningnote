# Mybatis动态`SQL`

* 随着用户的输入或外部条件的变化而变化的SQL语句,我们称为 动态SQL。

* `<if>`  判断条件是否成立,如果成立为`true`,拼接`SQL`

  ```mysql
  <if test = "gender != null"
  	and e.gender = #{gender}
  </if>
  ```


  * `<where>`  判断条件是否成立,来生成`where`关键字,并且自动去除前面多余的`and`和`or`关键字。

    ```mysql
    <select id="list" resultType="com.itheima.pojo.Emp">
    SELECT e .* , d.name AS deptName FROM emp e LEFT JOIN dept d ON e.dept_id = d.id
    <where>
    
    <if test="name != null and name != ' '">
    e.name like concat('%',#{name},'%')
    </if>
    
    <if test="gender != null">
    and e.gender = #{gender}
    </if>
    
    <if test="begin != null and end != null">
    and e.entry_date between #{begin} and #{end}
    </if>
    
    </where> order by e.update_time desc
    </select>
    ```
    
* `<foreach>` 用于批量插入数据的标签
  
  ```xml
  <insert id="insertBatch">
  	insert into emp_expr (emp_id, begin, end, company, job) values
  	<foreach collection = "exprList" item="expr" separator=",">
  	    (#{expr.empId}, #{expr.begin}, #{expr.end}, #{expr.company}, #{expr.job})
  	</foreach>
  </insert>
  ```
  
  * `<foreach>`属性说明:
  
  1. **collection:集合名称**
  
  2. **item:集合遍历出来的元素/项**
  
  3. **separator:每一次遍历使用的分隔符** （可选）
  
  4. **open:遍历开始前拼接的片段**（可选）
  
  5. **close:遍历结束后拼接的片段**（可选）

