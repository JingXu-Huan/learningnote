# 🎉Java `Streams` 的常用Api及其介绍😘

## Why Streams ?😕

传统集合操作（`for` 循环 + `if` + `add`）写起来又臭又长，而 `Stream` 提供了**声明式**的数据处理方式：

```java
// 传统写法：找出所有偶数并求平方和
List<Integer> result = new ArrayList<>();
for (int n : numbers) {
    if (n % 2 == 0) {
        result.add(n * n);
    }
}
int sum = 0;
for (int n : result) {
    sum += n;
}

// Stream 写法
int sum = numbers.stream()
    .filter(n -> n % 2 == 0)
    .map(n -> n * n)
    .reduce(0, Integer::sum);
```

**核心优势**：链式调用、可读性强、天然支持并行（`.parallelStream()`）。

------

## 一、Stream 的创建方式

```java
// 1. 从集合创建
List<String> list = Arrays.asList("a", "b", "c");
Stream<String> s1 = list.stream();

// 2. 从数组创建
Stream<String> s2 = Arrays.stream(new String[]{"a", "b", "c"});

// 3. Stream.of()
Stream<Integer> s3 = Stream.of(1, 2, 3, 4, 5);

// 4. 无限流（需配合 limit 使用）
Stream<Integer> s4 = Stream.iterate(0, n -> n + 2); // 0, 2, 4, 6...
Stream<Double> s5 = Stream.generate(Math::random);   // 随机数流
```

------

## 二、中间操作（Intermediate Operations）

中间操作**不会立即执行**，只有遇到终端操作时才会触发整个流水线的计算（延迟求值）。

### 1. `filter` —— 过滤

```java
List<String> names = Arrays.asList("Tom", "Jerry", "Alice", "Bob");

names.stream()
    .filter(name -> name.length() > 3)  // 保留长度 > 3 的
    .forEach(System.out::println);      // Jerry, Alice
```

### 2. `map` —— 映射转换

```java
List<String> words = Arrays.asList("hello", "world");

words.stream()
    .map(String::toUpperCase)    // 转大写
    .map(String::length)         // 再取长度
    .forEach(System.out::println); // 5, 5
```

### 3. `flatMap` —— 扁平化映射

将每个元素映射为一个流，再合并为一个流：

```java
List<List<Integer>> nested = Arrays.asList(
    Arrays.asList(1, 2),
    Arrays.asList(3, 4, 5)
);

nested.stream()
    .flatMap(List::stream)  // 展平为一维
    .forEach(System.out::println); // 1, 2, 3, 4, 5
```

### 4. `sorted` —— 排序

```java
List<String> list = Arrays.asList("banana", "apple", "cherry");

list.stream()
    .sorted()                            // 自然排序
    .sorted(Comparator.reverseOrder())   // 逆序
    .forEach(System.out::println);
```

### 5. `distinct` —— 去重

```java
Stream.of(1, 2, 2, 3, 3, 4)
    .distinct()
    .forEach(System.out::println); // 1, 2, 3, 4
```

### 6. `limit` / `skip` —— 截取

```java
Stream.of(1, 2, 3, 4, 5)
    .skip(2)     // 跳过前 2 个
    .limit(2)    // 取 2 个
    .forEach(System.out::println); // 3, 4
```

### 7. `peek` —— 调试用（不改变流）

```java
Stream.of(1, 2, 3)
    .peek(n -> System.out.println("处理: " + n))  // 打印调试信息
    .map(n -> n * 2)
    .forEach(System.out::println);
```

------

## 三、终端操作（Terminal Operations）

终端操作会**触发整个流水线的执行**，并产生结果。

### 1. `forEach` —— 遍历

```java
list.stream().forEach(System.out::println);
```

### 2. `collect` —— 收集结果（最常用）

```java
// 收集为 List
List<String> result = list.stream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toList());

// 收集为 Set
Set<String> set = list.stream()
    .collect(Collectors.toSet());

// 收集为 Map
Map<String, Integer> map = list.stream()
    .collect(Collectors.toMap(
        s -> s,             // key: 字符串本身
        String::length      // value: 字符串长度
    ));

// 分组
Map<Integer, List<String>> grouped = list.stream()
    .collect(Collectors.groupingBy(String::length));

// 分区（true/false 两组）
Map<Boolean, List<Integer>> partitioned = numbers.stream()
    .collect(Collectors.partitioningBy(n -> n > 10));

// 拼接为字符串
String joined = list.stream()
    .collect(Collectors.joining(", ", "[", "]"));
// 结果: "[Tom, Jerry, Alice]"
```

### 3. `reduce` —— 归约

```java
// 求和
int sum = Stream.of(1, 2, 3, 4, 5)
    .reduce(0, Integer::sum);

// 求最大值
Optional<Integer> max = Stream.of(1, 2, 3, 4, 5)
    .reduce(Integer::max);
```

### 4. `count` / `min` / `max` —— 统计

```java
long count = list.stream().count();

Optional<String> min = list.stream()
    .min(Comparator.comparingInt(String::length));

Optional<String> max = list.stream()
    .max(Comparator.naturalOrder());
```

### 5. `anyMatch` / `allMatch` / `noneMatch` —— 匹配判断

```java
boolean anyEven = Stream.of(1, 2, 3, 4)
    .anyMatch(n -> n % 2 == 0);  // true

boolean allPositive = Stream.of(1, 2, 3)
    .allMatch(n -> n > 0);       // true

boolean noneNegative = Stream.of(1, 2, 3)
    .noneMatch(n -> n < 0);      // true
```

### 6. `findFirst` / `findAny` —— 查找

```java
Optional<String> first = list.stream()
    .filter(s -> s.startsWith("A"))
    .findFirst();

Optional<String> any = list.parallelStream()
    .filter(s -> s.startsWith("A"))
    .findAny();  // 并行流中更高效
```

### 7. `toArray` —— 转数组

```java
String[] arr = list.stream().toArray(String[]::new);
```

------

## 四、常用组合技巧

### 去重 + 排序 + 取前N

```java
List<Integer> topN = numbers.stream()
    .distinct()
    .sorted(Comparator.reverseOrder())
    .limit(5)
    .collect(Collectors.toList());
```

### 分组 + 统计

```java
// 按部门分组，统计每组人数和平均工资
Map<String, LongSummaryStatistics> stats = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.summarizingDouble(Employee::getSalary)
    ));
```

### 多层分组

```java
// 先按部门分组，再按职级分组
Map<String, Map<String, List<Employee>>> nested = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.groupingBy(Employee::getLevel)
    ));
```

------

## 五、并行流（Parallel Stream）

```java
// 创建并行流
list.parallelStream()
    .filter(n -> n > 10)
    .map(n -> n * n)
    .reduce(0, Integer::sum);
```

**什么时候用并行流？**

| 场景 | 是否适合 | 原因 |
|------|---------|------|
| 数据量大（>10万） | ✅ | 多核并行加速 |
| CPU 密集型计算 | ✅ | 充分利用多核 |
| IO 密集型（查库、调接口） | ❌ | 线程等待浪费时间 |
| 数据量小（<1000） | ❌ | 并行开销 > 收益 |
| 有状态操作（limit、sorted） | ⚠️ | 并行下性能可能更差 |

------

## 六、注意事项

1. **Stream 是一次性的**：一旦执行终端操作，流就被消费了，不能再用
2. **不要在流中修改外部状态**：`forEach` 中修改外部变量不是线程安全的
3. **`collect` vs `reduce`**：收集可变容器（List/Set/Map）用 `collect`，聚合为单一值用 `reduce`
4. **Optional 要用好**：`findFirst`、`max`、`min` 返回 `Optional`，不要直接 `.get()`，用 `orElse` 或 `orElseThrow`

---

## 七、Stream API 最佳实践

### 1. 先判断是否适合使用 Stream

Stream 更适合表达“筛选、转换、聚合”这类数据处理流程。如果循环中包含复杂分支、异常处理、多个变量累加，或者需要频繁 `break` / `continue`，普通 `for` 循环通常更清晰，不要为了使用 Stream 而使用 Stream。

```java
// 适合 Stream：表达清晰的数据转换
List<String> names = users.stream()
    .filter(User::isEnabled)
    .map(User::getName)
    .toList(); // Java 16+

// 复杂流程使用普通循环更容易维护
for (User user : users) {
    if (!user.isEnabled()) {
        continue;
    }
    // 需要记录多种中间状态、处理异常或提前退出时，循环更直观
}
```

### 2. 尽早过滤，减少后续处理量

把成本低、选择性高的 `filter` 放在前面，避免无效元素继续执行 `map`、排序等操作。`sorted`、`distinct` 等可能需要缓存数据的操作，通常应尽量靠后。

```java
List<String> result = orders.stream()
    .filter(Order::isPaid)
    .filter(order -> order.getAmount().compareTo(BigDecimal.ZERO) > 0)
    .map(Order::getOrderNo)
    .toList();
```

### 3. 不要在 Stream 中修改外部状态

Lambda 中修改外部集合、计数器或对象属性会降低可读性，并且在并行流中容易产生线程安全问题。优先使用 `map`、`filter`、`collect`、`count` 等无副作用操作。

```java
// 不推荐：依赖外部变量
List<String> result = new ArrayList<>();
users.stream().forEach(user -> result.add(user.getName()));

// 推荐：让 Stream 自己产生结果
List<String> names = users.stream()
    .map(User::getName)
    .toList();
```

### 4. 使用 `toMap` 时明确处理重复 key

`Collectors.toMap` 默认不允许 key 重复，重复时会抛出 `IllegalStateException`。只要业务上不能保证 key 唯一，就必须提供合并函数。

```java
Map<String, User> userMap = users.stream()
    .collect(Collectors.toMap(
        User::getUsername,
        Function.identity(),
        (oldUser, newUser) -> newUser // 重复时保留后者
    ));
```

### 5. 正确处理 null，避免把空值带入链路

集合本身可能为 `null` 时，应在进入 Stream 前统一处理；集合元素可能为 `null` 时，再过滤元素。不要在每个 Lambda 中重复判断。

```java
List<String> names = Optional.ofNullable(users)
    .orElseGet(Collections::emptyList)
    .stream()
    .filter(Objects::nonNull)
    .map(User::getName)
    .filter(Objects::nonNull)
    .toList();
```

### 6. 数值计算优先使用基本类型流

`mapToInt`、`mapToLong`、`mapToDouble` 可以避免 `Integer` 等包装类型的装箱和拆箱，统计代码也更直接。

```java
int total = orders.stream()
    .mapToInt(Order::getQuantity)
    .sum();

double average = orders.stream()
    .mapToDouble(Order::getAmountAsDouble)
    .average()
    .orElse(0D);
```

### 7. `Optional` 不要直接调用 `get()`

`findFirst`、`min`、`max` 等操作可能没有结果。根据业务选择默认值，或者使用带明确异常信息的 `orElseThrow`。

```java
User user = users.stream()
    .filter(User::isEnabled)
    .findFirst()
    .orElseThrow(() -> new IllegalStateException("没有可用用户"));
```

### 8. 谨慎使用 `peek`

`peek` 主要用于临时调试，不应承担修改对象、写数据库、发送消息等业务副作用。调试完成后应删除，正式业务逻辑应放在明确的终端操作或普通代码中。

### 9. 并行流不要默认使用

`parallelStream()` 会复用公共 `ForkJoinPool`，可能与应用中的其他任务互相影响；它也不适合数据库查询、远程调用等阻塞 IO。只有在数据量、任务独立性和基准测试都支持时才考虑使用，并确保收集器和共享对象是线程安全的。

### 10. 保持链路短小、命名清晰

不要把一条 Stream 链写成难以调试的“万能表达式”。复杂转换可以提取为有名字的方法；需要观察中间结果时，拆成多个局部变量通常比堆叠更多 `peek` 更好。

```java
List<Summary> summaries = orders.stream()
    .filter(this::isValidOrder)
    .map(this::toSummary)
    .toList();
```

### 最佳实践速记

| 建议 | 目的 |
|------|------|
| 先过滤，再转换和排序 | 减少无效计算 |
| 无副作用地处理数据 | 便于理解，也更安全 |
| `toMap` 显式处理重复 key | 避免运行时异常 |
| 数值计算使用 `mapToInt` 等 | 减少装箱拆箱 |
| 不滥用 `peek` 和并行流 | 避免隐藏副作用和线程问题 |
| 复杂逻辑使用普通循环或拆分方法 | 保持可读、可调试 |

------

## 🔗 相关笔记

- [[lambda表达式]] —— Stream 操作大量使用 Lambda 表达式
- [[四种特殊的接口]] —— Supplier / Consumer / Function / Predicate 是 Stream 操作的基础
- [[语法糖]] —— Lambda 与函数式接口的语法糖关系
- [[技术栈/Java与框架/多线程/API/CompletableFuture]] —— 异步编程中也大量使用 Stream

