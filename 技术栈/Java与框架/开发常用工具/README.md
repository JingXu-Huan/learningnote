# Java 开发常用工具箱 🧰

这里记录后端 Java 开发中经常遇到的工具库、代码生成工具和对象处理工具。重点不是 API 罗列，而是：**什么时候用、怎么选、有哪些坑、如何在项目里落地**。

## 目录

- [[MapStruct]] —— 编译期生成对象映射代码，适合 DO、DTO、VO 之间转换
- [[Lombok]] —— 减少样板代码，但要理解注解生成代码和工程边界
- [[Jackson]] —— JSON 序列化、反序列化、泛型、时间和多态处理
- [[Hutool与Apache Commons]] —— 字符串、集合、日期、文件、HTTP 等通用工具选型
- [[Guava与Caffeine]] —— Guava 基础工具、Cache 与本地缓存设计
- [[EasyExcel与Apache POI]] —— Excel 导入导出、流式处理与大文件注意事项
- [[MinIO与Apache Tika]] —— 对象存储、文件类型识别与安全上传
- [[JWT与JJWT]] —— Token 签发、校验、过期和密钥管理
- [[Actuator与Micrometer]] —— 健康检查、指标、日志与链路观测

仓库中与本目录互补的内容：

- [[项目与成长/开发经验/Java排障工具]] —— `jstack` 线程栈排障
- [[技术栈/Java与框架/多线程/API/CompletableFuture]] —— 异步编排、线程池与超时控制
- [[技术栈/Java与框架/多线程/线程池七大核心参数]] —— `ThreadPoolExecutor` 参数和线程池设计

## 按问题选工具

| 问题 | 优先考虑 | 注意事项 |
|------|------|------|
| DO、DTO、VO 互转 | `MapStruct` | 需要编译期生成，适合结构稳定的模型 |
| 减少 getter、setter、构造器 | `Lombok` | 不要让 `@Data` 替代领域模型设计 |
| JSON 转对象 | `Jackson` | 泛型使用 `TypeReference`，时间格式要统一 |
| 字符串、集合、日期小工具 | JDK + `Hutool` / Commons Lang | 先看 JDK 是否已有清晰实现 |
| 文件、HTTP、加密等综合工具 | 按模块引入 Hutool 或专用客户端 | 不建议无脑引入所有依赖 |
| 线上线程卡顿 | `jstack`、Arthas、async-profiler | 先取证，再修改线程池或锁设计 |

## 通用原则

1. **工具库解决重复劳动，不替代业务语义**：金额、状态、权限、库存等逻辑不要藏进通用工具类。
2. **优先使用 JDK 和框架已有能力**：项目已经使用 Spring、Jackson 时，不要再引入行为重复的工具库。
3. **固定版本并关注传递依赖**：引入工具库后检查 `mvn dependency:tree`，避免版本冲突和安全问题。
4. **工具方法要有边界**：特别是 `BeanUtil`、反射、JSON 动态转换，便利性越高，类型安全通常越弱。
5. **记录输入输出约定**：null、空字符串、时区、精度、异常类型和线程安全性，都应写进工具方法说明。

## 一个实用的项目分层

```text
common/
├── constant/       # 常量和枚举，不放业务判断
├── converter/      # MapStruct、JSON、类型转换
├── utils/          # 纯工具方法，尽量无状态、无副作用
├── exception/      # 统一异常与错误码
└── config/         # ObjectMapper、线程池等基础设施配置
```

工具类一旦需要访问数据库、远程接口、当前用户或事务，就不再是纯工具类，应改成有明确职责的 Spring Service 或基础设施组件。

## 结合你的项目仓库整理出的工具地图

这些工具不是凭空罗列，而是从你账号下的项目依赖中提取出来的：

| 项目 | 已出现的工具 / 组件 | 可以沉淀的知识 |
|------|------|------|
| `Campus-Water-IQ` | Hutool、Guava、Caffeine、Redisson、Druid、RocketMQ、InfluxDB、Validation、Actuator | 本地缓存、分布式缓存、连接池、消息与时序数据 |
| `KafkaDemo` | Spring Kafka、Actuator、Lombok | 消息发送、消费、监控与测试 |
| `auth2Demo` | JWT、Micrometer Tracing、Elasticsearch、Tika、MinIO、XXL-JOB、Quartz、EasyExcel、Guava、Hutool | 认证、链路追踪、文件解析、对象存储和任务调度 |
| `shop-demo` | MyBatis-Plus、Lombok、SQL Server | 数据访问、分页和数据库适配 |
| `sky` | Fastjson、Commons Lang、Druid、PageHelper、Knife4j、JWT、OSS、POI、微信支付客户端 | 老项目维护、文件导出、支付与接口文档 |
| `JUC` | Java 21 | 并发工具、线程池和异步编排 |

建议后续学习顺序：

1. 先掌握 `MapStruct`、`Jackson`、`Lombok` 这类每天都会碰到的边界工具。
2. 再掌握 `Guava/Caffeine`、`EasyExcel/POI` 这类能明显提升工程效率的工具。
3. 最后按项目需要学习 `Tika`、`MinIO`、`XXL-JOB`、`Micrometer Tracing` 等专项工具。
