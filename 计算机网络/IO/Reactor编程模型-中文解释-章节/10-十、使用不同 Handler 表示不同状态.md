# 十、使用不同 Handler 表示不同状态

PDF 还介绍了 State-Object 的变体：读取阶段把 `Reader` 作为附件，读取完成后把附件替换成 `Sender`。

```java
class Reader implements Runnable {
    @Override
    public void run() {
        socket.read(input);

        if (inputIsComplete()) {
            process();

            // 状态转移：Reader -> Sender
            key.attach(new Sender());
            key.interestOps(SelectionKey.OP_WRITE);
            key.selector().wakeup();
        }
    }
}

class Sender implements Runnable {
    @Override
    public void run() {
        socket.write(output);

        if (outputIsComplete()) {
            key.cancel();
        }
    }
}
```

这种写法的优点是每个状态的代码边界更清楚；缺点是状态对象之间共享数据、异常处理和资源关闭会更复杂。现代 Netty 中的 `ChannelPipeline` 和多个 Handler，也可以看成这种“事件和处理器绑定”思想的工程化实现。

---

