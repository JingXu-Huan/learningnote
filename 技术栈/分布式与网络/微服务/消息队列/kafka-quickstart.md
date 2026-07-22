# Kafka 快速上手指南

> 面向已有 RocketMQ 经验的开发者，重点对比两者差异，快速建立 Kafka 认知体系。

---

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[kafka-quickstart-章节/01-一、核心概念对比|一、核心概念对比]]
- [[kafka-quickstart-章节/02-二、核心架构详解|二、核心架构详解]]
- [[kafka-quickstart-章节/03-三、环境搭建（本地单机，Docker）|三、环境搭建（本地单机，Docker）]]
- [[kafka-quickstart-章节/04-四、Java 客户端（Spring Boot 集成）|四、Java 客户端（Spring Boot 集成）]]
- [[kafka-quickstart-章节/05-五、重要机制详解|五、重要机制详解]]
- [[kafka-quickstart-章节/06-六、与 RocketMQ 选型建议|六、与 RocketMQ 选型建议]]
- [[kafka-quickstart-章节/07-七、常见坑|七、常见坑]]
- [[kafka-quickstart-章节/08-八、快速验证代码（纯 Java，无框架）|八、快速验证代码（纯 Java，无框架）]]
- [[kafka-quickstart-章节/09-九、术语表|九、术语表]]
- [[kafka-quickstart-章节/10-十、Mermaid 图表渲染说明|十、Mermaid 图表渲染说明]]

## 相关笔记

- [[技术栈/数据库/Redis/Redisson/阻塞队列]] —— 基于 Redisson RReliableQueue 的轻量级消息队列
- [[../多个微服务之间如何相互调用/OpenFeign]] —— 同步远程调用（与 Kafka 异步调用的对比）
- [[项目与成长/实习方法论/通讯协议/SSE/SSE vs WebSocket vs HTTP]] —— 通讯协议对比（HTTP 轮询 vs 消息推送 vs MQ）
- [[技术栈/Java与框架/多线程/API/CompletableFuture]] —— 异步消费 Kafka 消息时常用 CompletableFuture
