# 八、Acceptor：接收新连接

当监听 Channel 的 `OP_ACCEPT` 事件就绪时，Reactor 调用 `Acceptor`：

```java
class Acceptor implements Runnable {
    @Override
    public void run() {
        try {
            // 非阻塞 accept：没有连接时可能返回 null
            SocketChannel client = serverSocket.accept();

            if (client != null) {
                // Handler 会把客户端 Channel 注册到 Selector
                new Handler(selector, client);
            }
        } catch (IOException ex) {
            // 处理 accept 或初始化失败
        }
    }
}
```

`Acceptor` 的职责应该尽量简单：

1. 接受连接；
2. 配置非阻塞模式；
3. 创建连接对应的 Handler；
4. 把客户端 Channel 注册到 Selector。

不要在 `Acceptor` 中执行复杂业务，否则会延迟其他连接的事件处理。

---

