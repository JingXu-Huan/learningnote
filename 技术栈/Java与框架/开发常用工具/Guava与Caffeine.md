# Guava 与 Caffeine：工具类、本地缓存与并发辅助 🧠

你的 `Campus-Water-IQ` 和 `auth2Demo` 都引入了 Guava，前者还使用了 Caffeine 版本管理。两者经常一起出现，但职责不同：

- `Guava`：集合、缓存接口、限流、重试辅助、字符串和前置条件等通用能力；
- `Caffeine`：高性能本地缓存实现，适合在单体或微服务实例内缓存热点数据。

## Guava 常用能力

### 1. 不可变集合

```java
ImmutableList<String> roles = ImmutableList.of("admin", "operator");
ImmutableMap<String, Integer> statusMap = ImmutableMap.of(
    "created", 1,
    "finished", 2
);
```

不可变集合适合常量、配置快照和跨线程共享数据，可以减少“创建后又被修改”的问题。不要把它当成数据库或分布式配置的替代品。

### 2. `Preconditions`：尽早失败

```java
public void update(Long id, String name) {
    Preconditions.checkNotNull(id, "id 不能为空");
    Preconditions.checkArgument(!name.isBlank(), "name 不能为空");
}
```

`Preconditions` 更适合内部方法和基础设施代码。对 Web 接口参数，优先使用 Bean Validation 和统一异常处理，避免直接把 `IllegalArgumentException` 暴露给客户端。

### 3. `RateLimiter`：进程内限速

```java
RateLimiter limiter = RateLimiter.create(100.0);

if (!limiter.tryAcquire(50, TimeUnit.MILLISECONDS)) {
    throw new TooManyRequestsException();
}
callRemoteService();
```

它只在当前 JVM 实例内生效，集群环境不能直接当作全局限流方案。动态限流、跨实例配额和租户隔离应考虑 Redis、网关或专用限流组件。

## Caffeine 本地缓存

```java
Cache<Long, User> userCache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .recordStats()
    .build();

User user = userCache.get(userId, this::loadUser);
```

`get(key, mappingFunction)` 可以避免常见的“先查缓存、没有再查数据库、然后写缓存”的重复模板。缓存加载函数要注意异常、并发击穿和查询耗时。

### `LoadingCache` 与异步加载

```java
LoadingCache<Long, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build(this::loadUser);
```

如果加载过程是远程 I/O，不能因为使用了本地缓存就忽略超时和线程池隔离。异步场景可以使用 `AsyncCache`，但要避免把阻塞数据库查询直接放进公共线程池。

## 缓存三大问题

### 1. 缓存穿透

大量请求查询不存在的数据。可以对明确不存在的结果缓存短时间的空值，也可以先做参数校验和布隆过滤，但空值缓存必须设置较短过期时间。

### 2. 缓存击穿

某个热点 key 过期后，大量请求同时回源。可以使用 Caffeine 的单 key 加载、互斥锁、逻辑过期或预热机制，避免所有请求同时打到数据库。

### 3. 缓存雪崩

大量 key 在同一时间过期，或者缓存服务整体不可用。过期时间增加随机抖动，做好本地缓存、降级和限流，并监控缓存命中率和回源耗时。

## 本地缓存和 Redis 如何分工

| 场景 | 本地缓存 | Redis |
|------|------|------|
| 单实例热点数据 | ✅ | 可选 |
| 多实例共享数据 | ❌ | ✅ |
| 强一致数据 | 谨慎 | 也需配合失效策略 |
| 极低延迟读 | ✅ | 网络有额外开销 |
| 需要统一失效通知 | 需事件同步 | 更适合 |

本地缓存不是 Redis 的“更快版本”，而是一个实例级缓存层。两级缓存同时使用时，要设计更新顺序、失效通知和故障降级，否则容易出现本地缓存长期旧数据。

## 监控与清理

- 监控命中率、加载成功率、加载耗时、驱逐数量和缓存大小；
- 不要把用户 Token、密码、超大对象或高基数无限增长的数据放入本地缓存；
- 应用关闭和配置刷新时，根据业务需要清理缓存；
- 发生缓存异常时不要让缓存问题拖垮主链路，读取路径应有降级边界。

## 一句话总结

> Guava 更像 Java 工具箱，Caffeine 更像高性能本地缓存；本地缓存解决单实例热点读取，不能自动解决分布式一致性和缓存失效问题。
