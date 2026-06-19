# Redisson 分布式锁详细文档

## 目录
1. [概述](#概述)
2. [核心概念](#核心概念)
3. [锁的类型](#锁的类型)
4. [实现原理](#实现原理)
5. [使用指南](#使用指南)
6. [最佳实践](#最佳实践)
7. [常见问题](#常见问题)
8. [性能优化](#性能优化)

---

## 概述

### 什么是 Redisson

Redisson 是一个在 Redis 基础上实现的 Java 驻内存数据网格（In-Memory Data Grid）。它不仅提供了一系列的分布式 Java 常用对象，还提供了许多分布式服务。Redisson 的分布式锁是其核心功能之一。

### 为什么需要分布式锁

在分布式系统中，多个节点可能同时访问共享资源，为了保证数据一致性和避免并发问题，需要使用分布式锁来协调不同节点之间的访问。

**应用场景：**
- 防止库存超卖
- 避免重复下单
- 定时任务防止重复执行
- 分布式事务协调
- 缓存更新的并发控制

---

## 核心概念

### 分布式锁的特性

一个合格的分布式锁应该具备以下特性：

1. **互斥性**：在任意时刻，只有一个客户端能持有锁
2. **防死锁**：即使持有锁的客户端崩溃，锁最终也能被释放
3. **容错性**：只要大部分 Redis 节点正常运行，客户端就能加锁和解锁
4. **可重入性**：同一个线程可以多次获取同一把锁

### Redisson 的优势

- **自动续期机制**（看门狗）：防止业务执行时间过长导致锁提前释放
- **支持多种锁类型**：可重入锁、公平锁、读写锁等
- **基于 Lua 脚本**：保证加锁和解锁操作的原子性
- **完善的异常处理**：提供丰富的 API 和异常机制
- **高性能**：基于 Netty 框架，支持异步操作

---

## 锁的类型

### 1. 可重入锁（Reentrant Lock）

最常用的分布式锁类型，支持同一线程多次获取同一把锁。

**基本使用：**

```java
RLock lock = redissonClient.getLock("myLock");

// 加锁
lock.lock();
try {
    // 执行业务逻辑
    System.out.println("执行业务代码");
} finally {
    // 释放锁
    lock.unlock();
}
```

**带超时的加锁：**

```java
// 尝试加锁，最多等待 100 秒，锁定后 10 秒自动解锁
boolean isLocked = lock.tryLock(100, 10, TimeUnit.SECONDS);
if (isLocked) {
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
} else {
    // 获取锁失败的处理
    System.out.println("获取锁失败");
}
```

**可重入示例：**

```java
RLock lock = redissonClient.getLock("reentrantLock");

lock.lock();
try {
    System.out.println("第一次获取锁");
    
    // 同一线程再次获取锁
    lock.lock();
    try {
        System.out.println("第二次获取锁（可重入）");
    } finally {
        lock.unlock();
    }
} finally {
    lock.unlock();
}
```

### 2. 公平锁（Fair Lock）

公平锁保证线程按照请求锁的顺序来获取锁，避免线程饥饿问题。

```java
RLock fairLock = redissonClient.getFairLock("fairLock");

fairLock.lock();
try {
    // 业务逻辑
} finally {
    fairLock.unlock();
}
```

**使用场景：**
- 需要严格按照请求顺序处理的业务
- 防止某些线程长期无法获取锁
- 高并发下需要公平调度的场景

### 3. 读写锁（ReadWrite Lock）

读写锁允许多个读操作并发执行，但写操作是互斥的。

```java
RReadWriteLock rwLock = redissonClient.getReadWriteLock("rwLock");

// 读锁
RLock readLock = rwLock.readLock();
readLock.lock();
try {
    // 读操作，可以多个线程同时执行
    System.out.println("读取数据");
} finally {
    readLock.unlock();
}

// 写锁
RLock writeLock = rwLock.writeLock();
writeLock.lock();
try {
    // 写操作，互斥执行
    System.out.println("写入数据");
} finally {
    writeLock.unlock();
}
```

**使用场景：**
- 读多写少的场景
- 缓存更新
- 配置文件读写

### 4. 联锁（MultiLock）

可以同时锁定多个资源，只有所有锁都获取成功才算成功。

```java
RLock lock1 = redissonClient.getLock("lock1");
RLock lock2 = redissonClient.getLock("lock2");
RLock lock3 = redissonClient.getLock("lock3");

RedissonMultiLock multiLock = new RedissonMultiLock(lock1, lock2, lock3);

multiLock.lock();
try {
    // 所有锁都获取成功后执行业务逻辑
} finally {
    multiLock.unlock();
}
```

**使用场景：**
- 需要同时锁定多个资源
- 防止死锁（按顺序获取锁）
- 分布式事务

### 5. 红锁（RedLock）

基于多个独立的 Redis 实例实现的分布式锁，提高可靠性和容错性。

```java
RLock lock1 = redissonClient1.getLock("lock");
RLock lock2 = redissonClient2.getLock("lock");
RLock lock3 = redissonClient3.getLock("lock");

RedissonRedLock redLock = new RedissonRedLock(lock1, lock2, lock3);

redLock.lock();
try {
    // 业务逻辑
} finally {
    redLock.unlock();
}
```

**使用场景：**
- 对可靠性要求极高的场景
- 多机房部署
- 防止单点故障

### 6. 信号量（Semaphore）

用于限制同时访问特定资源的线程数量。

```java
RSemaphore semaphore = redissonClient.getSemaphore("semaphore");

// 设置许可数量
semaphore.trySetPermits(3);

// 获取许可
semaphore.acquire();
try {
    // 业务逻辑，最多 3 个线程同时执行
} finally {
    // 释放许可
    semaphore.release();
}
```

**使用场景：**
- 限流
- 资源池管理
- 并发控制

### 7. 可过期性信号量（PermitExpirableSemaphore）

带有过期时间的信号量，许可会自动过期。

```java
RPermitExpirableSemaphore semaphore = 
    redissonClient.getPermitExpirableSemaphore("expirableSemaphore");

semaphore.trySetPermits(3);

String permitId = semaphore.acquire(2, TimeUnit.SECONDS);
try {
    // 业务逻辑
} finally {
    semaphore.release(permitId);
}
```

### 8. 闭锁（CountDownLatch）

允许一个或多个线程等待其他线程完成操作。

```java
RCountDownLatch latch = redissonClient.getCountDownLatch("latch");

// 设置计数
latch.trySetCount(3);

// 等待线程
new Thread(() -> {
    try {
        latch.await();
        System.out.println("所有任务完成");
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}).start();

// 工作线程
for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        // 执行任务
        System.out.println("任务完成");
        latch.countDown();
    }).start();
}
```

---

## 实现原理

### 加锁原理

Redisson 使用 Lua 脚本来实现加锁操作，保证原子性。

**加锁的 Lua 脚本逻辑：**

```lua
-- KEYS[1]: 锁的名称
-- ARGV[1]: 锁的过期时间
-- ARGV[2]: 线程标识（UUID + 线程ID）

-- 判断锁是否存在
if (redis.call('exists', KEYS[1]) == 0) then
    -- 锁不存在，设置锁并设置过期时间
    redis.call('hset', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;

-- 锁存在，判断是否是当前线程持有
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    -- 是当前线程，重入次数加 1
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;

-- 锁被其他线程持有，返回锁的剩余过期时间
return redis.call('pttl', KEYS[1]);
```

**数据结构：**

Redis 中存储的锁数据结构是 Hash：

```
Key: "myLock"
Field: "UUID:ThreadID"
Value: 重入次数
```

示例：
```
myLock: {
    "8743c9c0-0795-4907-87fd-6c719a6b4586:1": 3
}
```

### 解锁原理

**解锁的 Lua 脚本逻辑：**

```lua
-- KEYS[1]: 锁的名称
-- ARGV[1]: 发布订阅的频道
-- ARGV[2]: 解锁消息
-- ARGV[3]: 线程标识

-- 判断锁是否是当前线程持有
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil;
end;

-- 重入次数减 1
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);

-- 如果还有重入，刷新过期时间
if (counter > 0) then
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return 0;
else
    -- 重入次数为 0，删除锁
    redis.call('del', KEYS[1]);
    -- 发布解锁消息
    redis.call('publish', ARGV[1], ARGV[2]);
    return 1;
end;

return nil;
```

### 看门狗机制（Watchdog）

看门狗是 Redisson 的核心特性之一，用于自动延长锁的过期时间。

**工作原理：**

1. 如果没有指定锁的过期时间，默认使用 30 秒
2. 启动一个后台线程（看门狗）
3. 每隔 10 秒（`lockWatchdogTimeout / 3`）检查一次
4. 如果锁仍然被当前线程持有，就续期到 30 秒

**配置看门狗：**

```java
Config config = new Config();
// 设置看门狗超时时间为 30000 毫秒（30 秒）
config.setLockWatchdogTimeout(30000);

RedissonClient redisson = Redisson.create(config);
```

**关闭看门狗：**

如果指定了锁的过期时间，看门狗不会启动：

```java
// 指定过期时间，看门狗不会启动
lock.lock(10, TimeUnit.SECONDS);
```

### 订阅发布机制

Redisson 使用 Redis 的 Pub/Sub 机制来实现锁的等待和唤醒。

**流程：**

1. 线程尝试获取锁失败后，订阅该锁的释放消息
2. 持有锁的线程释放锁时，发布解锁消息
3. 等待的线程收到消息后，再次尝试获取锁

**优势：**
- 减少无效的轮询
- 降低 CPU 占用
- 提高响应速度

---

## 使用指南

### 环境配置

**Maven 依赖：**

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>3.24.3</version>
</dependency>
```

**Gradle 依赖：**

```gradle
implementation 'org.redisson:redisson:3.24.3'
```

### 基础配置

**单机模式：**

```java
Config config = new Config();
config.useSingleServer()
    .setAddress("redis://127.0.0.1:6379")
    .setPassword("your_password")
    .setDatabase(0)
    .setConnectionMinimumIdleSize(10)
    .setConnectionPoolSize(64)
    .setIdleConnectionTimeout(10000)
    .setConnectTimeout(10000)
    .setTimeout(3000);

RedissonClient redisson = Redisson.create(config);
```

**集群模式：**

```java
Config config = new Config();
config.useClusterServers()
    .addNodeAddress("redis://127.0.0.1:7001")
    .addNodeAddress("redis://127.0.0.1:7002")
    .addNodeAddress("redis://127.0.0.1:7003")
    .setPassword("your_password")
    .setScanInterval(2000)
    .setMasterConnectionMinimumIdleSize(10)
    .setMasterConnectionPoolSize(64)
    .setSlaveConnectionMinimumIdleSize(10)
    .setSlaveConnectionPoolSize(64);

RedissonClient redisson = Redisson.create(config);
```

**哨兵模式：**

```java
Config config = new Config();
config.useSentinelServers()
    .setMasterName("mymaster")
    .addSentinelAddress("redis://127.0.0.1:26379")
    .addSentinelAddress("redis://127.0.0.1:26380")
    .addSentinelAddress("redis://127.0.0.1:26381")
    .setPassword("your_password")
    .setDatabase(0);

RedissonClient redisson = Redisson.create(config);
```

### Spring Boot 集成

**方式一：配置文件方式**

application.yml:

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password: your_password
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-wait: -1
        max-idle: 8
        min-idle: 0

redisson:
  lock-watchdog-timeout: 30000
  codec: org.redisson.codec.JsonJacksonCodec
```

配置类：

```java
@Configuration
public class RedissonConfig {
    
    @Value("${spring.redis.host}")
    private String host;
    
    @Value("${spring.redis.port}")
    private String port;
    
    @Value("${spring.redis.password}")
    private String password;
    
    @Value("${spring.redis.database}")
    private int database;
    
    @Value("${redisson.lock-watchdog-timeout}")
    private long lockWatchdogTimeout;
    
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setLockWatchdogTimeout(lockWatchdogTimeout);
        config.useSingleServer()
            .setAddress("redis://" + host + ":" + port)
            .setPassword(password)
            .setDatabase(database)
            .setConnectionMinimumIdleSize(10)
            .setConnectionPoolSize(64)
            .setIdleConnectionTimeout(10000)
            .setConnectTimeout(10000)
            .setTimeout(3000);
        
        return Redisson.create(config);
    }
}
```

**方式二：使用 redisson-spring-boot-starter**

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.24.3</version>
</dependency>
```

application.yml:

```yaml
spring:
  redis:
    redisson:
      file: classpath:redisson.yaml
```

redisson.yaml:

```yaml
singleServerConfig:
  address: "redis://127.0.0.1:6379"
  password: your_password
  database: 0
  connectionMinimumIdleSize: 10
  connectionPoolSize: 64
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000

lockWatchdogTimeout: 30000
codec: !<org.redisson.codec.JsonJacksonCodec> {}
```

### 实际应用案例

**案例 1：防止库存超卖**

```java
@Service
public class OrderService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private ProductMapper productMapper;
    
    @Autowired
    private OrderMapper orderMapper;
    
    public boolean createOrder(Long productId, Long userId, Integer quantity) {
        String lockKey = "product:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 10 秒，锁定后 30 秒自动释放
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (!isLocked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            
            // 查询商品库存
            Product product = productMapper.selectById(productId);
            if (product == null) {
                throw new BusinessException("商品不存在");
            }
            
            if (product.getStock() < quantity) {
                throw new BusinessException("库存不足");
            }
            
            // 扣减库存
            product.setStock(product.getStock() - quantity);
            productMapper.updateById(product);
            
            // 创建订单
            Order order = new Order();
            order.setProductId(productId);
            order.setUserId(userId);
            order.setQuantity(quantity);
            order.setCreateTime(new Date());
            orderMapper.insert(order);
            
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("获取锁被中断");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**案例 2：防止重复下单**

```java
@Service
public class OrderService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public boolean submitOrder(String orderNo, Long userId) {
        String lockKey = "order:submit:" + userId + ":" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 0 秒（立即返回），锁定后 5 秒自动释放
            boolean isLocked = lock.tryLock(0, 5, TimeUnit.SECONDS);
            
            if (!isLocked) {
                throw new BusinessException("请勿重复提交订单");
            }
            
            // 检查订单是否已存在
            Order existOrder = orderMapper.selectByOrderNo(orderNo);
            if (existOrder != null) {
                throw new BusinessException("订单已存在");
            }
            
            // 创建订单逻辑
            // ...
            
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("提交订单失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**案例 3：定时任务防止重复执行**

```java
@Component
public class ScheduledTask {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨 1 点执行
    public void dailyTask() {
        String lockKey = "scheduled:task:daily";
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 0 秒，锁定后 10 分钟自动释放
            boolean isLocked = lock.tryLock(0, 10, TimeUnit.MINUTES);
            
            if (!isLocked) {
                log.info("任务正在其他节点执行，跳过本次执行");
                return;
            }
            
            log.info("开始执行定时任务");
            
            // 执行任务逻辑
            // ...
            
            log.info("定时任务执行完成");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("定时任务执行失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**案例 4：缓存更新**

```java
@Service
public class UserService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public User getUser(Long userId) {
        String cacheKey = "user:info:" + userId;
        
        // 先从缓存获取
        User user = (User) redisTemplate.opsForValue().get(cacheKey);
        if (user != null) {
            return user;
        }
        
        // 使用读写锁
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("user:lock:" + userId);
        RLock readLock = rwLock.readLock();
        
        readLock.lock();
        try {
            // 再次检查缓存（双重检查）
            user = (User) redisTemplate.opsForValue().get(cacheKey);
            if (user != null) {
                return user;
            }
            
            // 释放读锁，获取写锁
            readLock.unlock();
            RLock writeLock = rwLock.writeLock();
            writeLock.lock();
            
            try {
                // 三次检查缓存
                user = (User) redisTemplate.opsForValue().get(cacheKey);
                if (user != null) {
                    return user;
                }
                
                // 从数据库查询
                user = userMapper.selectById(userId);
                
                if (user != null) {
                    // 写入缓存
                    redisTemplate.opsForValue().set(cacheKey, user, 30, TimeUnit.MINUTES);
                }
                
                return user;
                
            } finally {
                writeLock.unlock();
            }
            
        } finally {
            if (readLock.isHeldByCurrentThread()) {
                readLock.unlock();
            }
        }
    }
    
    public void updateUser(User user) {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("user:lock:" + user.getId());
        RLock writeLock = rwLock.writeLock();
        
        writeLock.lock();
        try {
            // 更新数据库
            userMapper.updateById(user);
            
            // 删除缓存
            String cacheKey = "user:info:" + user.getId();
            redisTemplate.delete(cacheKey);
            
        } finally {
            writeLock.unlock();
        }
    }
}
```

**案例 5：限流控制**

```java
@Service
public class RateLimiterService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 限流检查
     * @param resource 资源标识
     * @param maxPermits 最大许可数
     * @return 是否通过限流
     */
    public boolean tryAcquire(String resource, int maxPermits) {
        String semaphoreKey = "rate:limiter:" + resource;
        RSemaphore semaphore = redissonClient.getSemaphore(semaphoreKey);
        
        try {
            // 设置许可数
            if (!semaphore.isExists()) {
                semaphore.trySetPermits(maxPermits);
                // 设置过期时间
                semaphore.expire(1, TimeUnit.MINUTES);
            }
            
            // 尝试获取许可
            boolean acquired = semaphore.tryAcquire(0, TimeUnit.SECONDS);
            
            if (acquired) {
                // 异步释放许可（1 秒后）
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(1000);
                        semaphore.release();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            
            return acquired;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

@RestController
@RequestMapping("/api")
public class ApiController {
    
    @Autowired
    private RateLimiterService rateLimiterService;
    
    @GetMapping("/data")
    public Result getData() {
        // 每分钟最多 100 次请求
        if (!rateLimiterService.tryAcquire("api:data", 100)) {
            return Result.error("请求过于频繁，请稍后重试");
        }
        
        // 处理业务逻辑
        return Result.success();
    }
}
```

---

## 最佳实践

### 1. 合理设置超时时间

```java
// ❌ 不推荐：使用默认超时（可能导致锁长期持有）
lock.lock();

// ✅ 推荐：根据业务预估合理的超时时间
lock.lock(10, TimeUnit.SECONDS);

// ✅ 推荐：使用 tryLock 避免无限等待
boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
```

**原则：**
- 评估业务执行时间，设置略大于业务时间的超时
- 使用 `tryLock` 设置等待时间，避免线程长时间阻塞
- 对于可能长时间运行的任务，使用看门狗机制

### 2. 必须在 finally 中释放锁

```java
// ❌ 错误示范：可能导致锁无法释放
RLock lock = redissonClient.getLock("myLock");
lock.lock();
// 业务逻辑
lock.unlock();

// ✅ 正确示范：保证锁一定会被释放
RLock lock = redissonClient.getLock("myLock");
lock.lock();
try {
    // 业务逻辑
} finally {
    lock.unlock();
}

// ✅ 更安全的方式：检查锁是否被当前线程持有
try {
    lock.lock();
    // 业务逻辑
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

### 3. 锁的粒度要合理

```java
// ❌ 锁粒度过大：影响并发性能
RLock lock = redissonClient.getLock("global:lock");
lock.lock();
try {
    // 处理不同用户的订单
} finally {
    lock.unlock();
}

// ✅ 锁粒度合理：按用户或订单加锁
RLock lock = redissonClient.getLock("order:lock:" + userId);
lock.lock();
try {
    // 处理该用户的订单
} finally {
    lock.unlock();
}
```

**原则：**
- 锁的范围越小，并发性能越好
- 根据业务场景设计合理的锁粒度
- 避免使用全局锁

### 4. 避免死锁

```java
// ❌ 可能导致死锁：不同顺序获取多个锁
// 线程 1
lock1.lock();
lock2.lock();

// 线程 2
lock2.lock();
lock1.lock();

// ✅ 使用 MultiLock 避免死锁
RedissonMultiLock multiLock = new RedissonMultiLock(lock1, lock2);
multiLock.lock();
try {
    // 业务逻辑
} finally {
    multiLock.unlock();
}

// ✅ 按固定顺序获取锁
List<RLock> locks = Arrays.asList(lock1, lock2);
locks.sort(Comparator.comparing(lock -> lock.getName()));
for (RLock lock : locks) {
    lock.lock();
}
try {
    // 业务逻辑
} finally {
    for (int i = locks.size() - 1; i >= 0; i--) {
        locks.get(i).unlock();
    }
}
```

### 5. 异常处理

```java
RLock lock = redissonClient.getLock("myLock");

try {
    boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
    
    if (!isLocked) {
        // 获取锁失败的处理
        throw new BusinessException("系统繁忙，请稍后重试");
    }
    
    try {
        // 业务逻辑
        processBusinessLogic();
        
    } catch (BusinessException e) {
        // 业务异常处理
        log.error("业务处理失败", e);
        throw e;
        
    } catch (Exception e) {
        // 其他异常处理
        log.error("系统异常", e);
        throw new SystemException("系统异常", e);
        
    } finally {
        // 确保释放锁
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
    
} catch (InterruptedException e) {
    // 中断异常处理
    Thread.currentThread().interrupt();
    throw new SystemException("线程被中断", e);
}
```

### 6. 锁的命名规范

```java
// ✅ 推荐的锁命名规范
String lockKey = String.format("%s:%s:%s", 
    "业务模块",    // 如：order, product, user
    "操作类型",    // 如：create, update, delete
    "业务标识"     // 如：订单号, 用户ID
);

// 示例
String lockKey = "order:create:" + userId;
String lockKey = "product:stock:" + productId;
String lockKey = "user:update:" + userId;
```

**命名原则：**
- 使用清晰的业务语义
- 使用冒号分隔不同层级
- 包含必要的业务标识
- 避免使用过长的键名

### 7. 监控和日志

```java
@Aspect
@Component
@Slf4j
public class RedissonLockAspect {
    
    @Around("@annotation(redissonLock)")
    public Object around(ProceedingJoinPoint pjp, RedissonLock redissonLock) throws Throwable {
        String lockKey = redissonLock.key();
        long startTime = System.currentTimeMillis();
        
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean isLocked = lock.tryLock(
                redissonLock.waitTime(),
                redissonLock.leaseTime(),
                redissonLock.timeUnit()
            );
            
            if (!isLocked) {
                log.warn("获取锁失败: lockKey={}, waitTime={}ms", 
                    lockKey, redissonLock.waitTime());
                throw new BusinessException("系统繁忙");
            }
            
            log.info("获取锁成功: lockKey={}, costTime={}ms", 
                lockKey, System.currentTimeMillis() - startTime);
            
            try {
                return pjp.proceed();
            } finally {
                long executeTime = System.currentTimeMillis() - startTime;
                log.info("释放锁: lockKey={}, executeTime={}ms", lockKey, executeTime);
                
                // 监控执行时间
                if (executeTime > 5000) {
                    log.warn("锁执行时间过长: lockKey={}, executeTime={}ms", 
                        lockKey, executeTime);
                }
            }
            
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

### 8. 使用注解简化代码

自定义注解：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedissonLock {
    
    /**
     * 锁的 key
     */
    String key();
    
    /**
     * 等待时间（默认 10 秒）
     */
    long waitTime() default 10;
    
    /**
     * 锁定时间（默认 30 秒）
     */
    long leaseTime() default 30;
    
    /**
     * 时间单位（默认秒）
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
```

使用示例：

```java
@Service
public class OrderService {
    
    @RedissonLock(
        key = "'order:create:' + #userId",
        waitTime = 5,
        leaseTime = 10
    )
    public void createOrder(Long userId, OrderDTO orderDTO) {
        // 业务逻辑，自动加锁和解锁
    }
}
```

### 9. 性能优化建议

**使用连接池：**

```java
Config config = new Config();
config.useSingleServer()
    .setConnectionMinimumIdleSize(10)  // 最小空闲连接数
    .setConnectionPoolSize(64)          // 连接池大小
    .setIdleConnectionTimeout(10000)    // 空闲连接超时
    .setConnectTimeout(10000)           // 连接超时
    .setTimeout(3000);                  // 命令超时
```

**批量操作：**

```java
// ❌ 多次网络调用
for (String key : keys) {
    RLock lock = redissonClient.getLock(key);
    lock.lock();
    // ...
    lock.unlock();
}

// ✅ 使用 MultiLock 减少网络调用
RLock[] locks = keys.stream()
    .map(redissonClient::getLock)
    .toArray(RLock[]::new);
    
RedissonMultiLock multiLock = new RedissonMultiLock(locks);
multiLock.lock();
try {
    // 业务逻辑
} finally {
    multiLock.unlock();
}
```

**异步操作：**

```java
RLock lock = redissonClient.getLock("myLock");

// 异步加锁
RFuture<Void> lockFuture = lock.lockAsync();
lockFuture.whenComplete((result, exception) -> {
    if (exception != null) {
        log.error("加锁失败", exception);
        return;
    }
    
    try {
        // 业务逻辑
    } finally {
        lock.unlockAsync();
    }
});
```

---

## 常见问题

### Q1: 锁过期了但业务还没执行完怎么办？

**问题描述：**
如果设置的锁过期时间太短，业务逻辑还没执行完锁就被释放了，可能导致其他线程获取到锁，造成并发问题。

**解决方案：**

1. **使用看门狗机制**（推荐）：

```java
// 不指定过期时间，启用看门狗自动续期
lock.lock();
try {
    // 长时间运行的业务逻辑
    longRunningTask();
} finally {
    lock.unlock();
}
```

2. **合理评估业务时间，设置足够的过期时间**：

```java
// 预估业务需要 20 秒，设置 30 秒超时
lock.lock(30, TimeUnit.SECONDS);
```

3. **手动续期**：

```java
lock.lock(10, TimeUnit.SECONDS);
try {
    // 执行部分业务
    doSomething();
    
    // 手动续期
    lock.expire(10, TimeUnit.SECONDS);
    
    // 继续执行业务
    doMore();
} finally {
    lock.unlock();
}
```

### Q2: 如何避免死锁？

**常见死锁场景：**

1. **多个锁的获取顺序不一致**：

```java
// 线程 A
lock1.lock();
lock2.lock();

// 线程 B（顺序相反）
lock2.lock();
lock1.lock();
```

**解决方案：**

使用 MultiLock：

```java
RedissonMultiLock multiLock = new RedissonMultiLock(lock1, lock2);
multiLock.lock();
try {
    // 业务逻辑
} finally {
    multiLock.unlock();
}
```

2. **锁没有被正确释放**：

**解决方案：**

```java
// 始终在 finally 中释放锁
try {
    lock.lock();
    // 业务逻辑
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

### Q3: tryLock 和 lock 的区别？

**lock() 方法：**
- 阻塞式获取锁
- 一直等待直到获取到锁
- 如果一直获取不到会永久阻塞

**tryLock() 方法：**
- 非阻塞式尝试获取锁
- 可以设置等待时间
- 获取失败立即返回 false

**使用建议：**

```java
// 场景 1：必须获取到锁才能执行（使用 lock）
lock.lock();
try {
    // 必须执行的关键业务
} finally {
    lock.unlock();
}

// 场景 2：获取不到锁可以放弃（使用 tryLock）
boolean isLocked = lock.tryLock(5, TimeUnit.SECONDS);
if (isLocked) {
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
} else {
    // 获取锁失败的处理
    return "系统繁忙";
}
```

### Q4: 锁的重入次数有限制吗？

Redisson 的可重入锁理论上没有重入次数限制，但实际使用中应该避免过深的重入。

**示例：**

```java
RLock lock = redissonClient.getLock("reentrantLock");

public void method1() {
    lock.lock();  // 第 1 次获取锁
    try {
        method2();
    } finally {
        lock.unlock();
    }
}

public void method2() {
    lock.lock();  // 第 2 次获取锁（重入）
    try {
        method3();
    } finally {
        lock.unlock();
    }
}

public void method3() {
    lock.lock();  // 第 3 次获取锁（重入）
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
}
```

**注意事项：**
- 每次 `lock()` 必须对应一次 `unlock()`
- 重入次数存储在 Redis 的 Hash 结构中
- 过深的重入会增加代码复杂度，建议优化代码结构

### Q5: 如何保证锁不会被其他线程误删？

Redisson 通过存储线程标识来保证锁只能被持有它的线程释放。

**实现机制：**

```
锁的数据结构：
Key: "myLock"
Field: "UUID:ThreadID"  // 客户端 UUID + 线程 ID
Value: 重入次数
```

**解锁时的检查：**

```lua
-- 检查锁是否由当前线程持有
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil;  -- 不是当前线程持有，拒绝解锁
end;
```

**手动实现示例：**

```java
String lockValue = UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();

// 加锁时存储线程标识
redisTemplate.opsForValue().set(lockKey, lockValue, 30, TimeUnit.SECONDS);

// 解锁时检查
String currentValue = redisTemplate.opsForValue().get(lockKey);
if (lockValue.equals(currentValue)) {
    redisTemplate.delete(lockKey);
}
```

### Q6: Redisson 锁和 Zookeeper 锁的区别？

**Redisson（Redis）锁：**

优点：
- 性能高，延迟低
- 实现简单
- 支持多种锁类型
- 有成熟的客户端库

缺点：
- 强依赖 Redis 可用性
- 主从切换可能丢失锁
- 需要考虑锁过期问题

**Zookeeper 锁：**

优点：
- 强一致性保证
- 自动释放（session 过期）
- 支持公平锁
- 不需要考虑锁过期

缺点：
- 性能相对较低
- 实现复杂
- 运维成本高

**选择建议：**
- 对性能要求高：选择 Redisson
- 对一致性要求极高：选择 Zookeeper
- 一般业务场景：Redisson 足够

### Q7: 如何处理 Redis 主从切换导致的锁丢失？

**问题场景：**
1. 客户端 A 在 Master 节点获取锁成功
2. Master 宕机，锁数据还未同步到 Slave
3. Slave 提升为新的 Master
4. 客户端 B 在新 Master 上获取到同一把锁
5. 客户端 A 和 B 同时持有锁

**解决方案：**

1. **使用 RedLock（多 Redis 实例）**：

```java
// 配置多个独立的 Redis 实例
RedissonClient client1 = Redisson.create(config1);
RedissonClient client2 = Redisson.create(config2);
RedissonClient client3 = Redisson.create(config3);

RLock lock1 = client1.getLock("lock");
RLock lock2 = client2.getLock("lock");
RLock lock3 = client3.getLock("lock");

RedissonRedLock redLock = new RedissonRedLock(lock1, lock2, lock3);

redLock.lock();
try {
    // 业务逻辑
} finally {
    redLock.unlock();
}
```

2. **使用 Redis Cluster**：

Redis Cluster 的强一致性配置：

```
min-replicas-to-write 1
min-replicas-max-lag 10
```

3. **业务层面的幂等性设计**：

即使发生锁丢失，也通过业务幂等性保证数据一致性。

### Q8: 看门狗机制会不会导致锁永远不释放？

**不会**，看门狗有以下保护机制：

1. **只在锁被当前线程持有时续期**：

```java
if (lock.isHeldByCurrentThread()) {
    // 续期
    lock.expire(30, TimeUnit.SECONDS);
}
```

2. **客户端断开连接或崩溃**：
   - 看门狗线程停止
   - 锁在到期后自动释放

3. **显式解锁**：

```java
lock.unlock();  // 会停止看门狗并立即释放锁
```

**最佳实践：**

```java
// 推荐：使用 try-finally 确保释放
lock.lock();
try {
    // 业务逻辑
} finally {
    lock.unlock();  // 即使异常也会释放锁并停止看门狗
}
```

---

## 性能优化

### 1. 连接池配置优化

```java
Config config = new Config();
config.useSingleServer()
    // 连接池大小（默认 64）
    .setConnectionPoolSize(128)
    
    // 最小空闲连接数（默认 10）
    .setConnectionMinimumIdleSize(20)
    
    // 连接超时（毫秒，默认 10000）
    .setConnectTimeout(5000)
    
    // 命令执行超时（毫秒，默认 3000）
    .setTimeout(3000)
    
    // 空闲连接超时（毫秒，默认 10000）
    .setIdleConnectionTimeout(10000)
    
    // 重试次数（默认 3）
    .setRetryAttempts(3)
    
    // 重试间隔（毫秒，默认 1500）
    .setRetryInterval(1500);
```

### 2. 减少网络往返

**使用 MultiLock：**

```java
// ❌ 多次网络调用
RLock lock1 = redissonClient.getLock("lock1");
RLock lock2 = redissonClient.getLock("lock2");
lock1.lock();
lock2.lock();

// ✅ 一次网络调用
RedissonMultiLock multiLock = new RedissonMultiLock(lock1, lock2);
multiLock.lock();
```

**批量操作：**

```java
// 使用 RBatch 批量执行命令
RBatch batch = redissonClient.createBatch();
RLockAsync lock1 = batch.getLock("lock1");
RLockAsync lock2 = batch.getLock("lock2");

lock1.lockAsync();
lock2.lockAsync();

// 一次性执行所有命令
BatchResult<?> result = batch.execute();
```

### 3. 异步操作

```java
RLock lock = redissonClient.getLock("myLock");

// 异步加锁
RFuture<Void> lockFuture = lock.lockAsync(10, TimeUnit.SECONDS);

lockFuture.whenComplete((result, exception) -> {
    if (exception != null) {
        // 处理异常
        return;
    }
    
    // 执行业务逻辑
    CompletableFuture.runAsync(() -> {
        try {
            doBusinessLogic();
        } finally {
            lock.unlockAsync();
        }
    });
});
```

### 4. 合理使用锁类型

**读多写少场景：使用读写锁**

```java
RReadWriteLock rwLock = redissonClient.getReadWriteLock("rwLock");

// 读操作（可并发）
RLock readLock = rwLock.readLock();
readLock.lock();
try {
    // 读取数据
} finally {
    readLock.unlock();
}

// 写操作（互斥）
RLock writeLock = rwLock.writeLock();
writeLock.lock();
try {
    // 写入数据
} finally {
    writeLock.unlock();
}
```

**限流场景：使用信号量**

```java
// 使用信号量比使用多个锁更高效
RSemaphore semaphore = redissonClient.getSemaphore("semaphore");
semaphore.trySetPermits(10);

semaphore.acquire();
try {
    // 业务逻辑
} finally {
    semaphore.release();
}
```

### 5. 监控指标

建议监控以下指标：

```java
@Component
public class RedissonMetrics {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Scheduled(fixedRate = 60000) // 每分钟采集一次
    public void collectMetrics() {
        Config config = redissonClient.getConfig();
        
        // 连接池使用情况
        int totalConnections = getTotalConnections();
        int activeConnections = getActiveConnections();
        int idleConnections = getIdleConnections();
        
        log.info("Redisson连接池: total={}, active={}, idle={}", 
            totalConnections, activeConnections, idleConnections);
        
        // 锁的等待时间
        // 锁的持有时间
        // 锁的获取成功率
    }
}
```

**关键指标：**
- 锁获取成功率
- 平均等待时间
- 平均持有时间
- 锁超时次数
- 连接池使用率

### 6. 压力测试

```java
@Test
public void lockPerformanceTest() throws InterruptedException {
    int threadCount = 100;
    int iterations = 1000;
    
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);
    
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < threadCount; i++) {
        new Thread(() -> {
            for (int j = 0; j < iterations; j++) {
                RLock lock = redissonClient.getLock("test:lock");
                try {
                    boolean isLocked = lock.tryLock(1, 5, TimeUnit.SECONDS);
                    if (isLocked) {
                        successCount.incrementAndGet();
                        try {
                            // 模拟业务逻辑
                            Thread.sleep(10);
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            latch.countDown();
        }).start();
    }
    
    latch.await();
    long endTime = System.currentTimeMillis();
    
    System.out.println("总耗时: " + (endTime - startTime) + "ms");
    System.out.println("成功次数: " + successCount.get());
    System.out.println("失败次数: " + failCount.get());
    System.out.println("TPS: " + (successCount.get() * 1000.0 / (endTime - startTime)));
}
```

---

## 总结

Redisson 提供了强大而灵活的分布式锁实现，通过合理使用可以解决分布式系统中的并发控制问题。

**核心要点：**

1. **选择合适的锁类型**：根据业务场景选择可重入锁、读写锁、信号量等
2. **合理设置超时**：评估业务执行时间，设置合理的锁超时时间
3. **正确释放锁**：始终在 finally 块中释放锁
4. **避免死锁**：使用 MultiLock 或按固定顺序获取多个锁
5. **监控和优化**：监控锁的性能指标，及时发现和解决问题

**推荐阅读：**
- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [Redis 官方文档](https://redis.io/docs/)
- [分布式锁的实现与优化](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)

---

**版本信息：**
- Redisson: 3.24.3
- Redis: 7.0+
- JDK: 8+

**更新日期：** 2024-02-05

---

## 🔗 相关笔记

- [[../../开发经验/使用Redis构建分布式锁]] —— 手动实现分布式锁的基础版本（SETNX + UUID）
- [[../数据结构]] —— Redis 数据结构总览（String / Hash / List 等）
- [[阻塞队列]] —— 基于 Redisson RReliableQueue 的消息队列
- [[../../面经/如何解决缓存和数据库的数据不一致性]] —— 缓存与数据库一致性方案
- [[../../开发经验/关于使用缓存]] —— Redis 缓存模式与实战经验
