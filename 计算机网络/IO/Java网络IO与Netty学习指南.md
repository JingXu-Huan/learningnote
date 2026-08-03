# Java 网络 IO 与 Netty 渐进式学习指南

> 学习主线：网络字节流 → BIO → NIO → Reactor → AIO → Netty → 协议与生产实践。
>
> 教程入口：[[Java网络IO与Netty教程/00-学习路线与环境准备|从这里开始]]

这套笔记不是 API 罗列，而是按照“先看见问题，再学习抽象，最后完成项目”的顺序组织。每一章都有官方 API、教程代码、知识问答和动手任务。

------

## 推荐学习顺序

### 第一阶段：理解 Java 网络 IO

1. [[Java网络IO与Netty教程/01-网络IO基本功与常见模型|网络 IO 基本功与常见模型]]
2. [[Java网络IO与Netty教程/02-BIO阻塞式网络编程|BIO 阻塞式网络编程]]
3. [[Java网络IO与Netty教程/03-NIO的Buffer与Channel|NIO 的 Buffer 与 Channel]]
4. [[Java网络IO与Netty教程/04-Selector与手写Reactor|Selector 与手写 Reactor]]
5. [[Java网络IO与Netty教程/05-AIO异步完成模型|AIO 异步完成模型]]

### 第二阶段：掌握 Netty 核心

6. [[Java网络IO与Netty教程/06-Netty入门与第一个Echo服务|Netty 入门与第一个 Echo 服务]]
7. [[Java网络IO与Netty教程/07-EventLoop与线程模型|EventLoop 与线程模型]]
8. [[Java网络IO与Netty教程/08-ByteBuf与引用计数|ByteBuf 与引用计数]]
9. [[Java网络IO与Netty教程/09-Pipeline与Handler事件传播|Pipeline 与 Handler 事件传播]]
10. [[Java网络IO与Netty教程/10-粘包拆包与自定义协议|粘包拆包与自定义协议]]

### 第三阶段：走向可用的网络服务

11. [[Java网络IO与Netty教程/11-异步任务背压与线程切换|异步任务、背压与线程切换]]
12. [[Java网络IO与Netty教程/12-心跳重连安全与性能|心跳、重连、安全与性能]]
13. [[Java网络IO与Netty教程/13-测试排错实战与面试问答|测试、排错、实战与面试问答]]

------

## 配套资源

- [[Java网络IO与Netty教程/示例代码/README|教程代码运行说明]]
- [Netty 机制交互动画](Java网络IO与Netty教程/动画/netty-mechanism.html)
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释|现有 BIO/NIO/AIO 与内核资料]]
- [[Reactor编程模型-中文解释|现有 Reactor 编程模型资料]]

旧资料适合在完成第 4 章后阅读：此时再看 epoll、HotSpot 与 Doug Lea 的 Reactor 材料，会更容易把操作系统概念对应到 Java/Netty 代码。

------

## 学完后的验收标准

- 能解释同步/异步、阻塞/非阻塞、IO 多路复用为什么不是同一维度。
- 能用 Java NIO 正确处理半包、部分写和 `SelectionKey` 集合。
- 能从 `ServerBootstrap` 一路讲清 `Channel`、`EventLoop`、`Pipeline`、`Handler`。
- 能说明 `ByteBuf` 的双游标、池化、直接内存与引用计数。
- 能设计长度字段协议，并防御畸形长度、粘包、拆包和内存泄漏。
- 能避免阻塞 EventLoop，并用水位线、可写状态或读开关处理背压。
- 能用 `EmbeddedChannel` 为编解码器写单元测试，并根据日志定位常见故障。

