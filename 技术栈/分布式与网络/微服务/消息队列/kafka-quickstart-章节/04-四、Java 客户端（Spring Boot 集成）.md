# 四、Java 客户端（Spring Boot 集成）

## 4.1 依赖

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

## 4.2 配置（application.yml）

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

## 4.3 Producer

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

## 4.4 Consumer

```java
@Service
public class WaterDataConsumer {

    /**
     * 单条消费，手动提交 offset
     * 注意：@KafkaListener 默认是并发的（多个线程同时消费）
     *       如需保证顺序，设置 concurrency="1"
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

## 4.5 批量消费容器工厂配置

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

