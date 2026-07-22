# 十六、PDF 中代码的时代背景和需要修正的地方

这份材料非常经典，但代码明显带有早期 Java NIO 的时代特征。阅读时应注意：

## 1. 缺少泛型

原文使用了类似：

```java
Set selected = selector.selectedKeys();
Iterator it = selected.iterator();
```

现代 Java 应写成：

```java
Set<SelectionKey> selected = selector.selectedKeys();
Iterator<SelectionKey> iterator = selected.iterator();
```

## 2. `PooledExecutor` 已是旧式示例

PDF 中的 `util.concurrent` / `PooledExecutor` 是早期并发库的写法。现在通常使用：

```java
ExecutorService pool = Executors.newFixedThreadPool(8);
```

生产代码还应使用有界队列、拒绝策略和关闭流程。

## 3. 代码中的若干写法是演讲简化或排版问题

例如原文中的 `dispatch((SelectionKey)(it.next());` 少了括号，正确意图应是：

```java
dispatch((SelectionKey) iterator.next());
```

原文状态对象示例中出现的 `sk.interest(...)`，按 Java NIO API 的实际方法应理解为：

```java
sk.interestOps(SelectionKey.OP_WRITE);
```

这些代码应当用来理解结构，不宜直接复制运行。

## 4. 示例省略了最重要的异常和边界处理

完整实现还需要处理：

- 客户端异常断开；
- `read()` 返回 `-1`；
- `read()` / `write()` 返回 0；
- 半包和粘包；
- 输出缓冲区写不完；
- `CancelledKeyException`；
- Selector 线程和业务线程之间的并发注册；
- 空闲连接超时；
- 大消息限制和内存上限。

---

