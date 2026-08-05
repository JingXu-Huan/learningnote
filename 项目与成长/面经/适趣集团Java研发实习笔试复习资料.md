# 适趣集团 Java 研发实习笔试复习资料

> 整理日期：2026-08-04  
> 适用场景：适趣集团研发实习笔试，17 题，题型包含单选、多选、问答、代码，范围为 Java、Spring Boot、MySQL、Spring MVC。  
> 考试要求：正式考试必须脱离 AI 独立完成；本文用于考前学习与限时模拟，不用于考试中检索答案。

## 目录

- [一、先看结论](#一先看结论)
- [二、公司与岗位调查](#二公司与岗位调查)
- [三、120 分钟答题策略](#三120-分钟答题策略)
- [四、复习优先级](#四复习优先级)
- [五、Java 高频考点](#五java-高频考点)
- [六、Spring Boot 高频考点](#六spring-boot-高频考点)
- [七、Spring MVC 高频考点](#七spring-mvc-高频考点)
- [八、MySQL 高频考点](#八mysql-高频考点)
- [九、必须能独立写出的代码](#九必须能独立写出的代码)
- [十、17 题限时模拟卷](#十17-题限时模拟卷)
- [十一、模拟卷答案与评分点](#十一模拟卷答案与评分点)
- [十二、考前检查清单](#十二考前检查清单)
- [十三、仓库内延伸阅读](#十三仓库内延伸阅读)
- [十四、资料来源与可信度说明](#十四资料来源与可信度说明)

------

## 一、先看结论

### 1. 公司是什么

招聘材料中的“适趣集团”是品牌/集团称呼，公开官网及 App Store 展示的主要公司主体为 **北京智乐活科技有限公司**，核心产品是面向儿童中文阅读与识字的 **适趣 AI 中文**。

产品不是通用聊天机器人，核心思路是：测试儿童识字量，根据识字水平推荐包含少量生字的阅读材料，记录学习结果，再安排复习。公司自述的近期主要营收方式是购买流量获得短期会员用户，再通过私域运营转化为一年或两年会员，同时也做达人直播销售。

### 2. 这次笔试真正考什么

岗位介绍文档没有给出具体研发职责和技术架构。当前唯一明确的技术范围来自招聘通知：

1. Java 基础、集合、并发和代码能力。
2. Spring Boot 自动配置、Bean、AOP、事务。
3. Spring MVC 请求链路、参数绑定、校验、异常处理。
4. MySQL 索引、事务、锁、MVCC、SQL 编写与优化。

“难度偏高、需要独立落地编写代码”意味着不能只背定义。至少要能从空白编辑器写出：

- 一个带参数校验和异常处理的 REST 接口。
- 一个具有事务边界的业务方法。
- 一条包含连接、分组或窗口函数的 SQL。
- 一道中等难度 Java 集合/算法题，并说明复杂度与边界条件。

### 3. 岗位是否值得继续

技术考试本身可以参加，但是否接受岗位，要把下面几项和 HR 确认清楚：

- 文档中的“正常工作时间”覆盖周一至周五 9:00-22:00、周六 9:00-20:00；这究竟是沟通窗口，还是实际出勤要求？
- “每周至少 5 天、每天至少 8 小时”是否包含周六，课程期间怎样记录工时？
- 3～4k 是税前总额还是基本工资？绩效部分实际发放规则是什么？
- 加班的认定、审批、计算基数和支付方式是什么？
- 3 周带薪培训是否有考核淘汰，未通过时是否仍按约支付培训工资？
- 工作地点、是否远程、设备提供、实习协议主体和转正机会分别是什么？

这里最大的现实约束不是技术栈，而是 **至少 6 个月 + 每周至少 40 小时 + 较长沟通时段**。如果与课程、毕业设计或秋招冲突，应在投入后续流程前确认。

------

## 二、公司与岗位调查

### 1. 可交叉验证的公司信息

| 项目 | 调查结果 | 可信度 |
| --- | --- | --- |
| 对外品牌 | 适趣集团、适趣 AI 中文 | 官网、App Store 与招聘材料一致 |
| 主要公司主体 | 北京智乐活科技有限公司 | 官网页脚、App Store 开发者名称一致 |
| 成立时间 | 2013-08-22 | 企业信息平台公开信息，正式签约前仍应以国家企业信用信息公示系统为准 |
| 法定代表人/创始人 | 田敬 | 招聘平台、媒体报道和公司材料一致 |
| 主要产品 | 适趣 AI 中文，另有适趣高阶中文、适趣文常知识等产品 | App Store 可核验产品仍在架 |
| 公开备案 | 官网展示京 ICP 备 13049284 号 | 可在工信部备案系统进一步核验 |
| 所属领域 | 电子与信息 | 公开的中关村企业名录中能查到公司主体 |

### 2. 公司自述，不能直接当作审计事实

以下内容来自公司提供的候选人材料，应理解为招聘宣传口径：

- 公司已营收数亿、产品帮助上百万 3～8 岁儿童。
- 公司以数十倍速度发展。
- 适趣 AI 中文可让儿童提前 2～3 年自主阅读。
- 达人直播曾达到 285 万以上 GMV。
- 团队平均年龄 25 岁，氛围轻松、平等、自由。

这些说法可能真实，但目前没有在公开财报或审计材料中得到独立验证。面试时可以基于它们提问，不要用质疑式口吻直接下结论。

### 3. 产品与业务的技术推测

根据公开产品机制，可以合理推测研发系统会涉及下列问题，但这不是公司已确认的内部技术架构：

- 用户、会员、订单、权益等典型业务系统。
- 阅读内容、汉字、题目、学习记录等内容与行为数据。
- 识字量测评、内容推荐、学习进度和复习计划。
- 短期会员转长期会员的营销和转化链路。
- 儿童个人信息、内容安全、支付和数据合规。

因此，笔试里的 CRUD、事务、库存/权益幂等、SQL 统计和接口校验都具有较强的业务相关性。

### 4. 岗位条件原文摘要

| 项目 | 文档说明 |
| --- | --- |
| 流程 | 简历初筛 → 笔试与业务面试 → 带薪培训 → 签订实习协议 → 正式工作 |
| 正常工作时间 | 周一至周五 9:00-22:00，周六 9:00-20:00 |
| 弹性安排底线 | 每周工作不低于 5 天，每天不低于 8 小时 |
| 实习周期 | 不低于 6 个月 |
| 薪资构成 | 基本薪资 + 绩效薪资 + 加班费，基本与绩效约为 8:2 |
| 绩效 | 基本分 80 分，对应绩效薪资的 80%，再按评分增减 |
| 薪资范围 | 二面后明确，文档称正常范围约 3～4k |
| 培训 | 面试通过后进行 3 周公司与技术培训，文档称按工作时间计薪 |

### 5. 面试反问清单

建议优先问能影响决策的问题：

1. “研发实习生入职后的第一个真实任务是什么？会改生产项目还是完成培训项目？”
2. “目前后端 Java、Spring Boot、MySQL 的版本是什么？数据访问层使用 MyBatis、JPA 还是其他方案？”
3. “正常工作时间写到 22 点，能否举例说明实际一天的在线、会议和编码时间？”
4. “周六 9 点到 20 点是否每周固定出勤？若工作，如何计算和支付加班费？”
5. “3 周培训的通过标准、通过率、退出条件和工资结算规则是什么？”
6. “绩效工资占 20%，80 分只发绩效部分的 80%，那近几个月实习生绩效工资的实际平均发放比例是多少？”
7. “实习协议由哪个公司主体签署？薪资发放、个税记录和实习证明是否使用同一主体？”
8. “代码评审、测试、上线和事故复盘分别由谁负责？实习生是否有固定导师？”

### 6. 公开面经的额外提醒

牛客网能够检索到多篇同公司经历。下面按发布时间和岗位整理，均为候选人个人回忆，不是公司官方题库：

| 时间与岗位 | 候选人记录的流程/题目 | 对本次的参考价值 |
| --- | --- | --- |
| 2025-02，Java | 飞书开视频、共享屏幕答卷；脑筋急转弯、HashMap/List、3 个 MyBatis 问题、Vue、简单 SQL、数独 DFS | 与其他 Java 面经高度重复，可用于补充旧卷范围 |
| 2025-03，Java | 共 9 个模块：4 道逻辑题、集合与比较器、连接 SQL、MyBatis 三组知识、10 个 Vue 小题、6×6 数独；约 1 小时 15 分钟 | 信息最结构化，但时长和范围与本次通知不一致 |
| 2025-05，AI 产品教研 | 自动化初面 + 业务人工面；初面含约 14 个录制行为问题和 4 个综合素质题，全程露脸并共享屏幕，约 1～2 小时 | 能印证公司的自动化面试方式，不代表 Java 技术题 |
| 2026-02，大模型应用开发 | 作者称面试中没有技术问题，最后被沟通运营实习；未披露技术试题 | 单一个案，只用于提醒面试时确认岗位职责 |
| 2026-06，Java | 二分/逻辑题、6 道 List/Map 程序题、8 道 SQL、数学题、3 道 MyBatis、10 道前端题、不规则数独 | 时间最近，仍更像旧版综合卷；说明旧题型至少曾延续较长时间 |

跨多篇 Java 面经重复出现的旧卷内容是：

- Java `List`/`Map` 手写转换：有序去重、自定义排序、计数、Map 转换、字符串分类。
- 多道单表与多表 SQL，尤其是 `JOIN`。
- MyBatis 一级/二级缓存、`#{}`/`${}`、`resultType`/`resultMap`。
- 基础概率计算、逻辑推理、数独或类似回溯算法。
- 少量 Vue/前端题。

但精确检索没有找到与本次“17 题、限时 2 小时、Java、Spring Boot、MySQL、Spring MVC”完全一致的牛客面经。它与旧卷的 9 个模块、约 75 分钟以及 Vue/MyBatis/逻辑题结构明显不同，因此更像一份改版后的研发技术卷——这是根据公开信息作出的推测，不是公司确认。

正确策略是：

1. **以本次官方通知为最高优先级**，不能因为旧面经打乱四大模块复习。
2. 多份公开记录只能说明“历史上重复出现过”，不能证明本次仍是原题或相同结构。
3. 四大模块复习完成后，再用 30 分钟突袭：Java 集合操作 15 分钟、MyBatis 10 分钟、概率与逻辑 5 分钟。
4. 不购买所谓原题或答案；公开面经可能不完整、记忆有误，也可能已经换题。
5. 如果业务面继续推进，第一句话就确认：“该岗位入职后是 Java 研发还是其他岗位？研发任务、导师和代码仓库分别是什么？”

------

## 三、120 分钟答题策略

### 1. 时间分配

| 阶段 | 建议时间 | 动作 |
| --- | ---: | --- |
| 浏览全卷 | 3 分钟 | 看分值、代码题数量、是否允许切换题目，先标出最稳的题 |
| 单选与多选 | 17 分钟 | 平均每题 1～2 分钟；不确定项先标记，避免在一道题耗时 |
| 问答题 | 35 分钟 | 先写关键词骨架，再补机制、边界和实践 |
| 代码题 | 55 分钟 | 先保证正确和完整，再优化命名、复杂度与异常处理 |
| 总检查 | 10 分钟 | 查漏题、编译错误、SQL 分组错误、事务和边界条件 |

额外允许的 20 分钟只作为异常缓冲，不要一开始就按 140 分钟规划。若题目分值差异很大，以“分值/预计耗时”决定顺序。

### 2. 问答题万能结构

按下面五句展开，通常比堆概念得分稳定：

1. **定义**：它解决什么问题。
2. **机制**：内部怎样工作。
3. **关键对象/流程**：列出核心组件或执行顺序。
4. **边界与坑**：什么时候失效、有什么代价。
5. **实践**：项目里怎样选型、排查或验证。

例如回答 `@Transactional`：

> 它通过 Spring AOP 代理为方法建立事务边界。调用必须经过代理对象；代理在调用前开启或加入事务，成功时提交，满足回滚规则的异常抛出时回滚。默认通常只对 `RuntimeException` 和 `Error` 回滚。常见失效原因是同类自调用、非 Spring Bean、异常被吃掉、方法不可代理、使用了错误的事务管理器或跨线程执行。业务上应把事务放在 Service 的公开方法，缩小事务范围，并通过唯一索引或条件更新处理并发。

### 3. 代码题落笔顺序

1. 写清输入、输出和非法输入策略。
2. 选择数据结构，先在注释中写时间/空间复杂度。
3. 先写主流程，再补空集合、重复值、越界、并发等边界。
4. Spring 代码先划分 Controller、Service、Repository 职责。
5. SQL 先确认“一行代表什么”，再决定 `JOIN`、`WHERE`、`GROUP BY`、`HAVING`。
6. 最后手动走一遍最小用例和反例。

------

## 四、复习优先级

### S 级：必须能讲原理并写代码

- `HashMap`、`ConcurrentHashMap`、`ArrayList`。
- `synchronized`、`volatile`、CAS、线程池参数和拒绝策略。
- Spring AOP、事务传播、事务失效。
- `DispatcherServlet` 请求链路。
- 参数绑定、Bean Validation、全局异常处理。
- B+ 树、联合索引、覆盖索引、回表、`EXPLAIN`。
- ACID、隔离级别、MVCC、行锁、间隙锁、死锁。
- `JOIN`、聚合、子查询、窗口函数、分页和条件更新。

### A 级：选择和问答高频

- `equals`/`hashCode`、String、泛型、异常。
- Java 内存模型、线程安全集合、`CompletableFuture`。
- Bean 生命周期、循环依赖、自动配置、条件注解。
- `Filter`、`HandlerInterceptor`、AOP 的区别。
- JSON 序列化、HTTP 状态码、REST 设计。
- redo log、undo log、binlog 的职责。

### B 级：时间充足再看

- JVM 垃圾回收器细节。
- Spring 源码中的完整扩展点顺序。
- 分布式事务、MQ、Redis 等招聘通知未明确列出的内容。

------

## 五、Java 高频考点

### 1. `==`、`equals` 与 `hashCode`

- 基本类型的 `==` 比较值；引用类型的 `==` 比较是否指向同一对象。
- `Object.equals` 默认仍比较引用，String 等类重写后比较内容。
- 两个对象 `equals` 相等，则 `hashCode` 必须相等；反过来不成立。
- 作为 `HashMap` 键的对象，参与 `equals/hashCode` 的字段在入表后不要改变，否则可能再也查不到该键。

### 2. String 与包装类型

- `String` 不可变，便于常量池复用、缓存哈希值和线程安全共享。
- 循环拼接使用 `StringBuilder`；跨线程共享且确需同步时才考虑 `StringBuffer`。
- 字符串字面量可能复用常量池对象，`new String(...)` 会显式创建对象，内容比较始终用 `equals`。
- 自动拆箱遇到 `null` 会抛 `NullPointerException`。
- `Integer` 缓存常见范围是 -128～127，不能用 `==` 判断两个包装对象的数值相等。

### 3. ArrayList 与 LinkedList

| 对比项 | ArrayList | LinkedList |
| --- | --- | --- |
| 底层 | 动态数组 | 双向链表 |
| 随机访问 | `O(1)` | `O(n)` |
| 尾部追加 | 均摊 `O(1)` | `O(1)` |
| 已知节点位置后的插入删除 | 移动元素 `O(n)` | 改指针 `O(1)`，但查找位置仍可能 `O(n)` |
| 内存局部性 | 好 | 较差，节点额外保存指针 |

不能简单回答“插入多就用 LinkedList”。大多数业务场景 ArrayList 仍更常用，因为查找位置和缓存局部性也会影响性能。

### 4. HashMap

Java 8 的核心结构是 **数组 + 链表 + 红黑树**：

1. 根据 key 的哈希值定位桶。
2. 桶为空则直接插入。
3. 桶内通过哈希值和 `equals` 判断是否同键。
4. 冲突过多时链表可能树化。
5. 元素数超过 `容量 × 负载因子` 时扩容。

必背边界：

- 默认负载因子是 0.75。
- 链表长度达到 8 且数组容量至少为 64 时才树化；容量不足时优先扩容。
- 允许一个 `null` key 和多个 `null` value。
- 非线程安全，并发写可能发生覆盖、数据不一致等问题。
- 树化不能消除糟糕哈希，只是把极端桶内查找从链表退化改善为近似 `O(log n)`。

### 5. ConcurrentHashMap

- Java 8 不再使用早期版本的 Segment 分段锁结构作为主要实现。
- 读操作大多不加互斥锁；写操作结合 CAS 和桶级 `synchronized`。
- 不允许 `null` key/value，避免并发语义下无法区分“没有映射”和“映射值为 null”。
- 复合操作不要写成 `containsKey` 后再 `put`，应使用 `putIfAbsent`、`compute`、`merge` 等原子 API。

### 6. 泛型与 PECS

- Java 泛型主要通过类型擦除实现，运行时通常无法直接获得具体类型参数。
- `? extends T` 适合作为生产者读取 T，通常不能安全写入具体 T。
- `? super T` 适合作为消费者写入 T，读取时只能按 Object 处理。
- 口诀：Producer Extends，Consumer Super。

### 7. 异常

- checked exception 编译期要求捕获或声明；unchecked exception 是 `RuntimeException` 及其子类。
- 不要捕获 `Exception` 后只打印日志并继续返回成功。
- `finally` 中不要 `return`，它可能覆盖 `try/catch` 的返回值或异常。
- 实现 `AutoCloseable` 的资源用 try-with-resources，多个关闭异常会以 suppressed exception 保存。

### 8. Java 内存模型与并发

`volatile` 提供：

- 可见性：一个线程写入后，其他线程能看到新值。
- 有序性：通过内存屏障限制特定指令重排。
- 不提供复合操作原子性，`count++` 仍不是线程安全的。

`synchronized` 提供互斥、可见性和有序性。锁对象必须稳定，不能对可能变化的对象引用加锁。

CAS 是“比较旧值，仍相等才写新值”的原子操作。优点是避免阻塞，问题包括 ABA、自旋开销和只能原子更新单个位置；Java 可用版本号或 `AtomicStampedReference` 处理 ABA。

### 9. 线程池七个参数

`ThreadPoolExecutor` 的核心参数：

1. `corePoolSize`
2. `maximumPoolSize`
3. `keepAliveTime`
4. `unit`
5. `workQueue`
6. `threadFactory`
7. `handler`

典型执行顺序：核心线程未满 → 创建核心线程；核心已满 → 入队；队列满且线程未达最大值 → 创建非核心线程；仍无法接收 → 执行拒绝策略。

不要在生产代码中无脑使用无界队列。线程数、队列长度和拒绝策略必须结合任务耗时、到达速率、下游容量与可接受延迟设置。

### 10. JVM 最小知识框架

- 程序计数器：当前线程执行位置，线程私有。
- Java 虚拟机栈：栈帧、局部变量、操作数栈，线程私有。
- 堆：对象主要分配区域，线程共享，是 GC 重点区域。
- 方法区的 HotSpot 实现是元空间，主要保存类元数据。
- 直接内存不属于 JVM 堆，但同样可能耗尽。
- 判断对象是否存活主要看可达性分析，不是简单引用计数。

### 11. Java 集合转换突袭

历史公开面经特别提到手写 `List`/`Map` 操作，这几种必须不依赖 IDE 补全也能写出。

保持原顺序去重：

```java
public static <T> List<T> distinctInOrder(List<T> input) {
    return new ArrayList<>(new LinkedHashSet<>(input));
}
```

统计词频并取频率最高的三个词；频率相同时按字典序排序，确保结果稳定：

```java
public static List<String> topThree(List<String> words) {
    Map<String, Long> frequency = words.stream()
            .collect(Collectors.groupingBy(
                    Function.identity(),
                    Collectors.counting()));

    return frequency.entrySet().stream()
            .sorted((a, b) -> {
                int byCount = Long.compare(b.getValue(), a.getValue());
                return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
            })
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
}
```

List 转 Map 时显式处理重复 key：

```java
Map<Long, User> userMap = users.stream()
        .collect(Collectors.toMap(
                User::getId,
                Function.identity(),
                (oldValue, newValue) -> newValue,
                LinkedHashMap::new));
```

字符串分类时注意 Java 正则转义：

```java
if (value.matches("\\d+")) {
    numbers.add(value);
} else if (value.matches("[a-zA-Z]+")) {
    letters.add(value);
}
```

不要把正则写成 Java 字符串 `"\d+"`；Java 源码中反斜杠本身还要再转义一次。

------

## 六、Spring Boot 高频考点

### 1. `@SpringBootApplication`

它组合了三个核心注解：

- `@SpringBootConfiguration`：本质上属于配置类。
- `@EnableAutoConfiguration`：开启自动配置。
- `@ComponentScan`：扫描启动类所在包及其子包中的组件。

启动类放在过深或错误的包中，可能导致 Controller、Service 等没有被扫描。

### 2. 自动配置原理

可以按这条主线回答：

1. Spring Boot 启动时开启自动配置。
2. 根据约定位置加载自动配置候选类；现代版本主要通过 `AutoConfiguration.imports` 声明。
3. `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 等条件决定配置是否生效。
4. 自动配置通常在 classpath 有相关依赖、配置项满足条件且用户没有自定义 Bean 时提供默认 Bean。
5. 用户自定义 Bean 常可让默认配置“退让”，这就是约定优于配置但允许覆盖。

Starter 主要解决依赖组合和版本协调，AutoConfiguration 负责根据条件创建 Bean，两者不是一回事。

### 3. Bean 生命周期

简化顺序：

1. 实例化。
2. 属性填充/依赖注入。
3. Aware 接口回调。
4. `BeanPostProcessor#postProcessBeforeInitialization`。
5. `@PostConstruct`、`InitializingBean`、自定义 init-method。
6. `BeanPostProcessor#postProcessAfterInitialization`，AOP 代理常在这里产生。
7. 容器关闭时执行 `@PreDestroy`、`DisposableBean`、destroy-method。

### 4. 为什么推荐构造器注入

- 依赖显式且可以设为 `final`。
- 对象创建后就是完整状态。
- 单元测试时可以直接构造，不必启动容器或反射注入。
- 能更早暴露循环依赖和类职责过重问题。

### 5. AOP 心智模型

Spring AOP 的常见实现是运行期代理：

```text
调用方 -> 代理对象 -> 通知/拦截器链 -> 目标方法
```

JDK 动态代理面向接口；CGLIB 通过生成子类代理。Spring Boot 具体选择会受版本和配置影响，答题时不要绝对化为“有接口一定只用 JDK”。

### 6. `@Transactional`

高频点：

- 默认传播行为是 `REQUIRED`。
- `REQUIRES_NEW` 会挂起外层事务并开启新事务。
- 默认通常对 `RuntimeException` 和 `Error` 回滚；checked exception 需要配置 `rollbackFor`。
- 回滚的前提是异常离开事务代理边界；内部捕获后不再抛出，代理会认为方法成功。

常见失效原因：

1. 同一个类里 `this.method()` 自调用，没有经过代理。
2. 对象不是 Spring Bean，或自行 `new` 出来。
3. 异常被捕获吞掉。
4. 回滚异常类型不匹配。
5. 方法不可被当前代理方式增强。
6. 数据库引擎或操作本身不支持事务。
7. 多数据源时使用了错误的事务管理器。
8. 在新线程中执行数据库操作，线程绑定的事务上下文没有自动传播。

### 7. 配置管理

- 使用 `@ConfigurationProperties` 绑定一组结构化配置，优于散落大量 `@Value`。
- 用 profile 区分环境，但密钥不要提交到仓库。
- 配置优先级题不要死背所有层级，重点理解命令行、系统属性、环境变量、外部配置通常可以覆盖包内默认值；不同 Spring Boot 版本细节要以版本文档为准。
- Actuator 的健康检查和指标端点不应无保护地暴露到公网。

### 8. MyBatis 突袭知识

这部分不在当前通知列出的四个名称中，但历史公开面经出现过，可作为最后补充：

- `#{value}` 通过预编译参数占位传值，通常能避免把用户值直接拼进 SQL。
- `${value}` 是文本替换，适合经过白名单校验的表名、列名或排序方向等无法参数化的位置；直接接收用户输入会有 SQL 注入风险。
- `resultType` 适合列名与属性名能直接映射的简单结果。
- `resultMap` 用于列名/属性名不一致、嵌套对象、一对一或一对多等复杂映射。
- 一级缓存默认是 `SqlSession` 级别；同一会话中的更新操作会使相关缓存失效。
- 二级缓存是 Mapper namespace 级别，需要显式配置并注意跨会话脏数据风险；实际项目不能把缓存当成数据库一致性的替代品。
- Spring 集成中 `SqlSession` 生命周期通常由框架管理，不要自行长期持有会话对象。

------

## 七、Spring MVC 高频考点

### 1. 请求处理链路

```mermaid
flowchart LR
    A["HTTP 请求"] --> B["Filter 链"]
    B --> C["DispatcherServlet"]
    C --> D["HandlerMapping"]
    D --> E["HandlerExecutionChain"]
    E --> F["HandlerAdapter"]
    F --> G["参数解析与校验"]
    G --> H["Controller"]
    H --> I["返回值处理"]
    I --> J["HttpMessageConverter"]
    J --> K["HTTP 响应"]
```

异常可能交给 `HandlerExceptionResolver` 体系处理；视图模式下还会经过 `ViewResolver`，REST 接口则通常通过消息转换器把对象序列化为 JSON。

### 2. 常见参数绑定

| 注解 | 来源 | 例子 |
| --- | --- | --- |
| `@PathVariable` | URL 路径变量 | `/users/{id}` |
| `@RequestParam` | 查询串或表单字段 | `/users?page=1` |
| `@RequestBody` | HTTP 请求体 | JSON 转 DTO |
| `@RequestHeader` | 请求头 | `Authorization` |
| `@CookieValue` | Cookie | 会话或偏好值 |
| `@ModelAttribute` | 表单/查询参数绑定对象 | HTML 表单对象 |

`@RequestBody` 通常由 `HttpMessageConverter` 反序列化；它不是从 URL 查询参数中取值。

### 3. 参数校验

```java
public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名最多 50 个字符")
        String name,

        @NotNull(message = "年龄不能为空")
        @Min(value = 1, message = "年龄必须大于 0")
        Integer age
) {
}

@PostMapping("/users")
public ResponseEntity<UserResponse> create(
        @Valid @RequestBody CreateUserRequest request) {
    UserResponse result = userService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
}
```

注意区分：

- `@Valid` 触发对象级联校验常用。
- `@Validated` 是 Spring 扩展，支持分组校验，也常用于方法级参数校验。
- DTO 的包装类型能区分“未传值”和基本类型默认值，例如 `Integer` 与 `int`。

### 4. 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_ARGUMENT", message));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getCode(), ex.getMessage()));
    }
}
```

生产代码不要把堆栈、SQL、服务器路径等内部细节直接返回给客户端。

### 5. Filter、Interceptor 与 AOP

| 对比 | Filter | HandlerInterceptor | Spring AOP |
| --- | --- | --- | --- |
| 所属层次 | Servlet 容器 | Spring MVC | Spring Bean 方法 |
| 典型范围 | 几乎所有进入容器的请求 | 被 MVC 映射的请求 | 符合切点的 Bean 方法 |
| 典型用途 | CORS、字符集、请求包装、通用鉴权入口 | 登录检查、接口耗时、Controller 上下文 | 事务、审计、方法级日志 |
| 能否直接获取 HandlerMethod | 不方便 | 可以 | 关注方法本身 |

### 6. 常见 HTTP 状态码

- `200 OK`：成功查询或普通成功响应。
- `201 Created`：资源创建成功。
- `204 No Content`：成功但无响应体。
- `400 Bad Request`：请求格式或参数校验错误。
- `401 Unauthorized`：未认证。
- `403 Forbidden`：已识别身份但无权限。
- `404 Not Found`：资源不存在。
- `409 Conflict`：业务状态冲突，如重复创建或库存竞争失败。
- `500 Internal Server Error`：未预期服务端错误。

------

## 八、MySQL 高频考点

### 1. 为什么常用 B+ 树索引

- 扇出大、树高低，能减少磁盘 I/O。
- 非叶子节点主要保存键和指针，单页能容纳更多索引项。
- 叶子节点按顺序连接，适合范围扫描和排序。
- 查询路径稳定，点查和范围查都比较均衡。

### 2. 聚簇索引、二级索引、回表与覆盖索引

- InnoDB 主键索引的叶子节点保存整行数据，称为聚簇索引。
- 二级索引叶子节点通常保存索引列和主键值。
- 通过二级索引找到主键后，再查主键索引取得其他列，叫回表。
- 查询需要的列都能从某个索引取得，叫覆盖索引，可减少回表。
- 主键应尽量短且稳定，因为二级索引叶子节点也要保存主键。

### 3. 联合索引与最左前缀

索引 `(a, b, c)` 按 a、b、c 的顺序排序，常见可利用形式：

- `a = ?`
- `a = ? AND b = ?`
- `a = ? AND b = ? AND c = ?`
- `a = ? AND b > ?`，但范围条件之后的列能否用于定位要结合版本和执行计划判断。

不能把“WHERE 书写顺序”误认为“索引使用顺序”，优化器会重排等值条件。真正关键是是否提供了联合索引的有效最左前缀。

常见索引失效或收益下降场景：

- 对索引列使用函数或隐式类型转换。
- 以 `%` 开头的模糊查询。
- 选择性很差，优化器认为全表扫描更便宜。
- 联合索引跳过最左列。
- 返回数据比例过高，回表成本太大。

### 4. `EXPLAIN` 重点字段

- `type`：访问方式，`ALL` 通常意味着全表扫描；不能只靠排序口诀判断好坏。
- `possible_keys`：可能使用的索引。
- `key`：实际选择的索引。
- `key_len`：实际使用的索引长度，可辅助判断联合索引用到几列。
- `rows`：优化器估算扫描行数。
- `filtered`：按条件过滤后预计保留比例。
- `Extra`：关注 `Using index`、`Using filesort`、`Using temporary` 等。

分析慢 SQL 的顺序：确认慢 SQL 和参数 → 看执行计划 → 检查扫描行数、索引和排序/临时表 → 查看表结构与数据分布 → 调整索引或 SQL → 用真实数据复测。

### 5. ACID 与隔离级别

- 原子性：事务中的操作要么都成功，要么都回滚，主要依赖 undo log 等机制。
- 一致性：事务前后业务约束成立，是原子性、隔离性、持久性与业务逻辑共同实现的结果。
- 隔离性：并发事务彼此影响受到控制，依赖锁和 MVCC。
- 持久性：已提交结果在故障后可恢复，主要依赖 redo log 等机制。

隔离级别由弱到强：读未提交、读已提交、可重复读、串行化。InnoDB 常见默认级别是可重复读，但面试中最好说“以实际数据库配置为准”。

### 6. MVCC

MVCC 的目标是让读写尽量不互相阻塞。InnoDB 通过隐藏事务信息、undo log 版本链和 Read View 判断某个版本对当前事务是否可见。

- 普通 `SELECT` 常是快照读。
- `SELECT ... FOR UPDATE`、`UPDATE`、`DELETE` 属于当前读，需要读取最新版本并加锁。
- 读已提交通常每次一致性读生成新的 Read View。
- 可重复读通常在事务内复用 Read View，因此多次快照读结果稳定。

### 7. 锁与死锁

- 记录锁：锁定索引记录。
- 间隙锁：锁定索引记录之间的范围。
- Next-Key Lock：记录锁与间隙锁的组合。
- 意向锁：表级标记，帮助快速判断表中是否存在行锁。

降低死锁概率：

1. 多个事务以一致顺序访问资源。
2. 事务尽量短，不在事务中执行远程调用或长时间计算。
3. 为查询条件建立合适索引，减少锁定范围。
4. 对死锁异常做有限次数重试，并保证操作幂等。

### 8. 三类日志

| 日志 | 层次 | 主要作用 |
| --- | --- | --- |
| undo log | InnoDB | 回滚、MVCC 历史版本 |
| redo log | InnoDB | 崩溃恢复、持久性 |
| binlog | Server 层 | 逻辑变更记录、复制、时间点恢复 |

### 9. SQL 书写高频陷阱

- `WHERE` 在分组前过滤行，`HAVING` 在分组后过滤组。
- `COUNT(*)` 统计结果行；`COUNT(column)` 不统计该列为 `NULL` 的行。
- `LEFT JOIN` 右表条件若写在 `WHERE` 中，可能把未匹配的 NULL 行过滤掉，效果接近 INNER JOIN。
- `NOT IN` 子查询只要出现 NULL 就可能产生不符合直觉的三值逻辑，常优先考虑 `NOT EXISTS`。
- 查询非聚合列时要满足 `ONLY_FULL_GROUP_BY` 规则，不要依赖宽松模式返回不确定值。
- 深分页 `LIMIT offset, size` 会扫描并丢弃大量记录，可使用基于稳定排序键的游标翻页。

------

## 九、必须能独立写出的代码

### 1. 线程安全计数器

```java
public final class Counter {
    private final AtomicLong value = new AtomicLong();

    public long increment() {
        return value.incrementAndGet();
    }

    public long get() {
        return value.get();
    }
}
```

若题目要求多个变量保持复合不变式，只换成多个 Atomic 类通常不够，应考虑同一把锁或把状态封装为不可变对象后整体 CAS。

### 2. LRU 缓存

```java
public class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LruCache(int capacity) {
        super(capacity, 0.75F, true);
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

`accessOrder=true` 表示按访问顺序维护。该实现本身不保证线程安全；并发环境需要外部同步或选用成熟缓存组件。

### 3. 带校验的 REST 接口

```java
@RestController
@RequestMapping("/api/reading-records")
public class ReadingRecordController {
    private final ReadingRecordService service;

    public ReadingRecordController(ReadingRecordService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReadingRecordResponse> create(
            @Valid @RequestBody CreateReadingRecordRequest request) {
        ReadingRecordResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ReadingRecordResponse detail(@PathVariable Long id) {
        return service.findById(id);
    }
}
```

### 4. 防止超卖的条件更新

SQL：

```sql
UPDATE membership_stock
SET available = available - 1,
    version = version + 1
WHERE product_id = #{productId}
  AND available > 0;
```

Service：

```java
@Service
public class OrderService {
    private final StockMapper stockMapper;
    private final OrderMapper orderMapper;

    public OrderService(StockMapper stockMapper, OrderMapper orderMapper) {
        this.stockMapper = stockMapper;
        this.orderMapper = orderMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Long userId, Long productId, String requestId) {
        Order existing = orderMapper.findByRequestId(requestId);
        if (existing != null) {
            return existing.getId();
        }

        int affected = stockMapper.decreaseIfAvailable(productId);
        if (affected != 1) {
            throw new BusinessException("OUT_OF_STOCK", "库存不足");
        }

        Order order = Order.pending(userId, productId, requestId);
        orderMapper.insert(order);
        return order.getId();
    }
}
```

数据库还应为 `request_id` 建唯一索引。仅在代码里“先查再插”不能彻底防住并发重复请求，插入时还需处理唯一键冲突并读取已有结果。

### 5. 每个用户最近一次阅读记录

```sql
SELECT user_id, article_id, read_at
FROM (
    SELECT rr.*,
           ROW_NUMBER() OVER (
               PARTITION BY rr.user_id
               ORDER BY rr.read_at DESC, rr.id DESC
           ) AS rn
    FROM reading_record rr
) ranked
WHERE ranked.rn = 1;
```

用 `id DESC` 作为同一时间下的稳定决胜条件，避免结果不确定。

### 6. 游标分页

```sql
SELECT id, user_id, article_id, read_at
FROM reading_record
WHERE (read_at < :lastReadAt)
   OR (read_at = :lastReadAt AND id < :lastId)
ORDER BY read_at DESC, id DESC
LIMIT :pageSize;
```

配套索引可考虑 `(read_at, id)`；实际是否有效必须结合数据分布和 `EXPLAIN` 验证。

------

## 十、17 题限时模拟卷

> 建议严格计时 120 分钟，先独立作答，再看下一节答案。题型比例是复习用模拟，并不代表真实试卷分布。

### 单选题

#### 1. 关于 Java 8 `HashMap` 树化，正确的是

A. 链表长度达到 8 时一定立即树化  
B. 只有数组容量至少 64 且链表达到阈值时才会树化，否则可能先扩容  
C. 树化后所有操作严格为 `O(1)`  
D. `HashMap` 不允许 `null` key

#### 2. 下列哪种情况最可能使 `@Transactional` 不生效

A. 一个 Spring Service 的公开方法由另一个 Bean 调用  
B. 事务方法抛出未捕获的 `RuntimeException`  
C. 同一个类中的非事务方法通过 `this` 调用事务方法  
D. 事务方法执行一条普通 InnoDB `UPDATE`

#### 3. Spring MVC 中负责根据请求找到 Handler 的组件是

A. `HandlerMapping`  
B. `ViewResolver`  
C. `HttpMessageConverter`  
D. `BeanFactoryPostProcessor`

#### 4. 有联合索引 `(user_id, status, created_at)`，最不能直接利用其最左前缀定位的是

A. `WHERE user_id = 1`  
B. `WHERE user_id = 1 AND status = 0`  
C. `WHERE status = 0 AND created_at > '2026-01-01'`  
D. `WHERE user_id = 1 AND status = 0 ORDER BY created_at`

#### 5. `COUNT(column)` 与 `COUNT(*)` 的关键区别是

A. 前者不统计 column 为 NULL 的行  
B. 后者永远比前者慢  
C. 前者不可以使用索引  
D. 后者只统计主键不为 NULL 的行

### 多选题

#### 6. 关于 `volatile`，正确的是

A. 能保证被修饰变量的可见性  
B. 能保证 `count++` 原子性  
C. 能限制特定指令重排  
D. 适合“一个线程写停止标记，其他线程读”的场景

#### 7. 下列情况可能导致 Spring 事务没有按预期回滚的是

A. 异常在事务方法内部被捕获且没有重新抛出  
B. 抛出 checked exception，但没有配置对应回滚规则  
C. 数据库操作在新线程中执行  
D. 公开事务方法由另一个 Spring Bean 调用

#### 8. 关于 Spring MVC，正确的是

A. `@RequestBody` 常通过 `HttpMessageConverter` 反序列化  
B. `@RestControllerAdvice` 可集中处理 Controller 异常  
C. `Filter` 一定能直接拿到最终的 `HandlerMethod`  
D. `@Valid` 可触发请求 DTO 校验

#### 9. 下列做法可能改善慢 SQL 的是

A. 为高频过滤和排序设计合适联合索引  
B. 只看 SQL 文本，不看真实参数和数据分布  
C. 用覆盖索引减少回表  
D. 用稳定游标分页替代超大 offset 深分页

### 问答题

#### 10. 说明 `HashMap` 的 put 流程、扩容时机以及为什么不线程安全。

#### 11. 说明 Spring Boot 自动配置如何做到“有依赖才配置、用户配置后退让”。

#### 12. 从 HTTP 请求进入应用开始，说明 Spring MVC 返回 JSON 的完整主链路。

#### 13. 解释 InnoDB MVCC，并比较读已提交与可重复读下 Read View 的典型差异。

#### 14. 收到一条线上慢 SQL 后，你会怎样定位并优化？至少写出 6 个步骤。

### 代码题

#### 15. Java：实现固定容量 LRU 缓存

要求支持 `get`、`put`，两者平均时间复杂度为 `O(1)`；说明是否线程安全。

#### 16. MySQL：查询每个用户最近 3 次阅读记录

表结构：

```sql
CREATE TABLE reading_record (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL
);
```

返回 `user_id、article_id、read_at`，同一用户按 `read_at DESC, id DESC` 排序。

#### 17. Spring Boot：编写创建会员订单的核心业务方法

要求：

1. 参数包含 `userId、productId、requestId`。
2. `requestId` 保证幂等。
3. 库存大于 0 才能扣减，不能超卖。
4. 扣库存和创建订单在同一事务中。
5. 说明并发重复请求、库存不足和异常回滚如何处理。

------

## 十一、模拟卷答案与评分点

### 1～5 单选

1. B
2. C
3. A
4. C
5. A

### 6～9 多选

6. A、C、D
7. A、B、C
8. A、B、D
9. A、C、D

### 10. HashMap

评分点：

- 哈希扰动并定位数组桶。
- 空桶插入；冲突时比较 hash 和 `equals`，同键覆盖 value。
- 桶内结构可能是链表或红黑树。
- 插入后元素数超过阈值触发扩容。
- 树化需要同时考虑链表阈值和最小数组容量。
- 并发 put 没有互斥，可能覆盖更新、看见不一致中间状态；应使用 `ConcurrentHashMap`。

### 11. 自动配置

评分点：

- `@EnableAutoConfiguration` 导入自动配置候选类。
- 自动配置类由约定元数据声明，现代版本常见 `AutoConfiguration.imports`。
- `@ConditionalOnClass` 判断依赖是否存在。
- `@ConditionalOnProperty` 判断配置项。
- `@ConditionalOnMissingBean` 在用户没有自定义 Bean 时才创建默认 Bean。
- Starter 管依赖组合，自动配置负责建 Bean。

### 12. Spring MVC 链路

参考顺序：

```text
Filter
-> DispatcherServlet
-> HandlerMapping
-> HandlerExecutionChain（包含 Interceptor）
-> HandlerAdapter
-> 参数解析、类型转换、@RequestBody 反序列化、校验
-> Controller
-> 返回值处理器
-> HttpMessageConverter（如 Jackson）序列化 JSON
-> HTTP 响应
```

异常由异常解析器体系处理，`@RestControllerAdvice`/`@ExceptionHandler` 是常用入口。

### 13. MVCC

评分点：

- 版本链由记录中的事务信息和 undo log 等共同支持。
- Read View 根据活跃事务范围判断版本可见性。
- 快照读通常不加行级互斥锁，提升读写并发。
- 读已提交通常每次一致性读创建新 Read View。
- 可重复读通常在事务内复用 Read View。
- 当前读不走旧快照语义，而是读最新记录并配合锁。

### 14. 慢 SQL 排查

参考步骤：

1. 从监控或慢查询日志确认 SQL、耗时、频率和影响面。
2. 获取真实绑定参数，判断是否只有特定参数慢。
3. 查看表结构、索引、数据量、数据分布和统计信息。
4. 使用 `EXPLAIN`/`EXPLAIN ANALYZE` 检查实际访问路径与估算偏差。
5. 检查扫描行数、回表、连接顺序、临时表、排序和隐式转换。
6. 判断是否被锁等待、连接池或下游资源伪装成“SQL 慢”。
7. 调整 SQL 或索引，必要时拆分查询或改变分页方式。
8. 在接近生产的数据量上复测，并观察写放大和索引空间成本。
9. 灰度上线并持续观察 P95/P99，而不是只看一次本地执行时间。

### 15. LRU

可以使用“HashMap + 双向链表”手写，也可像本文第九节使用 `LinkedHashMap(accessOrder=true)`。必须说明：

- HashMap 负责 `O(1)` 定位。
- 双向链表维护最近使用顺序。
- 访问后移动到头部，超容量淘汰尾部。
- 默认实现不线程安全。

### 16. 每个用户最近 3 条

```sql
SELECT user_id, article_id, read_at
FROM (
    SELECT rr.*,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY read_at DESC, id DESC
           ) AS rn
    FROM reading_record rr
) ranked
WHERE rn <= 3
ORDER BY user_id, read_at DESC, id DESC;
```

### 17. 会员订单

本文第九节“防止超卖的条件更新”就是参考骨架。完整得分点：

- Service 公开方法上设置事务边界。
- 数据库为 `request_id` 建唯一索引，不能只依赖先查后插。
- 使用 `UPDATE ... WHERE available > 0`，检查影响行数是否为 1。
- 库存不足抛出能触发回滚的业务异常。
- 插入订单异常时扣库存回滚。
- 并发唯一键冲突时读取并返回已有订单，或按接口契约返回重复请求。
- 不把支付、短信等远程调用长时间包在本地数据库事务内。

------

## 十二、考前检查清单

### 考前一天

- [ ] 不看新知识，只补 S 级薄弱项。
- [ ] 从空白文件手写一次 LRU、事务 Service、窗口函数 SQL。
- [ ] 用自己的话讲一遍 Spring MVC 请求链和事务失效原因。
- [ ] 准备身份证明、纸笔、充电器和备用网络。
- [ ] 关闭会弹窗或抢占摄像头、麦克风的软件。

### 开考前 30 分钟

- [ ] 测试网络、摄像头、麦克风、屏幕共享。
- [ ] 确认浏览器权限、电源和系统时间。
- [ ] 清空桌面无关程序，关闭 AI 工具和资料页面，遵守考试规则。
- [ ] 确认 Java/SQL 编辑器是否由考试平台提供，不临时安装环境。
- [ ] 提前进入设备检测，不占用正式答题时间。

### 交卷前

- [ ] 17 道题没有漏答。
- [ ] 多选题确认“少选、错选”的计分规则。
- [ ] Java 代码检查导包、空值、循环边界和返回值。
- [ ] SQL 检查连接条件，防止笛卡尔积。
- [ ] 聚合查询检查 `GROUP BY` 与 `HAVING`。
- [ ] 事务代码确认异常会抛出代理边界。
- [ ] 代码题写明复杂度、并发安全性和必要假设。

------

## 十三、仓库内延伸阅读

- [[技术栈/Java与框架/MybatisPlus/Spring源码分析]]：IoC、Bean 生命周期、AOP、事务、Spring MVC、Spring Boot 自动配置主线。
- [[技术栈/Java与框架/MybatisPlus/Spring/SpringBoot/数据库事务]]：事务传播行为和多数据源事务管理器。
- [[技术栈/Java与框架/MybatisPlus/Spring/SpringBoot/获取参数的注解]]：MVC 参数绑定。
- [[技术栈/Java与框架/多线程/线程池七大核心参数]]：线程池参数。
- [[技术栈/Java与框架/多线程/API/CompletableFuture]]：异步任务编排。
- [[技术栈/Java与框架/Java/源码的分析/总结源码😘😘🎉]]：HashMap 源码梳理。
- [[项目与成长/实习方法论/数据库/MySQL/MySQL索引/索引和索引下推]]：索引与 `EXPLAIN`。
- [[项目与成长/实习方法论/数据库/MySQL/SQL知识]]：SQL 基础。
- [[项目与成长/实习方法论/数据库/MySQL/多表查询]]：连接查询。
- [[项目与成长/实习方法论/数据库/MySQL/窗口函数]]：Top N 等窗口函数。
- [[项目与成长/面经/发现的薄弱点]]：已有面试薄弱项答案。

------

## 十四、资料来源与可信度说明

### 公司和产品

1. [适趣集团官网](https://www.zhilehuo.com/)：用于核对品牌、官网主体和 ICP 展示信息。
2. [适趣 AI 中文 App Store 页面](https://apps.apple.com/cn/app/id1489800613)：用于核对产品仍在架、开发者主体和公开产品功能。
3. [国家外汇管理局公开的中关村企业名录 PDF](https://www.safe.gov.cn/beijing/file/file/20210416/4f62dd958447492aa5ee2ee0a9a68973.pdf)：可查到北京智乐活科技有限公司及“电子与信息”领域信息。
4. [人民周刊网对适趣和田敬的报道](https://www.peopleweekly.cn/html/2021/dushu_0928/90645.html)：用于交叉核对创始人和早期产品背景。
5. [候选人公司介绍（飞书）](https://rzn9p0sxxl.feishu.cn/docx/IBZDdfFv3oiZVUxaHMxcAXsDnve)：公司自述材料，涉及营收、用户数、产品效果、GMV、团队文化等内容时均按“公司自述”处理。
6. [研发实习岗位介绍（飞书）](https://pvislwzdfw7.feishu.cn/docx/HdYZdbrzdow0fsx1g9AcWEfAnne)：岗位流程、工时、实习周期、薪资与培训信息来源。
7. [智乐活/适趣 AI Java 凉经（牛客网，2025-02）](https://www.nowcoder.com/feed/main/detail/315007cf5d834bbf9b7259ab1eef670c)：Java 候选人的旧版自动化笔试回忆。
8. [智乐活一面（牛客网，2025-03）](https://www.nowcoder.com/feed/main/detail/f9c2520ccf3940dea7209535a131e373)：较完整地列出旧卷 9 个模块和约 75 分钟用时。
9. [智乐活 AI 产品教研实习生凉经（牛客网，2025-05）](https://www.nowcoder.com/discuss/755510705440174080)：用于交叉核对自动化面试、露脸和屏幕共享流程；岗位不是 Java 研发。
10. [适趣 AI 线上笔试记录（牛客网，2026-06）](https://www.nowcoder.com/discuss/892573010295570432)：较新的 Java 候选人回忆，题型仍与旧版综合卷相近。
11. [适趣 AI 大模型应用开发实习一面凉经（牛客网，2026-02）](https://www.nowcoder.com/feed/main/detail/5930f7b4eaba4a1b97cec74c2bdde60f)：单个候选人关于面试内容和岗位沟通的负面经历，只能作为确认岗位职责的提醒。
12. [适趣 AI 中文线上一轮笔试记录（编程导航，2025-09）](https://www.codefather.cn/post/1971256242978439169)：站外候选人回忆，可与牛客 Java 面经交叉核对题型。

### 技术内容

本文是面向笔试的复习提纲，不替代具体版本的官方文档。Spring Boot、Spring Framework 和 MySQL 在不同版本中存在实现差异；遇到题目明确版本时，应按题目版本作答。涉及公司内部技术架构的部分均标为推测，没有将公开产品功能直接当成公司真实后端实现。
