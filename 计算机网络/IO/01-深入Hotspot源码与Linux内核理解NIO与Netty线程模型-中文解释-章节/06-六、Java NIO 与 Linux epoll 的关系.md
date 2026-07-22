# 六、Java NIO 与 Linux epoll 的关系

PDF 重点解释了三个 Java 方法如何一路落到 Linux 内核：

```java
Selector selector = Selector.open();

socketChannel.register(selector, SelectionKey.OP_READ);

selector.select();
```

可以把它们理解成三个阶段：

```text
Selector.open()
    -> 创建 Java Selector
    -> Linux 下选择合适的 SelectorProvider
    -> 底层创建 epoll 实例或其他 IO 多路复用对象

channel.register(...)
    -> 把 Socket 对应的文件描述符注册到 Selector
    -> 底层通过 epoll_ctl 关注 EPOLLIN 等事件

selector.select()
    -> Java 线程进入等待
    -> 底层调用 epoll_wait
    -> 有事件就绪后返回
```

## 6.1 “文件描述符”是什么

Linux 把 Socket、文件、管道等 IO 对象抽象成文件描述符，通常简称 fd。Java 程序不会直接操作 fd，而是通过 `SocketChannel`、`Selector` 等对象间接使用它。

Java 层看到的是：

```text
SocketChannel + Selector + SelectionKey
```

Linux 层看到的是：

```text
socket fd + epoll fd + epoll_ctl + epoll_wait
```

## 6.2 `epoll_create`

```c
int epoll_create(int size);
```

创建一个 epoll 实例并返回一个文件描述符。旧版本的 `size` 参数只是容量提示，不是最大连接数；在现代 Linux 中它基本已经没有实际意义，但接口仍然保留。

## 6.3 `epoll_ctl`

```c
int epoll_ctl(
    int epfd,
    int op,
    int fd,
    struct epoll_event *event
);
```

它用于增删改某个 fd 关心的事件：

```text
EPOLL_CTL_ADD：新增监听
EPOLL_CTL_MOD：修改监听事件
EPOLL_CTL_DEL：移除监听
```

典型场景：

- 新客户端连接建立后，添加它的 fd，并关注 `EPOLLIN`；
- 输出缓冲区有数据后，修改为同时关注 `EPOLLOUT`；
- 响应写完后，取消 `EPOLLOUT`，避免持续触发写事件；
- 连接关闭时，删除 fd。

## 6.4 `epoll_event`

```c
struct epoll_event {
    __uint32_t events;
    epoll_data_t data;
};

typedef union epoll_data {
    void *ptr;
    int fd;
    __uint32_t u32;
    __uint64_t u64;
} epoll_data_t;
```

`events` 表示要监听的事件，`data` 用于携带用户数据，例如 fd 或指向连接对象的指针。

PDF 中列出的常见事件：

- `EPOLLIN`：可以读取；
- `EPOLLOUT`：可以写入；
- `EPOLLERR`：发生错误。

## 6.5 `epoll_wait`

```c
int epoll_wait(
    int epfd,
    struct epoll_event *events,
    int maxevents,
    int timeout
);
```

它等待 epoll 实例中的就绪事件。与 `select` / `poll` 每次都扫描大量 fd 不同，epoll 会维护就绪事件集合，等待返回时重点处理已经就绪的对象。

---

