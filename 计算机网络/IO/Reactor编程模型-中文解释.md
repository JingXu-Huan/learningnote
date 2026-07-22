# Reactor 编程模型中文解释

> 对应资料：`Reactor编程模型-预习资料ne.pdf`
>
> 原文标题：*Scalable IO in Java*
>
> 作者：Doug Lea，State University of New York at Oswego
>
> 原文主页：<http://gee.cs.oswego.edu>

这份 PDF 不是一篇从头到尾完整实现服务器的教程，而是一份演讲式技术材料。它主要回答三个问题：

1. 传统的“一条连接一个线程”为什么难以扩展？
2. Reactor 如何利用 IO 事件，把网络服务拆成许多非阻塞的小任务？
3. Java NIO 中的 `Buffer`、`Channel`、`Selector` 和 `SelectionKey` 如何共同实现 Reactor？

整份资料可以概括为下面这条链路：

```text
客户端连接
    |
    v
ServerSocketChannel -- OP_ACCEPT --> Acceptor
                                      |
                                      v
                              SocketChannel 注册到 Selector
                                      |
                                      v
Selector 发现 OP_READ/OP_WRITE
    |
    v
Handler 状态机：读取 -> 解码 -> 业务处理 -> 编码 -> 写回
```

---

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[Reactor编程模型-中文解释-章节/01-一、网络服务到底在做什么|一、网络服务到底在做什么]]
- [[Reactor编程模型-中文解释-章节/02-二、传统服务器模型：一个连接一个处理线程|二、传统服务器模型：一个连接一个处理线程]]
- [[Reactor编程模型-中文解释-章节/03-三、可扩展性的目标：把处理拆成小任务|三、可扩展性的目标：把处理拆成小任务]]
- [[Reactor编程模型-中文解释-章节/04-四、Reactor 模式是什么|四、Reactor 模式是什么]]
- [[Reactor编程模型-中文解释-章节/05-五、单线程 Reactor 的结构|五、单线程 Reactor 的结构]]
- [[Reactor编程模型-中文解释-章节/06-六、Reactor 第一步：创建 Selector 和监听 Channel|六、Reactor 第一步：创建 Selector 和监听 Channel]]
- [[Reactor编程模型-中文解释-章节/07-七、Reactor 第二步：事件分发循环|七、Reactor 第二步：事件分发循环]]
- [[Reactor编程模型-中文解释-章节/08-八、Acceptor：接收新连接|八、Acceptor：接收新连接]]
- [[Reactor编程模型-中文解释-章节/09-九、Handler：用状态机处理一次请求|九、Handler：用状态机处理一次请求]]
- [[Reactor编程模型-中文解释-章节/10-十、使用不同 Handler 表示不同状态|十、使用不同 Handler 表示不同状态]]
- [[Reactor编程模型-中文解释-章节/11-十一、多线程 Reactor：把业务处理移出 IO 线程|十一、多线程 Reactor：把业务处理移出 IO 线程]]
- [[Reactor编程模型-中文解释-章节/12-十二、任务之间如何协调|十二、任务之间如何协调]]
- [[Reactor编程模型-中文解释-章节/13-十三、Java NIO 的四个核心对象|十三、Java NIO 的四个核心对象]]
- [[Reactor编程模型-中文解释-章节/14-十四、文件传输和零拷贝提示|十四、文件传输和零拷贝提示]]
- [[Reactor编程模型-中文解释-章节/15-十五、长连接服务需要保存会话状态|十五、长连接服务需要保存会话状态]]
- [[Reactor编程模型-中文解释-章节/16-十六、PDF 中代码的时代背景和需要修正的地方|十六、PDF 中代码的时代背景和需要修正的地方]]
- [[Reactor编程模型-中文解释-章节/17-十七、最小可运行思维模型|十七、最小可运行思维模型]]
- [[Reactor编程模型-中文解释-章节/18-十八、如何把这份 PDF 和 Netty 联系起来|十八、如何把这份 PDF 和 Netty 联系起来]]
- [[Reactor编程模型-中文解释-章节/19-十九、学习时最应该记住的结论|十九、学习时最应该记住的结论]]
- [[Reactor编程模型-中文解释-章节/20-二十、推荐实践顺序|二十、推荐实践顺序]]
