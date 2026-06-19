# 😎😎😎聊聊 CompletableFuture：异步任务编排与并发执行

> Java 8 引入的 `java.util.concurrent.CompletableFuture`，是对 `Future` 的增强版，支持**链式编排、回调、组合、异常处理**，是异步编程的"瑞士军刀"。

---

## 📑 目录

- [一、为什么需要 CompletableFuture](#一为什么需要-completablefuture)
- [二、核心思想](#二核心思想)
- [三、四种创建方式](#三四种创建方式)
- [四、链式编排：结果转换与消费](#四链式编排结果转换与消费)
- [五、组合：多任务协同](#五组合多任务协同)
- [六、异常处理](#六异常处理)
- [七、allOf 与 anyOf](#七allof-与-anyof)
- [八、实战案例：电商下单](#八实战案例电商下单)
- [九、注意事项与坑](#九注意事项与坑)

---

## 一、为什么需要 CompletableFuture

### 1.1 传统 `Future` 的痛点

`Future` 是 Java 5 引入的异步结果占位符，但**只能阻塞 `get()` 取结果**，无法做到：

- 任务完成后**自动通知**我
- 多个异步任务**编排组合**（先后、并行、汇聚）
- 任务执行过程中**捕获异常**并处理
- 像写同步代码一样**链式调用**

### 1.2 CompletableFuture 解决了什么

| 能力 | 说明 |
|------|------|
| 主动完成 | `complete()` / `completeExceptionally()` 手动结束 |
| 回调通知 | `thenApply` / `thenAccept` / `thenRun` |
| 链式编排 | `thenCompose` 串行依赖 |
| 任务组合 | `thenCombine` 双源汇聚 |
| 多任务协同 | `allOf` 全完成 / `anyOf` 任一完成 |
| 异常处理 | `exceptionally` / `handle` / `whenComplete` |

### 1.3 适用场景

- 微服务间**并行调用**多个下游接口（聚合数据）
- 耗时操作**异步化**（发短信、写日志、查缓存）
- 复杂的**任务编排 DAG**（有向无环图）
- **流水线式**数据处理

---

## 二、核心思想

### 2.1 设计哲学

> **回调函数 + 状态机 + 函数式编程**

`CompletableFuture` 实现了 `Future<T>` 和 `CompletionStage<T>` 两个接口：
- `Future<T>`：拿到异步结果
- `CompletionStage<T>`：描述"完成之后做什么"，支持链式组合

### 2.2 状态流转

```mermaid
stateDiagram-v2
    [*] --> NEW: new CompletableFuture()
    NEW --> RUNNING: 任务开始执行
    NEW --> COMPLETED: complete()/completeExceptionally()
    RUNNING --> COMPLETED: 任务正常完成
    RUNNING --> FAILED: 任务抛异常
    COMPLETED --> [*]
    FAILED --> [*]
    note right of COMPLETED
        此后所有回调都会被触发
        且状态不再改变
    end note
```

### 2.3 三种触发方式

```mermaid
graph LR
    A[CompletableFuture] -->|1. 主动 complete| B[完成态]
    A -->|2. 异步任务执行完成| B
    A -->|3. 链式前置任务完成| B
    B --> C[触发后续回调链]
    style B fill:#90EE90
```

---

## 三、四种创建方式

### 3.1 方式一：直接 `new` + 手动完成

```java
CompletableFuture<String> cf = new CompletableFuture<>();
// 另起一个线程设置结果
new Thread(() -> {
    try {
        Thread.sleep(1000);
        cf.complete("hello");
    } catch (Exception e) {
        cf.completeExceptionally(e);
    }
}).start();
```

**适用场景**：跨线程传递结果（如 WebSocket、消息推送）。

### 3.2 方式二：`runAsync` —— 无返回值

```java
CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
    // 耗时操作，无返回值
    System.out.println("running in " + Thread.currentThread().getName());
});
```

- 默认使用 `ForkJoinPool.commonPool()`
- 可传入自定义 `Executor`

### 3.3 方式三：`supplyAsync` —— 有返回值

```java
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
    return "Hello CompletableFuture";
}, executor);
```

### 3.4 方式四：已完成的 `completedFuture`

```java
CompletableFuture<String> cf = CompletableFuture.completedFuture("已就绪");
```

**适用场景**：缓存命中、默认值快速返回。

### 3.5 创建方式速查表

| 方法 | 是否有返回值 | 线程池 |
|------|------|------|
| `new CompletableFuture<>()` | 手动 | 由调用方决定 |
| `runAsync(Runnable)` | 否 | ForkJoinPool / 自定义 |
| `supplyAsync(Supplier)` | 是 | ForkJoinPool / 自定义 |
| `completedFuture(value)` | 是 | 立即完成 |

---

## 四、链式编排：结果转换与消费

### 4.1 三大基础回调

```java
// 1. thenApply：转换结果（有入参，有返回值）
CompletableFuture<Integer> cf1 = CompletableFuture
    .supplyAsync(() -> "123")
    .thenApply(s -> Integer.parseInt(s) + 1);  // 124

// 2. thenAccept：消费结果（有入参，无返回值）
CompletableFuture<Void> cf2 = CompletableFuture
    .supplyAsync(() -> "result")
    .thenAccept(System.out::println);

// 3. thenRun：执行副作用（无入参，无返回值）
CompletableFuture<Void> cf3 = CompletableFuture
    .supplyAsync(() -> "x")
    .thenRun(() -> System.out.println("done"));
```

### 4.2 异步回调：xxxAsync

每个回调都有 `xxxAsync` 版本，**强制切换到线程池**执行：

```java
.thenApply(s -> s + "1")         // 沿用上一步的线程
.thenApplyAsync(s -> s + "2")    // 切到 ForkJoinPool
.thenApplyAsync(s -> s + "3", executor)  // 切到自定义线程池
```

### 4.3 thenApply vs thenCompose

```mermaid
graph LR
    A[CF<String>] -->|thenApply| B[CF<Integer>]
    A -->|thenCompose| C[CF<String>]
    B -.返回 Integer.-> D[嵌套一层包装]
    C -.返回 String.-> E[展平为 CF]
    style D fill:#FFB6C1
    style E fill:#90EE90
```

```java
// ❌ thenApply：返回 CompletableFuture<CompletableFuture<String>>
CompletableFuture<CompletableFuture<String>> bad = 
    CompletableFuture.supplyAsync(this::getUserId)
                      .thenApply(this::findUserAsync);

// ✅ thenCompose：展平为 CompletableFuture<String>
CompletableFuture<String> good = 
    CompletableFuture.supplyAsync(this::getUserId)
                      .thenCompose(this::findUserAsync);
```

**记忆口诀**：**Compose 是平铺，Apply 是套娃**。

---

## 五、组合：多任务协同

### 5.1 thenCombine：双任务汇聚

```java
CompletableFuture<String> cfA = CompletableFuture.supplyAsync(() -> "A");
CompletableFuture<Integer> cfB = CompletableFuture.supplyAsync(() -> 100);

CompletableFuture<String> result = cfA.thenCombine(cfB, (a, b) -> a + b);
// result = "A100"
```

**流程图**：

```mermaid
graph LR
    T1["任务A<br/>耗时 1s"] -->|完成后| Merge((合并))
    T2["任务B<br/>耗时 2s"] -->|完成后| Merge
    Merge --> Result["结果"]
    style Merge fill:#FFD700
```

### 5.2 thenAcceptBoth / runAfterBoth

```java
// 两个都完成后，消费结果
cfA.thenAcceptBoth(cfB, (a, b) -> System.out.println(a + b));

// 两个都完成后，执行动作
cfA.runAfterBoth(cfB, () -> System.out.println("都完成"));
```

### 5.3 applyToEither / acceptEither：谁快用谁

```java
CompletableFuture<String> result = cfA.applyToEither(cfB, s -> "先到的是：" + s);
```

**流程图**：

```mermaid
graph LR
    T1["任务A<br/>1s"] --> Race((竞争))
    T2["任务B<br/>2s"] --> Race
    Race -->|A 先到| R1["取 A 结果"]
    Race -->|B 先到| R2["取 B 结果"]
    style Race fill:#FF6347
```

### 5.4 串行 vs 并行 速查

| 模式 | 方法 | 关系 |
|------|------|------|
| 串行依赖 | `thenCompose` | A 完成才能开始 B |
| 汇聚组合 | `thenCombine` | A、B 都完成才触发 |
| 竞争选择 | `applyToEither` | A、B 谁先到用谁 |

---

## 六、异常处理

### 6.1 三大异常处理方法

```java
// 1. exceptionally：捕获异常，返回兜底值（类似 catch）
CompletableFuture<Integer> cf1 = CompletableFuture
    .supplyAsync(() -> { throw new RuntimeException("oops"); })
    .exceptionally(ex -> {
        System.out.println("捕获异常：" + ex.getMessage());
        return -1;
    });

// 2. handle：无论成败都能处理（有入参，有返回值）
CompletableFuture<Integer> cf2 = CompletableFuture
    .supplyAsync(() -> 10 / 0)
    .handle((result, ex) -> {
        if (ex != null) return 0;
        return result * 2;
    });

// 3. whenComplete：无论成败都能处理（无返回值）
CompletableFuture<Integer> cf3 = CompletableFuture
    .supplyAsync(() -> "x")
    .whenComplete((result, ex) -> {
        if (ex != null) System.out.println("失败了");
        else System.out.println("成功了：" + result);
    });
```

### 6.2 异常传播链

```mermaid
graph LR
    T1[Task1] -->|异常| T2[Task2]
    T2 -->|异常透传| T3[Task3]
    T3 -->|异常透传| Handle[handle/exceptionally]
    Handle -.->|吞掉或兜底| Result[结果]
    style Handle fill:#90EE90
```

**注意**：异常会沿着链**透传**，直到被 `handle` 或 `exceptionally` 捕获。

### 6.3 选择指南

| 方法 | 是否有返回值 | 是否能感知结果 | 典型用途 |
|------|------|------|------|
| `exceptionally` | 是 | 只能感知异常 | 兜底降级 |
| `handle` | 是 | 结果 + 异常都能感知 | 统一收口 |
| `whenComplete` | 否 | 结果 + 异常都能感知 | 日志埋点 |

---

## 七、allOf 与 anyOf

### 7.1 allOf：等待所有任务完成

```java
CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> "A");
CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> "B");
CompletableFuture<String> c = CompletableFuture.supplyAsync(() -> "C");

CompletableFuture<Void> all = CompletableFuture.allOf(a, b, c);
all.thenRun(() -> System.out.println("全部完成"));
```

**注意**：`allOf` 返回 `CompletableFuture<Void>`，**不会**汇聚各子任务的结果，需要手动从 `a/b/c` 取：

```java
all.thenRun(() -> {
    String ra = a.join();
    String rb = b.join();
    String rc = c.join();
    System.out.println(ra + rb + rc);
});
```

### 7.2 anyOf：任一任务完成即触发

```java
CompletableFuture<Object> any = CompletableFuture.anyOf(a, b, c);
any.thenAccept(first -> System.out.println("最先完成：" + first));
```

**注意**：返回类型是 `CompletableFuture<Object>`，**结果是 Object**。

### 7.3 流程图

```mermaid
graph TB
    subgraph allOf["allOf：全部完成"]
        A1[Task1] --> G1((汇聚))
        A2[Task2] --> G1
        A3[Task3] --> G1
        G1 --> R1[触发]
    end
    
    subgraph anyOf["anyOf：任一完成"]
        B1[Task1] --> G2((竞争))
        B2[Task2] --> G2
        B3[Task3] --> G2
        G2 --> R2[触发]
    end
    
    style G1 fill:#90EE90
    style G2 fill:#FF6347
```

### 7.4 完整版：allOf + 聚合结果

```java
public static <T> CompletableFuture<List<T>> allOfList(
        CompletableFuture<T>... futures) {
    return CompletableFuture.allOf(futures)
        .thenApply(v -> Stream.of(futures)
                              .map(CompletableFuture::join)
                              .collect(Collectors.toList()));
}
```

---

## 八、实战案例：电商下单

### 8.1 场景描述

下单需要：
1. **查用户信息**（1s）
2. **查商品库存**（1.5s）
3. **算优惠**（0.5s）
4. **扣库存 + 创建订单**（1s，依赖前 3 步）

### 8.2 串行 vs 并行优化

```mermaid
graph TB
    subgraph Serial["❌ 串行：4s"]
        S1[查用户 1s] --> S2[查库存 1.5s]
        S2 --> S3[算优惠 0.5s]
        S3 --> S4[下单 1s]
    end
    
    subgraph Parallel["✅ 并行：2.5s"]
        P1[查用户 1s] --> P4[下单 1s]
        P2[查库存 1.5s] --> P4
        P3[算优惠 0.5s] --> P4
    end
    
    style Serial fill:#FFE4E1
    style Parallel fill:#E0FFE0
```

### 8.3 完整代码

```java
public CompletableFuture<OrderResult> placeOrder(Long userId, Long skuId) {
    // 1、2、3 步并行
    CompletableFuture<User> userF = CompletableFuture
        .supplyAsync(() -> userService.getById(userId), ioPool);
    CompletableFuture<Sku> skuF = CompletableFuture
        .supplyAsync(() -> skuService.getById(skuId), ioPool);
    CompletableFuture<Discount> discountF = CompletableFuture
        .supplyAsync(() -> discountService.calc(skuId), ioPool);
    
    // 三步汇聚后下单
    return userF.thenCombine(skuF, User::withSku)
               .thenCombine(discountF, Order::applyDiscount)
               .thenCompose(order -> CompletableFuture
                   .supplyAsync(() -> orderService.create(order), ioPool))
               .exceptionally(ex -> {
                   log.error("下单失败", ex);
                   return OrderResult.fail(ex.getMessage());
               });
}
```

### 8.4 多接口聚合（Spring Cloud 场景）

```java
public CompletableFuture<HomePageVO> getHomePage(Long userId) {
    CompletableFuture<User> userF = CompletableFuture
        .supplyAsync(() -> userClient.getById(userId), ioPool);
    CompletableFuture<List<Order>> orderF = CompletableFuture
        .supplyAsync(() -> orderClient.listByUser(userId), ioPool);
    CompletableFuture<List<Coupon>> couponF = CompletableFuture
        .supplyAsync(() -> couponClient.listByUser(userId), ioPool);
    CompletableFuture<Cart> cartF = CompletableFuture
        .supplyAsync(() -> cartClient.get(userId), ioPool);
    
    return CompletableFuture.allOf(userF, orderF, couponF, cartF)
        .thenApply(v -> new HomePageVO(
            userF.join(), orderF.join(), couponF.join(), cartF.join()));
}
```

---

## 九、注意事项与坑

### 9.1 常见坑

| 坑 | 现象 | 解决方案 |
|----|------|----------|
| 包装 `Future` 用 `thenApply` | 出现 `CompletableFuture<CompletableFuture<T>>` | 改用 `thenCompose` |
| 业务线程池被 `join()` 阻塞 | 线程池耗尽 | 用 `thenApply`/`whenComplete` 替代 `join` |
| `allOf` 结果拿不到 | 不会汇聚各子任务结果 | 手动 `.join()` 各 CF 或封装为 `allOfList` |
| 异步任务抛异常没处理 | 结果永远拿不到（无 `get()` 抛 `TimeoutException`） | 末端必加 `exceptionally` / `handle` |
| `ForkJoinPool.commonPool()` 默认大小 | CPU 密集型不够用，IO 密集型被占满 | 自定义 `ThreadPoolExecutor` |

### 9.2 线程池选型

```java
// IO 密集型：多线程
private static final ExecutorService ioPool = new ThreadPoolExecutor(
    16, 32, 60, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1024),
    new ThreadFactoryBuilder().setNameFormat("io-pool-%d").build()
);

// CPU 密集型：与 CPU 核数相当
private static final ExecutorService cpuPool = new ThreadPoolExecutor(
    Runtime.getRuntime().availableProcessors(),
    Runtime.getRuntime().availableProcessors(),
    0, TimeUnit.MILLISECONDS,
    new SynchronousQueue<>(),
    new ThreadFactoryBuilder().setNameFormat("cpu-pool-%d").build()
);
```

### 9.3 整体 API 关系图

```mermaid
mindmap
  root((CompletableFuture))
    创建
      new CF
      runAsync
      supplyAsync
      completedFuture
    链式回调
      thenApply
      thenAccept
      thenRun
    组合
      thenCompose
      thenCombine
      applyToEither
    多任务
      allOf
      anyOf
    异常处理
      exceptionally
      handle
      whenComplete
    主动控制
      complete
      completeExceptionally
      get / join
```

### 9.4 一句话总结

> **CompletableFuture = Future + 回调 + 编排 + 异常处理**
> 异步任务的"乐高积木"，能把复杂的并发逻辑写得**像同步代码一样优雅**。🎉🎉🎉

---

## 🔗 相关笔记

- [[线程池七大核心参数]] —— 异步任务运行的载体
- [[../README]] —— 多线程总览
- [[../../Java/lambda表达式]] —— CompletableFuture 的回调大量使用 Lambda
- [[../../Java/四种特殊的接口]] —— Supplier / Function / Consumer 接口在 thenApply / thenAccept 中的应用
