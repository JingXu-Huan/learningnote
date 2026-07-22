# 十一、多线程 Reactor：把业务处理移出 IO 线程

单线程 Reactor 最大的问题是：业务处理不能太慢。

如果 Reactor 线程正在执行：

```java
process(); // 慢数据库、复杂计算、远程调用
```

那么其他连接的读写事件都不能及时处理。PDF 因此介绍了两种扩展方向。

## 方案一：Reactor + Worker 线程池

基本结构：

```text
Reactor 线程：accept / read / write
       |
       | 请求读完整
       v
Worker 线程池：decode / compute / encode
       |
       | 业务处理完成
       v
Reactor 线程：继续 write
```

核心原则：

- Reactor 线程只负责快速的 IO 操作和任务分发；
- 计算密集型或可能阻塞的业务交给 Worker；
- Worker 完成后，通知 Reactor 重新关注 `OP_WRITE`；
- Worker 数量通常远小于客户端连接数量。

PDF 中的示意代码使用了旧版 `PooledExecutor`：

```java
private void read() throws IOException {
    socket.read(input);

    if (inputIsComplete()) {
        state = PROCESSING;

        // 不在 Reactor 线程中直接执行耗时业务
        pool.execute(new Processor());
    }
}

class Processor implements Runnable {
    @Override
    public void run() {
        processAndHandOff();
    }
}
```

今天更常见的是 `ExecutorService`：

```java
ExecutorService workers = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
);

workers.execute(() -> {
    // 这里执行解码、业务计算、编码
    process();

    // 注意：跨线程修改 SelectionKey 后，通常需要唤醒 Selector
    selector.wakeup();
});
```

## Worker 线程池的风险

如果请求处理速度小于到达速度，任务队列会不断增长。因此线程池必须配合：

- 有界队列；
- 拒绝策略或降级策略；
- 超时控制；
- 连接级背压；
- 监控队列长度和任务等待时间。

否则，线程池只是把阻塞从 IO 线程转移到了内存队列，最终仍可能发生延迟堆积和内存耗尽。

## 方案二：多个 Reactor

当 IO 事件本身也很多时，可以使用多个 Reactor：

```text
                    Main Reactor
                         |
              负责 OP_ACCEPT 接收连接
                 /       |       \
                v        v        v
          Sub Reactor  Sub Reactor  Sub Reactor
          Selector     Selector     Selector
             |             |             |
          客户端 IO     客户端 IO     客户端 IO
```

通常分为：

- Main Reactor：只负责接收连接；
- Sub Reactor：负责已建立连接的读写事件；
- Worker Pool：负责业务计算。

PDF 给出的分配方式是轮询：

```java
private int next = 0;

void distribute(SocketChannel client) {
    Reactor reactor = reactors[next];

    // 把连接交给下一个 Sub Reactor
    reactor.register(client);

    next = (next + 1) % reactors.length;
}
```

轮询简单，但不一定代表真实负载均衡。实际系统还要考虑连接活跃度、请求量、消息量和 Reactor 当前队列长度。

---

