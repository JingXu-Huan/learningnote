# RocketMQ 学习笔记

> 基于火车票务系统实战（rocketmq-spring-boot-starter 2.2.3 + RocketMQ 5.1.0）

---

## 一、RocketMQ 是什么

Apache RocketMQ 是阿里巴巴开源的**分布式消息中间件**，核心能力：

| 能力 | 说明 |
|------|------|
| 异步解耦 | 推单 → MQ → 消费者，生产者和消费者互不感知 |
| 削峰填谷 | 高峰期消息堆积在 Broker，消费者按自己能力拉取 |
| 延迟投递 | 订单超时释放、定时重试等场景 |
| 事务消息 | 先发消息再执行本地事务，保证最终一致性 |
| 死信队列 | 消费多次失败的消息进入 DLQ，人工介入处理 |

### 四大角色

```
Producer → NameServer ← Consumer
    ↓           ↑           ↑
 Broker ←──────┘           │
    ↑                      │
    └── Consumer 拉取消息 ──┘
```

| 角色 | 职责 | 端口 |
|------|------|------|
| NameServer | 轻量注册中心，Broker 在此注册 Topic 路由 | 9876 |
| Broker | 消息存储和转发 | 10911 |
| Producer | 消息生产者 | - |
| Consumer | 消息消费者 | - |

---

## 二、核心概念

### 2.1 Topic / Tag / Consumer Group

```
Topic（主题）
  ├── Tag: order_push   → Consumer Group: ticket_group_order_push
  ├── Tag: order_expire → Consumer Group: ticket_group_order_expire
  └── Tag: ...          → Consumer Group: ...
```

| 概念 | 作用 | 类比 |
|------|------|------|
| Topic | 消息的一级分类 | 快递柜编号 |
| Tag | 同一 Topic 下的二级分类 | 快递柜格子 |
| Consumer Group | 一组消费者实例，负载均衡消费 | 一组快递员 |

**项目中的常量设计**（RocketMqConstants.java）：

```java
public class RocketMqConstants {
    public static final String PREFIX_PRJ = "ticket_";
    // 订单
    public static final String TOPIC_ORDER_PUSH = "topic_ctrip_order_push";
    public static final String TAG_ORDER_PUSH   = "ticket_tag_order_push";
    public static final String TAG_ORDER_EXPIRE = "ticket_tag_order_expire";
    public static final String GROUP_ORDER_PUSH = "ticket_group_order_push";
    // 改签
    public static final String TOPIC_CHANGE = "ticket_topic_change";
    public static final String TAG_CHANGE   = "ticket_tag_change";
    public static final String GROUP_CHANGE_CONSUMER = "ticket_group_change_consumer";
    // 退票
    public static final String TOPIC_REFUND = "ticket_topic_refund";
    public static final String TAG_REFUND   = "ticket_tag_refund";
    public static final String GROUP_REFUND_CONSUMER = "ticket_group_refund_consumer";
    // 儿童票
    public static final String TOPIC_CHILD_TICKET = "ticket_topic_child_free";
    public static final String TAG_CHILD_TICKET   = "ticket_tag_child_free";
}
```

### 2.2 消息类型

| 类型 | 说明 | 项目用法 |
|------|------|---------|
| 普通消息 | 发出去就投递 | 携程推单、改签推单 |
| 延迟消息 | 指定时间后才投递 | 订单超时释放 |
| 事务消息 | 半消息+本地事务+提交 | 改签/退票事务 |
| 死信消息 | 消费N次仍失败 | 进入 %DLQ% 死信 Topic |

---

## 三、Docker 部署

### 3.1 docker-compose.yml

```yaml
version: '3.8'
services:
  namesrv:
    image: apache/rocketmq:5.1.0
    ports: [9876:9876]
    command: sh mqnamesrv
  broker:
    image: apache/rocketmq:5.1.0
    ports: [10911:10911]
    environment:
      - NAMESRV_ADDR=rmqnamesrv:9876
    volumes:
      - ./conf/broker.conf:/home/rocketmq/rocketmq-5.1.0/conf/broker.conf
    command: sh mqbroker -c /home/rocketmq/rocketmq-5.1.0/conf/broker.conf
  dashboard:
    image: apacherocketmq/rocketmq-dashboard:5.1.0
    ports: [8008:8080]
    environment:
      - JAVA_OPTS=-Drocketmq.namesrv.addr=rmqnamesrv:9876
```

### 3.2 broker.conf

```properties
brokerClusterName = DefaultCluster
brokerName = broker-tk
brokerId = 0
deleteWhen = 04                     # 每天4点清理过期文件
fileReservedTime = 48               # 消息保留48小时
brokerRole = SYNC_FLUSH             # 同步刷盘
flushDiskType = SYNC_FLUSH          # 写磁盘后才返回成功
autoCreateTopicEnable = true        # 自动创建Topic
enableTimerWheel = true             # 开启时间轮（精确延迟消息）
brokerIP1 = 192.168.1.36            # 改成本机IP
```

启动：```docker-compose -p rocketmq -f rocketMQ-docker-compose.yml up -d```
Dashboard：```http://localhost:8008```

---

## 四、Spring Boot 集成

### 4.1 Maven 依赖

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.2.3</version>
</dependency>
```

### 4.2 application.yml

```yaml
rocketmq:
  name-server: 192.168.1.69:9876
  producer:
    group: ticket-push-group
    send-message-timeout: 30000
    retry-times-when-send-failed: 3
    retry-times-when-send-async-failed: 3
  consumer:
    listeners:
      # 开发时禁用特定消费者
      ticket_group_child_free_consumer.ticket_topic_child_free: true
      ticket_group_order_consumer.ticket_topic_order: true
```

---

## 五、消息生产者（Producer）

### 5.1 消息体接口约束

所有 MQ 消息体必须实现 IMqBaseMessage：

`java
public interface IMqBaseMessage {
    Long getId();                    // 消息ID（作为 KEYS）
    Date getMqExpireTime();          // 消息过期时间
    Long getCustomerServiceId();     // 客服ID
}
`

### 5.2 生产者封装（MqPublisher）

`java
@Component
public class MqPublisher {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    // 普通同步消息
    public <T extends IMqBaseMessage> SendResult publishMessage(T body, String topic, String tag) {
        Message<T> message = buildMessage(body, topic, tag);
        String destination = topic + ":" + tag;
        SendResult result = rocketMQTemplate.syncSend(destination, message);
        if (result.getSendStatus() != SEND_OK) throw new ServiceException("发送失败");
        return result;
    }

    // 延迟消息（秒级）
    public <T extends IMqBaseMessage> SendResult publishMessage(T body, String topic, String tag, long delaySeconds) {
        return rocketMQTemplate.syncSendDelayTimeSeconds(topic + ":" + tag, buildMessage(body, topic, tag), delaySeconds);
    }

    // 定时投递（精确到毫秒）
    public <T extends IMqBaseMessage> SendResult publishMessage(T body, String topic, String tag, Date deliverTime) {
        return rocketMQTemplate.syncSendDeliverTimeMills(topic + ":" + tag, buildMessage(body, topic, tag), deliverTime.getTime());
    }

    // 事务消息
    public <T extends IMqBaseMessage> TransactionSendResult publishTransaction(T body, String topic, String tag) {
        return rocketMQTemplate.sendMessageInTransaction(topic + ":" + tag, buildMessage(body, topic, tag), topic + ":" + tag);
    }

    private <T extends IMqBaseMessage> Message<T> buildMessage(T body, String topic, String tag) {
        return MessageBuilder.withPayload(body)
                .setHeader(RocketMQHeaders.KEYS, body.getId())
                .setHeader(RocketMQHeaders.TAGS, tag)
                .build();
    }
}
`

### 5.3 发送方式对比

| 方法 | 特点 | 适用场景 |
|------|------|---------|
| syncSend | 同步阻塞，等 Broker 确认 | 推单（必须确保到达） |
| asyncSend | 异步回调 | 日志上报 |
| sendOneWay | 发了就走 | 超高吞吐、允许丢失 |
| syncSendDelayTimeSeconds | 延迟 N 秒投递 | 订单超时释放 |
| syncSendDeliverTimeMills | 指定时间点投递 | 精确定时任务 |
| sendMessageInTransaction | 事务消息 | 本地事务+消息一致 |

---

## 六、消息消费者（Consumer）

### 6.1 消费者注解

`java
@RocketMQMessageListener(
    topic = "ticket_topic_change",
    consumerGroup = "ticket_group_change_consumer",
    selectorType = SelectorType.TAG,
    selectorExpression = "ticket_tag_change",
    maxReconsumeTimes = 3
)
`

### 6.2 消费者生命周期管理

所有 Consumer 继承 RocketMQPushConsumerLifecycle，获得暂停/恢复消费能力：

`java
public class RocketMQPushConsumerLifecycle implements RocketMQPushConsumerLifecycleListener {
    private DefaultMQPushConsumer consumer;

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        this.consumer = consumer;
    }

    public void suspendConsumption() {
        if (consumer != null) consumer.suspend();   // 暂停消费
    }

    public void resumeConsumption() {
        if (consumer != null) consumer.resume();    // 恢复消费
    }
}
`

**为什么需要暂停消费？**
- 出票开关关闭时暂停，避免无人处理时消息堆积
- 系统维护时暂停，维护完成后恢复

### 6.3 完整消费者——携程推单

`java
@Component
@RocketMQMessageListener(
    topic = RocketMqConstants.TOPIC_ORDER_PUSH,
    consumerGroup = RocketMqConstants.GROUP_ORDER_PUSH,
    selectorType = TAG,
    selectorExpression = RocketMqConstants.TAG_ORDER_PUSH,
    maxReconsumeTimes = 3
)
public class OrderPushConsumer
    extends RocketMQPushConsumerLifecycle
    implements RocketMQListener<MessageExt>, IOrderPushConsumer<PushOrderInfo> {

    @Override
    public void onMessage(MessageExt msg) {
        consume(msg, PushOrderInfo.class);  // 委托模板方法
    }

    public String getRequestKey(PushOrderInfo payload) { return payload.getRequestKey(); }
    public LockInfo getLock(String lockKey, PushOrderInfo payload) {
        return lockTemplate.lock("order_push_lock:" + lockKey, 30000L, 5000L);
    }
    public OrderDetail lockOrderAndInsert(PushOrderInfo payload, Long lockUserId) {
        return apiService.lockOrderAndInsert(payload, lockUserId);
    }
    public void enqueueForAssign(PushOrderInfo payload, OrderDetail orderDetail) {
        orderAssignQueueService.enqueue(orderDetail);
    }
    public void releaseLock(LockInfo lockInfo) { lockTemplate.releaseLock(lockInfo); }
}
`

### 6.4 推单消费模板方法（IOrderPushConsumer）

用接口默认方法实现模板方法模式：

`java
public interface IOrderPushConsumer<P> {
    // 子类实现的扩展点
    String getRequestKey(P payload);
    LockInfo getLock(String lockKey, P payload);
    OrderDetail lockOrderAndInsert(P payload, Long lockUserId) throws Exception;
    void enqueueForAssign(P payload, OrderDetail orderDetail) throws Exception;
    void releaseLock(LockInfo lockInfo);

    // 通用消费流程（模板方法）
    default void consume(MessageExt msg, Class<P> clazz) {
        P payload = JSONUtil.toBean(new String(msg.getBody(), UTF_8), clazz);
        if (payload == null) return;

        String lockKey = getRequestKey(payload);
        if (!hasText(lockKey)) lockKey = getPartnerOrderId(payload);  // 兜底
        if (!hasText(lockKey)) return;

        LockInfo lockInfo = getLock(lockKey, payload);
        if (lockInfo == null) throw new ServiceException("锁竞争，等待MQ重试");
        try {
            OrderDetail orderDetail = lockOrderAndInsert(payload, null);
            if (orderDetail != null) enqueueForAssign(payload, orderDetail);
        } catch (Exception e) {
            throw new ServiceException("消费异常，等待MQ重试", e);
        } finally {
            releaseLock(lockInfo);
        }
    }
}
`

**设计亮点**：
- 模板方法定义骨架，子类只实现扩展点
- 分布式锁防止重复消费
- 抛异常触发 MQ 自动重试
- 兜底策略：requestKey 失败时降级到 partnerOrderId

---

## 七、消息流转全景图

`
携程API推单 → MqPublisher.syncSend → Broker
                                        ├── Tag: order_push
                                        │   └── OrderPushConsumer
                                        │       ├── 分布式锁
                                        │       ├── 锁单入库
                                        │       ├── 自动拒单策略
                                        │       └── 入派单等待队列
                                        │
                                        └── Tag: order_expire
                                            └── OrderTtlConsumer
                                                └── 超时释放客服

改签推单 → MqPublisher → ticket_topic_change → TkChangeConsumer
退票推单 → MqPublisher → ticket_topic_refund → RefundPushConsumer
儿童票   → MqPublisher → ticket_topic_child_free → ChildFreeConsumer
延迟消息 → syncSendDelayTimeSeconds → 到期投递 → 对应 Consumer
`

---

## 八、重试与死信队列

### 8.1 重试机制

`
第1次失败 → 间隔10s重试
第2次失败 → 间隔30s重试
第3次失败 → 进入死信队列 %DLQ%ticket_group_xxx_consumer
`

### 8.2 项目中死信队列清单

| 原始 Group | 死信 Topic | 死信 Consumer Group |
|-----------|-----------|-------------------|
| ticket_group_order_consumer | %DLQ%ticket_group_order_consumer | ticket_group_order_dead |
| ticket_group_change_consumer | %DLQ%ticket_group_change_consumer | ticket_group_change_dead |
| ticket_group_child_free_consumer | %DLQ%ticket_group_child_free_consumer | ticket_group_child_free_dead |
| ticket_group_refund_consumer | %DLQ%ticket_group_refund_consumer | ticket_group_refund_dead |

---

## 九、幂等消费

MQ 消息可能重复投递，消费者必须做到幂等。

### 9.1 分布式锁幂等

`java
LockInfo lockInfo = lockTemplate.lock(
    "order_push_lock:" + requestKey, 30000L, 5000L, RedisTemplateLockExecutor.class);
if (lockInfo == null) throw new ServiceException("锁竞争，等待MQ重试");
try {
    // 业务逻辑
} finally {
    lockTemplate.releaseLock(lockInfo);
}
`

### 9.2 数据库幂等

`java
List<PushChangeChildTicket> existing = mapper.selectList(
    new LambdaQueryWrapper<PushChangeChildTicket>()
        .eq(PushChangeChildTicket::getChildTicketRecordId, dto.getChildTicketRecordId()));
if (CollectionUtil.isNotEmpty(existing)) {
    log.warn("重复消息，跳过");
    return;
}
mapper.insert(dto);
`

---

## 十、常见问题排查

### 10.1 消息积压排查

1. 检查消费者是否在线（Group 是否有连接）
2. 检查消费者是否报错（频繁重试导致阻塞）
3. 检查消费速度是否跟上生产速度
4. 必要时临时增加消费者实例（水平扩容）

### 10.2 消息丢失防范

| 阶段 | 丢失原因 | 解决方案 |
|------|---------|---------|
| 生产→Broker | 发送失败未重试 | retry-times-when-send-failed: 3 |
| Broker存储 | 异步刷盘丢数据 | flushDiskType = SYNC_FLUSH |
| Broker→消费 | 消费失败未重试 | maxReconsumeTimes: 3 + 死信队列 |

### 10.3 开发环境禁用消费者

`yaml
rocketmq:
  consumer:
    listeners:
      ticket_group_child_free_consumer.ticket_topic_child_free: false
      ticket_group_order_consumer.ticket_topic_order: false
`

---

## 附录：速查表

| 场景 | 推荐发送方式 |
|------|---------|
| 推单（必须到达） | syncSend |
| 订单超时释放 | syncSendDelayTimeSeconds / syncSendDeliverTimeMills |
| 改签/退票事务 | sendMessageInTransaction |
| 日志上报（允许少量丢失） | asyncSend / sendOneWay |

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| maxReconsumeTimes | 最大重试次数 | 3 |
| consumeThreadMax | 最大消费线程数 | 按业务调整 |
| consumeTimeout | 消费超时(分钟) | 30 |
