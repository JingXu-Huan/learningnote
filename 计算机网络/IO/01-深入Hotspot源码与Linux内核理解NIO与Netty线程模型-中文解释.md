# Java BIO、NIO、AIO 与 Netty 线程模型中文解释

> 对应资料：`01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-预习资料.pdf`
>
> 本文重点处理原 PDF 中代码截图缩进不明显、分页断裂和注释不足的问题。

这份资料的主线是：

```text
BIO：一个连接通常占用一个阻塞线程
  -> NIO：一个线程通过 Selector 管理多个连接
  -> epoll：把大量 IO 就绪判断交给 Linux 内核
  -> AIO：IO 完成后由系统回调通知程序
  -> Netty：在 NIO 之上封装出更完整的异步网络框架
```

需要先说明：PDF 中的代码主要是教学示例，不是完整的生产级服务器。示例中省略了资源关闭、半包处理、部分写入、异常连接、线程安全和协议解析等细节。本文会把这些缺口标出来。

---

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/01-一、先区分四个概念|一、先区分四个概念]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/02-二、BIO：Blocking IO|二、BIO：Blocking IO]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/03-三、NIO：Non-blocking IO|三、NIO：Non-blocking IO]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/04-四、Selector：只处理真正就绪的连接|四、Selector：只处理真正就绪的连接]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/05-五、Buffer 的正确使用方式|五、Buffer 的正确使用方式]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/06-六、Java NIO 与 Linux epoll 的关系|六、Java NIO 与 Linux epoll 的关系]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/07-七、select、poll、epoll 的对比|七、select、poll、epoll 的对比]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/08-八、Redis 的线程模型说明|八、Redis 的线程模型说明]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/09-九、AIO：Asynchronous IO|九、AIO：Asynchronous IO]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/10-十、为什么 Netty 通常使用 NIO 而不是 AIO|十、为什么 Netty 通常使用 NIO 而不是 AIO]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/11-十一、BIO、NIO、AIO 对比|十一、BIO、NIO、AIO 对比]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/12-十二、“煮水”比喻到底在说明什么|十二、“煮水”比喻到底在说明什么]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/13-十三、原 PDF 示例代码需要注意的坑|十三、原 PDF 示例代码需要注意的坑]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/14-十四、从 HotSpot 到内核，应该怎样理解|十四、从 HotSpot 到内核，应该怎样理解]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/15-十五、与 Netty 线程模型的对应关系|十五、与 Netty 线程模型的对应关系]]
- [[01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-中文解释-章节/16-十六、最终总结|十六、最终总结]]
