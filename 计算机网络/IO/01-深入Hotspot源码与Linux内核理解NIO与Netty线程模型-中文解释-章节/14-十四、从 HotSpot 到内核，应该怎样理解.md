# 十四、从 HotSpot 到内核，应该怎样理解

PDF 想表达的调用链可以简化为：

```text
Java 业务代码
    |
    v
SocketChannel / Selector
    |
    v
JDK NIO 实现与 SelectorProvider
    |
    v
Native 方法与平台 IO API
    |
    v
Linux epoll / Windows Winsock 等机制
    |
    v
网卡、中断、内核就绪队列
```

这里的“异步通知”不是说 Java 线程完全不参与，而是说：

- 操作系统负责监测大量 fd 的就绪状态；
- Java 线程不必逐个扫描所有连接；
- `select()` / `epoll_wait()` 返回就绪事件；
- Java 线程继续完成用户态的数据读取和业务处理。

所以，NIO 的优势不是“Java 不做任何工作”，而是把最浪费的连接状态扫描交给了更适合做这件事的内核机制。

---

