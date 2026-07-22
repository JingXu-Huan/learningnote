# 三、环境搭建（本地单机，Docker）

## 3.1 docker-compose.yml（KRaft 模式，无需 ZooKeeper）

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

## 3.2 基本命令

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

