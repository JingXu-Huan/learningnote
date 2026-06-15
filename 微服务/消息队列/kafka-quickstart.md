# Kafka 快速上手指南

> 面向已有 RocketMQ 经验的开发者，重点对比两者差异，快速建立 Kafka 认知体系。

---

## 一、核心概念对比

| 概念 | RocketMQ | Kafka | 一句话解释 |
|------|----------|-------|-----------|
| 消息服务器 | Broker | Broker | 相同，都是存储和转发消息的服务 |
| 主题 | Topic | Topic | 相同，消息的逻辑分类容器 |
| 队列/分区 | Queue（物理存储） | Partition（物理存储） | **核心差异**：RocketMQ 的 Queue 是物理队列，Kafka 的 Partition 是日志分片 |
| 消费者组 | Consumer Group | Consumer Group | 订阅同一 Topic 的一组消费者，组内负载均衡 |
| 注册中心 | NameServer | ZooKeeper / KRaft | **Kafka 0.9 前用 ZooKeeper**，新版用 KRaft（内置 Raft 协议，无需外部 ZooKeeper） |
| 消息偏移 | Offset（逻辑概念，Broker 管理） | Offset（物理位置，存于 Partition） | **核心差异**：RocketMQ 的 offset 是逻辑序号，Kafka 的 offset 是物理位置（类似数组下标） |
| 顺序消息 | MessageQueue 级别有序 | Partition 级别有序 | Kafka 只保证 Partition 内有序，比 RocketMQ 的 Queue 级别更细粒度 |
| 延迟消息 | 原生支持 | **不支持**，需自行实现 | Kafka 官方明确不支持延迟消息，需借助外部方案 |
| 事务消息 | 原生支持 | 支持（幂等 + 事务 Producer） | Kafka 的事务更底层，主要保证"精确一次"生产 |

**关键差异总结：**
- **并发模型**：Kafka 的并发单位是 Partition（分区），一个 Partition 同时只被一个 Consumer 消费；RocketMQ 的并发单位是 Queue（队列）
- **消费位点管理**：RocketMQ 由 Broker 管理（Consumer 无感），Kafka 由 Consumer 自己管理（存储在 `__consumer_offsets` Topic 中）
- **适用场景**：Kafka 擅长高吞吐日志/流处理，RocketMQ 擅长事务/延迟消息等业务场景

---

## 二、核心架构详解

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Kafka 集群（Cluster）                            │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐ │
│  │      Broker 1       │  │      Broker 2       │  │      Broker 3       │ │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │  │  ┌───────────────┐  │ │
│  │  │ Partition 0   │  │  │  │ Partition 1   │  │  │  │ Partition 2   │  │ │
│  │  │ (Leader)     │  │  │  │ (Leader)     │  │  │  │ (Leader)     │  │ │
│  │  │ [日志文件]    │  │  │  │ [日志文件]    │  │  │  │ [日志文件]    │  │ │
│  │  └───────────────┘  │  │  └───────────────┘  │  │  └───────────────┘  │ │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │  │  ┌───────────────┐  │ │
│  │  │ Partition 2   │  │  │  │ Partition 0   │  │  │  │ Partition 1   │  │ │
│  │  │ (Follower)   │  │  │  │ (Follower)   │  │  │  │ (Follower)   │  │ │
│  │  │ [日志文件]    │  │  │  │ [日志文件]    │  │  │  │ [日志文件]    │  │ │
│  │  └───────────────┘  │  │  └───────────────┘  │  │  └───────────────┘  │ │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────────┘ │
│                                    │                                        │
│                         ZooKeeper / KRaft                                  │
│                    （协调元数据、Leader 选举）                              │
└─────────────────────────────────────────────────────────────────────────────┘
         │                                        │
         ▼                                        ▼
┌─────────────────┐                     ┌─────────────────┐
│    Producer     │                     │ Consumer Group  │
│  （生产者）      │                     │  （消费者组）     │
│                 │                     │  ┌───────────┐  │
│ 按 key 路由到    │                     │  │Consumer 1 │  │
│ 特定 Partition  │                     │  └───────────┘  │
│                 │                     │  ┌───────────┐  │
│                 │                     │  │Consumer 2 │  │
│                 │                     │  └───────────┘  │
└─────────────────┘                     │  ┌───────────┐  │
                                        │  │Consumer 3 │  │
                                        │  └───────────┘  │
                                        └─────────────────┘
```

### 2.2 Partition（分区）—— Kafka 并发的核心

**为什么 Partition 是并发单位？**

```
Topic: campus-water（3 个 Partition）

┌─────────────────────────────────────────────────────────────┐
│ Partition 0                    Partition 1              Partition 2 │
│ ┌─────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐  │
│ │ offset: 0               │  │ offset: 0               │  │ offset: 0               │  │
│ │ key=device-001          │  │ key=device-002          │  │ key=device-003          │  │
│ │ value={"flow":1.5}      │  │ value={"flow":2.0}      │  │ value={"flow":1.8}      │  │
│ ├─────────────────────────┤  ├─────────────────────────┤  ├─────────────────────────┤  │
│ │ offset: 1               │  │ offset: 1               │  │ offset: 1               │  │
│ │ key=device-001          │  │ key=device-002          │  │ key=device-003          │  │
│ │ value={"flow":1.6}      │  │ value={"flow":2.1}      │  │ value={"flow":1.9}      │  │
│ └─────────────────────────┘  └─────────────────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

Consumer Group: water-group（3 个 Consumer）

分配关系：
  Partition 0 ──► Consumer 1（处理 device-001 的数据）
  Partition 1 ──► Consumer 2（处理 device-002 的数据）
  Partition 2 ──► Consumer 3（处理 device-003 的数据）
```

**关键规则：**
1. **一个 Partition 同时只被一个 Consumer 消费**（负载均衡的基础）
2. **消费者数量 ≤ Partition 数量**，否则多余的消费者会空闲
3. **相同 key 的消息永远路由到同一 Partition**（保证同 key 消息的顺序）

### 2.3 日志存储结构（Partition 内部）

```
Partition 0 的物理存储结构：

┌─────────────────────────────────────────────────────────────────────────┐
│                           Partition 0                                   │
│  ┌───────────┬───────────┬───────────┬───────────┬───────────┐         │
│  │ Segment 0 │ Segment 1 │ Segment 2 │ Segment 3 │ Segment N │  ...    │
│  │ 000000000 │ 000000001 │ 000000002 │ 000000003 │           │         │
│  │ .log 文件 │  .log 文件 │  .log 文件│  .log 文件│           │         │
│  │ 000000000 │ 000000001 │ 000000002 │ 000000003 │           │         │
│  │ .index 文件│  .index 文件│  .index 文件│  .index 文件│           │         │
│  └───────────┴───────────┴───────────┴───────────┴───────────┘         │
└─────────────────────────────────────────────────────────────────────────┘

每个 Segment 包含：
- .log 文件：存储实际消息（append-only 日志）
- .index 文件：存储消息索引（稀疏索引，按偏移量定位）
- .timeindex 文件：按时间戳索引

Offset 说明：
- GlobalOffset：绝对偏移量，Topic 全局唯一（Partition 0 的 offset 从 0 开始）
- SegmentOffset：段内偏移量，每个 Segment 重新从 0 开始
```

### 2.4 Consumer Group 消费模型

```
场景 1：消费者数 = Partition 数（理想情况）
┌─────────────────────────────────┐
│ Topic: order (3 Partition)      │
│  P0 ──► C0                      │
│  P1 ──► C1                      │
│  P2 ──► C2                      │
└─────────────────────────────────┘

场景 2：消费者数 < Partition 数
┌─────────────────────────────────┐
│ Topic: order (4 Partition)      │
│  P0 ──► C0（处理 P0、P2）        │
│  P1 ──► C1（处理 P1、P3）        │
│  P2 ──┤                        │
│  P3 ──┤                        │
└─────────────────────────────────┘

场景 3：消费者数 > Partition 数（多余消费者空闲）
┌─────────────────────────────────┐
│ Topic: order (2 Partition)     │
│  P0 ──► C0                      │
│  P1 ──► C1                      │
│  P2 ──✗（空闲）                 │
│  P3 ──✗（空闲）                 │
└─────────────────────────────────┘
```

---

## 三、环境搭建（本地单机，Docker）

### 3.1 docker-compose.yml（KRaft 模式，无需 ZooKeeper）

```yaml
version: '3'
services:
  kafka:
    image: bitnami/kafka:3.7          # Bitnami 维护的 Kafka 镜像，自带 ZK
    ports:
      - "9092:9092"                   # Kafka 客户端连接端口
    environment:
      # ========== KRaft 模式核心配置 ==========
      
      # 节点 ID（KRaft 模式必填）
      - KAFKA_CFG_NODE_ID=1
      
      # 当前节点的角色（broker=数据节点，controller=控制节点）
      # 单节点同时担任两个角色
      - KAFKA_CFG_PROCESS_ROLES=broker,controller
      
      # 监听地址（客户端连接用）
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
      
      # 对外暴露的地址（客户端连接时使用，localhost 供本地访问）
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092
      
      # 监听器安全协议（PLAINTEXT=无加密）
      - KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      
      # Controller 投票节点列表（格式：节点ID@地址:端口）
      # 单节点模式只需配置自己
      - KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@kafka:9093
      
      # Controller 监听器名称（需与 LISTENERS 中的 CONTROLLER 配置一致）
      - KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
```

**KRaft vs ZooKeeper 模式对比：**

| 对比项 | ZooKeeper 模式 | KRaft 模式 |
|--------|---------------|-----------|
| 依赖 | 需要独立部署 ZooKeeper | 无外部依赖，自带 Raft 协议 |
| 复杂度 | 高（两个系统需要同时管理） | 低（只需管理 Kafka） |
| 适用版本 | Kafka 0.9 之前 | Kafka 2.8+（推荐） |
| 官方态度 | 已废弃，逐步移除 | 官方推荐模式 |

### 3.2 基本命令

```bash
# 进入容器
docker exec -it <container_id> bash

# 创建 Topic（3 个 Partition，副本因子 1）
# --partitions: 分区数，决定并发消费的上限
# --replication-factor: 副本数，用于容灾（1=无副本，仅测试用）
kafka-topics.sh --create \
  --topic campus-water \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092

# 查看 Topic 列表
kafka-topics.sh --list --bootstrap-server localhost:9092

# 查看 Topic 详情
# 输出解释：Leader=主副本所在 Broker，Replicas=所有副本，Isr=同步中的副本
kafka-topics.sh --describe --topic campus-water --bootstrap-server localhost:9092

# 命令行发送消息（输入内容后按回车发送，Ctrl+C 退出）
kafka-console-producer.sh --topic campus-water --bootstrap-server localhost:9092

# 命令行消费消息（从头开始消费）
kafka-console-consumer.sh --topic campus-water \
  --from-beginning \
  --bootstrap-server localhost:9092
```

---

## 四、Java 客户端（Spring Boot 集成）

### 4.1 依赖

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 4.2 配置（application.yml）

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      # acks: 消息确认级别
      # acks=0: Producer 不等待确认，最快但可能丢消息
      # acks=1: Leader 副本确认即可
      # acks=all: 所有 ISR 副本确认，最安全但最慢
      acks: all
      retries: 3
      properties:
        # 幂等性：保证相同消息不会重复发送
        enable.idempotence: true
    
    consumer:
      group-id: campus-water-group    # 消费者组 ID，相同组的消费者共享消费进度
      # earliest: 从最早消息开始消费（新消费者组首次启动时）
      # latest: 只消费新消息（新消费者组首次启动时）
      auto-offset-reset: earliest      # 类比 RocketMQ 的 CONSUME_FROM_FIRST_OFFSET
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      # 关闭自动提交 offset，由 Consumer 手动控制（推荐）
      enable-auto-commit: false
    listener:
      # manual_immediate: 每条消息处理后立即手动提交 offset
      # 其他模式：batch（批量处理后提交）、time（定时提交）
      ack-mode: manual_immediate
```

### 4.3 Producer

```java
@Service
public class WaterDataProducer {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 同步发送：等待消息确认后返回
     * key 决定路由到哪个 Partition（相同 key → 同一 Partition → 保证顺序）
     */
    public void sendWaterData(String deviceId, String data) {
        try {
            // send() 返回 ListenableFuture，类似 CompletableFuture
            SendResult<String, String> result =
                kafkaTemplate.send("campus-water", deviceId, data).get();
            
            System.out.println("发送成功，Partition: " + result.getRecordMetadata().partition()
                + ", Offset: " + result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.error("消息发送失败", e);
        }
    }

    /**
     * 异步发送：通过回调处理结果，不阻塞主线程
     */
    public void sendAsync(String deviceId, String data) {
        kafkaTemplate.send("campus-water", deviceId, data)
            .addCallback(
                result -> log.info("发送成功 offset={}", result.getRecordMetadata().offset()),
                ex -> log.error("发送失败", ex)
            );
    }
}
```

### 4.4 Consumer

```java
@Service
public class WaterDataConsumer {

    /**
     * 单条消费，手动提交 offset
     * ⚠️ 注意：@KafkaListener 默认是并发的（多个线程同时消费）
     *        如需保证顺序，设置 concurrency="1"
     */
    @KafkaListener(topics = "campus-water", groupId = "campus-water-group")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String deviceId = record.key();
            String data = record.value();
            
            log.info("收到消息 partition={} offset={} key={} value={}",
                record.partition(), record.offset(), deviceId, data);

            // 业务处理
            processWaterData(deviceId, data);

            // 手动提交 offset（类似 RocketMQ 的 ACK）
            ack.acknowledge();
        } catch (Exception e) {
            log.error("消费失败", e);
            // 不 ack，下次重新消费（注意幂等性！）
        }
    }

    /**
     * 批量消费：高吞吐场景下，一次性拉取多条消息处理
     * 需要配合 KafkaConfig 中配置的批量容器工厂
     */
    @KafkaListener(
        topics = "campus-water-batch",
        groupId = "campus-water-batch-group",
        containerFactory = "batchKafkaListenerContainerFactory"  // 需额外配置，见 4.5 节
    )
    public void batchConsume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("批量消费 {} 条", records.size());
        
        // 批量写入 InfluxDB 等时序数据库
        batchInsertToInfluxDB(records);
        
        // 批量处理完成后统一提交 offset
        ack.acknowledge();
    }
}
```

### 4.5 批量消费容器工厂配置

```java
@Configuration
public class KafkaConfig {

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);   // 开启批量消费模式
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
```

---

## 五、重要机制详解

### 5.1 Offset 管理（⚠️ 与 RocketMQ 最大的差异）

**两种消息系统的消费位点管理对比：**

```
RocketMQ 模式：
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Broker    │ ◄── │  Consumer    │ ──► │  业务处理    │
│             │     │              │     │              │
│  记录当前    │     │  发送 ack    │     │              │
│  消费进度    │     │  请求下一条  │     │              │
└──────────────┘     └──────────────┘     └──────────────┘
     ▲
     │ 主动推送
     │
消费者是被动的，Broker 记录所有消费进度

Kafka 模式：
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  __consumer  │ ◄── │  Consumer    │ ──► │  业务处理    │
│  _offsets    │     │              │     │              │
│             │     │  自己提交     │     │              │
│  存储各组的   │     │  offset      │     │              │
│  消费进度    │     │              │     │              │
└──────────────┘     └──────────────┘     └──────────────┘
                      ▲
                      │ 主动拉取（poll）
                      │
消费者主动拉取消息，自己管理 offset 进度
```

**三种消息传递语义：**

| 语义 | 定义 | 实现方式 | 风险 |
|------|------|---------|------|
| **至少一次（At Least Once）** | 消息绝不会丢失，但可能重复消费 | `enable.auto.commit=false` + 手动 `ack.acknowledge()` | 重复消费（需业务方做幂等） |
| **至多一次（At Most Once）** | 可能丢失消息，但绝不会重复消费 | `enable.auto.commit=true` + 自动提交 + 失败不重试 | 消息丢失 |
| **精确一次（Exactly Once）** | 消息恰好被处理一次 | 幂等 Producer + 事务 Producer + 手动提交 | 实现复杂，Kafka Streams 专用 |

```java
// 精确一次语义配置（事务 Producer）
@Transactional
public void sendWithTransaction(String key, String value) {
    kafkaTemplate.executeInTransaction(t -> {
        t.send("topic1", key, value);
        t.send("topic2", key, value);
        return null;
    });
}
```

### 5.2 消息重试 & 死信队列

**Kafka 原生不支持延迟重试**，Spring Kafka 提供了两种补偿方案：

```java
@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * 方案一：简单重试（不区分异常类型）
     * FixedBackOff: 重试 3 次，每次间隔 1 秒
     */
    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(backOff);
    }

    /**
     * 方案二：重试失败后发送到死信 Topic（推荐）
     * 死信 Topic 命名规则：原 Topic 名称 + .DLT
     * 例如：campus-water → campus-water.DLT
     */
    @Bean
    public DefaultErrorHandler errorHandlerWithDlt(KafkaOperations<String, String> operations) {
        // recoverer: 重试失败后的处理器，将消息发送到死信 Topic
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(operations);
        
        // 重试 3 次，每次间隔 1 秒，仍失败则发到死信 Topic
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }

    /**
     * 方案三：指数退避重试（更智能的重试策略）
     * 初始间隔 1 秒，最大间隔 30 秒，重试 5 次
     */
    @Bean
    public DefaultErrorHandler exponentialErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxElapsedTime(60000L);  // 总最大重试时间
        return new DefaultErrorHandler(backOff);
    }
}
```

### 5.3 顺序消息

**实现思路：相同 key 的消息 → 同一 Partition → 单线程消费**

```java
// Producer: 发送时指定 key，相同 key 会路由到同一 Partition
kafkaTemplate.send("order-topic", orderId, orderData);

// Consumer 方案一：单线程消费单个 Partition（最简单）
@KafkaListener(
    topicPartitions = @TopicPartition(
        topic = "order-topic",
        partitions = {"0"}  // 只消费 Partition 0
    ),
    concurrency = "1"      // 单线程
)
public void consumeOrderPartition0(ConsumerRecord<String, String> record, Acknowledgment ack) {
    // 处理订单（同一订单的所有消息都在 Partition 0，且单线程消费，保证顺序）
    processOrder(record.value());
    ack.acknowledge();
}

// Consumer 方案二：按 key 分区消费（更灵活）
@KafkaListener(
    topics = "order-topic",
    groupId = "order-consumer-group"
)
public void consumeOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
    String orderId = record.key();
    
    // 同一 orderId 的消息一定在同一个 Partition 内按顺序到达
    // 只需要确保单线程消费该 Partition 即可保证顺序
    synchronized (orderId.intern()) {
        processOrder(record.value());
    }
    ack.acknowledge();
}
```

### 5.4 消息积压排查

```bash
# 查看消费者组的消费进度和积压量（Lag = 积压消息数）
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group campus-water-group

# 输出示例：
# GROUP               TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG     OWNER
# campus-water-group  campus-water   0          100             150             50      consumer-1
# campus-water-group  campus-water   1          80              80             0      consumer-2
# campus-water-group  campus-water   2          120            120             0      consumer-3

# 字段解释：
# CURRENT-OFFSET: 当前已消费到的位置
# LOG-END-OFFSET: 生产者最新写入的位置
# LAG: 积压量（LAG > 0 说明消费速度 < 生产速度）
```

**积压原因及解决方案：**

| LAG 大的原因 | 排查方向 | 解决方案 |
|-------------|---------|---------|
| 消费者处理慢 | 业务逻辑耗时、数据库慢查询 | 优化业务逻辑、异步处理、增加 Consumer |
| 消费者故障 | 网络抖动、OOM、异常未捕获 | 检查日志、增加重试机制 |
| Partition 数不够 | 消费者数 > Partition 数 | 增加 Partition 数量（已有消息不变） |
| 消费者频繁 Rebalance | `max.poll.interval.ms` 过小 | 调大该值、减少 `max.poll.records` |

---

## 六、与 RocketMQ 选型建议

| 场景 | 推荐 | 原因 |
|------|------|------|
| 大数据流处理（Flink/Spark 集成） | **Kafka** | 生态最完善，Connector 丰富 |
| 日志采集、埋点数据 | **Kafka** | 高吞吐，适合海量日志 |
| 电商订单、延迟消息、事务消息 | **RocketMQ** | 原生支持延迟/事务，开发成本低 |
| 微服务解耦（国内技术栈） | **RocketMQ** | 文档中文友好，社区活跃 |
| IoT 高频数据采集 | **Kafka** | 极高吞吐，水平扩展能力强 |

---

## 七、常见坑

### 坑 1：Partition 数不够导致并发受限

```bash
# 查看 Topic 的 Partition 数
kafka-topics.sh --describe --topic campus-water --bootstrap-server localhost:9092

# 如果消费者数 > Partition 数，多余消费者空闲
# 解决方案：创建 Topic 时预估足够的 Partition 数
kafka-topics.sh --alter --topic campus-water --partitions 10 --bootstrap-server localhost:9092
```

### 坑 2：auto.offset.reset 配置错误

| 配置值 | 行为 | 使用场景 |
|--------|------|---------|
| `earliest` | 从最早消息开始消费 | 新消费者组首次启动、需要处理历史数据 |
| `latest` | 只消费新消息 | 生产环境、只关心新消息 |

```yaml
# 典型错误：生产环境用了 earliest，每次重启都重复消费历史消息
spring:
  kafka:
    consumer:
      auto-offset-reset: latest  # 生产环境应该用这个
```

### 坑 3：消息体过大

```yaml
spring:
  kafka:
    producer:
      # 单条消息最大字节数（默认 1MB）
      max-request-size: 10485760   # 10MB
    consumer:
      # 单次 poll 最大字节数（默认 1MB）
      max-poll-records: 10485760   # 10MB
```

### 坑 4：Rebalance 风暴

**原因**：Consumer 处理消息时间过长，超过 `max.poll.interval.ms`，触发 Rebalance

```yaml
spring:
  kafka:
    consumer:
      # 两次 poll 之间的最大间隔（默认 5 分钟）
      # 处理逻辑耗时 > 此值会触发 Rebalance
      max-poll-interval-ms: 300000
      
      # 每次 poll 的最大消息数（默认 500）
      # 减少此值可以缩短处理时间
      max-poll-records: 100
```

### 坑 5：在 @KafkaListener 中做耗时操作

```java
// ❌ 错误：阻塞主线程，导致 Rebalance
@KafkaListener(topics = "campus-water")
public void consume(ConsumerRecord<String, String> record) {
    // 同步调用外部服务（耗时 5 秒）
    callExternalService(record.value());  // 阻塞 5 秒
    ack.acknowledge();
}

// ✅ 正确：异步处理，主线程快速返回
@KafkaListener(topics = "campus-water")
public void consume(ConsumerRecord<String, String> record) {
    // 异步提交到线程池处理
    CompletableFuture.runAsync(() -> {
        callExternalService(record.value());
    });
    ack.acknowledge();  // 立即 ack
}
```

---

## 八、快速验证代码（纯 Java，无框架）

### 8.1 Producer

```java
public class ProducerDemo {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        // 开启幂等性（精确一次语义的前提）
        props.put("enable.idempotence", true);
        props.put("acks", "all");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        // 发送消息（key 相同则路由到同一 Partition）
        ProducerRecord<String, String> record = 
            new ProducerRecord<>("campus-water", "device-001", "{\"flow\":1.5}");
        
        producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                System.out.printf("发送成功: topic=%s, partition=%d, offset=%d%n",
                    metadata.topic(), metadata.partition(), metadata.offset());
            } else {
                exception.printStackTrace();
            }
        });
        
        producer.close();  // 关闭前确保所有消息发送完成
    }
}
```

### 8.2 Consumer

```java
public class ConsumerDemo {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "test-group");           // 消费者组 ID
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");    // 新消费者组从头消费
        props.put("enable.auto.commit", false);        // 手动提交 offset

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        
        // 订阅 Topic（支持正则匹配多个 Topic）
        consumer.subscribe(Collections.singletonList("campus-water"));

        while (true) {
            // poll: 拉取消息，参数为超时时间
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("partition=%d, offset=%d, key=%s, value=%s%n",
                    record.partition(), record.offset(), record.key(), record.value());
            }
            
            // 手动提交 offset（确保消息处理完成后提交）
            consumer.commitSync();
        }
    }
}
```

---

## 九、术语表

| 术语 | 解释 |
|------|------|
| **Broker** | Kafka 集群中的单个节点，负责存储消息 |
| **Topic** | 消息的逻辑分类容器，类似 RocketMQ 的 Topic |
| **Partition** | Topic 的物理分区，每个 Partition 是一个有序的日志文件 |
| **Segment** | Partition 内部的分段文件，包含 .log（数据）和 .index（索引） |
| **Offset** | 消息在 Partition 中的物理位置（从 0 开始的递增序号） |
| **Consumer Group** | 消费者组，同组消费者共享订阅的 Topic，组内负载均衡 |
| **Leader/Follower** | Leader 负责读写请求，Follower 同步数据（ISR 列表） |
| **ISR** | In-Sync Replicas，同步中的副本集合 |
| **Lag** | 消费滞后量 = LOG-END-OFFSET - CURRENT-OFFSET |
| **Rebalance** | 消费者组内分区所有权重新分配的过程（触发时消费者会短暂不可用） |
| **KRaft** | Kafka 内置的 Raft 协议实现，替代 ZooKeeper 进行元数据管理 |
| **__consumer_offsets** | Kafka 内置 Topic，存储各消费者组的消费进度 |

---

> **下一步推荐**：学习 Kafka Streams 或与 Flink 集成，实现实时流计算。