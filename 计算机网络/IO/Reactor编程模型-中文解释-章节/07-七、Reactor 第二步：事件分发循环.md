# 七、Reactor 第二步：事件分发循环

PDF 中的事件循环是：

```java
public void run() {
    try {
        while (!Thread.interrupted()) {
            // 阻塞等待，直到至少有一个 IO 事件就绪
            selector.select();

            // 取出已经就绪的 SelectionKey
            Set<SelectionKey> selected = selector.selectedKeys();

            Iterator<SelectionKey> iterator = selected.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                // 处理完后立即从 selected 集合删除
                iterator.remove();

                dispatch(key);
            }
        }
    } catch (IOException ex) {
        // 生产代码需要记录日志并决定是否退出或重启
    }
}

private void dispatch(SelectionKey key) {
    if (!key.isValid()) {
        return;
    }

    Runnable handler = (Runnable) key.attachment();
    if (handler != null) {
        handler.run();
    }
}
```

## `select()` 做了什么

`selector.select()` 会让当前线程进入等待状态。操作系统发现某个注册的 Socket 可以进行对应操作时，Selector 返回，并把相关 `SelectionKey` 放到 `selectedKeys()` 中。

常见事件包括：

- `OP_ACCEPT`：监听 Socket 有新连接；
- `OP_READ`：连接有数据可读，或者读操作不会阻塞；
- `OP_WRITE`：连接可以继续写入；
- `OP_CONNECT`：非阻塞连接建立过程已经可以继续。

## 为什么不能直接遍历后只调用 `selected.clear()`

PDF 采用“遍历结束后清空”的简化写法。在实际代码中，更推荐在迭代时调用 `iterator.remove()`，因为这样可以明确表示某个事件已经消费，避免异常、提前跳出或重复处理时留下旧事件。

---

