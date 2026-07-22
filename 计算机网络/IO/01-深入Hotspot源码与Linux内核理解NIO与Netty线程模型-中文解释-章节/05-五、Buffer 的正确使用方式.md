# 五、Buffer 的正确使用方式

原 PDF 的示例使用：

```java
ByteBuffer byteBuffer = ByteBuffer.allocate(128);
int len = socketChannel.read(byteBuffer);
System.out.println(new String(byteBuffer.array()));
```

教学上能帮助理解，但有两个问题：

1. `new String(byteBuffer.array())` 会把整个 128 字节数组都转成字符串，可能包含无效尾部数据；
2. 它没有处理半包，也没有展示 `flip()` / `get()` 的典型状态变化。

更规范的基本写法是：

```java
ByteBuffer buffer = ByteBuffer.allocate(128);
int read = channel.read(buffer);

if (read > 0) {
    // read 表示本次真正读到的字节数
    buffer.flip();

    byte[] data = new byte[buffer.remaining()];
    buffer.get(data);

    System.out.println(new String(data, StandardCharsets.UTF_8));
}
```

## `ByteBuffer` 的状态变化

```text
allocate()
    -> position = 0, limit = capacity

channel.read(buffer) / buffer.put(...)
    -> 写入数据，position 向后移动

buffer.flip()
    -> 把 position 变成 0，把原 position 变成 limit
    -> 进入读取刚才写入数据的模式

buffer.get(...) / channel.write(buffer)
    -> 读取数据，position 向后移动

buffer.clear()
    -> 准备重新使用整个 Buffer
```

如果一次读取没有得到完整请求，不能直接 `clear()`，否则未处理的半包会丢失。实际协议通常要保留未消费数据，并在下一次读取时继续拼接。

---

