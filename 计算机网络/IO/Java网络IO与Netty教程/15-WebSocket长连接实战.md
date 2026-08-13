# 十五、WebSocket 长连接实战

> 白话翻译：WebSocket 先借 HTTP 完成一次“升级协商”，成功后同一条 TCP 连接不再传普通 HTTP 请求，而是传递带边界的 WebSocket Frame。

## 15.1 从 HTTP Upgrade 到双向通信

WebSocket 建连包含两个阶段：

```text
阶段一：HTTP 请求
Client -- GET /ws + Upgrade: websocket --> Server
Client <-- 101 Switching Protocols -------- Server

阶段二：WebSocket Frame
Client <------ Text / Binary / Ping / Pong / Close ------> Server
```

`101` 只表示协议升级成功，不是普通业务响应。升级后客户端和服务端都可以主动发送 Frame，因此适合聊天室、协同编辑、实时通知和设备控制。

## 15.2 Pipeline 如何完成协议切换

```java
WebSocketServerProtocolConfig config =
        WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/ws")
                .maxFramePayloadLength(64 * 1024)
                .handshakeTimeoutMillis(10_000)
                .build();

pipeline.addLast(new HttpServerCodec());
pipeline.addLast(new HttpObjectAggregator(64 * 1024));
pipeline.addLast(new WebSocketServerProtocolHandler(config));
pipeline.addLast(new WebSocketFrameAggregator(64 * 1024));
pipeline.addLast(new WebSocketChatHandler());
```

各 Handler 的职责：

| Handler | 职责 |
| --- | --- |
| `HttpServerCodec` | 解析升级前的 HTTP 请求，编码握手响应 |
| `HttpObjectAggregator` | 聚合完整的 HTTP Upgrade 请求 |
| `WebSocketServerProtocolHandler` | 校验握手、修改 Pipeline、处理 Close/Ping/Pong 控制帧 |
| `WebSocketFrameAggregator` | 把分片数据帧聚合为完整 Text/Binary Frame |
| 业务 Handler | 只关注文本或二进制业务消息 |

升级不是重新建立连接，而是在原 Channel 的 Pipeline 中切换协议处理器。

## 15.3 教程代码：浏览器聊天室

完整代码：`示例代码/src/main/java/note/io/netty/websocket/NettyWebSocketServer.java`，端口为 `9007`。

启动服务端后，打开两个浏览器页面，在开发者工具 Console 中分别执行：

```javascript
const ws = new WebSocket("ws://localhost:9007/ws");
ws.onopen = () => console.log("连接成功");
ws.onmessage = event => console.log("收到：", event.data);
ws.onclose = event => console.log("关闭：", event.code, event.reason);
```

任意窗口发送：

```javascript
ws.send("你好，Netty WebSocket");
```

关闭时使用协议握手，而不是直接让页面丢弃连接：

```javascript
ws.close(1000, "测试结束");
```

## 15.4 为什么握手完成后再加入在线列表

TCP `channelActive` 发生时，WebSocket 握手可能还没成功。若此时加入在线列表，普通 HTTP 请求、握手失败连接也可能被当作在线用户。

```java
@Override
public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
    if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
        ONLINE.add(ctx.channel());
        broadcast("有人加入：" + ctx.channel().remoteAddress());
        return;
    }
    ctx.fireUserEventTriggered(event);
}
```

这是生命周期判断：

```text
channelActive       = TCP 已建立
HandshakeComplete   = WebSocket 协议已升级
业务登录成功         = 用户身份已认证
```

三者不能混为一谈。生产聊天室通常在业务登录或 Token 校验成功后，才建立“用户 ID -> Channel”的会话映射。

## 15.5 Text、Binary 与控制帧

- `TextWebSocketFrame`：UTF-8 文本，适合 JSON 和简单命令；
- `BinaryWebSocketFrame`：二进制数据，适合自定义协议、Protobuf 或媒体片段；
- `PingWebSocketFrame` / `PongWebSocketFrame`：协议层保活与可达性探测；
- `CloseWebSocketFrame`：携带关闭码与原因，完成有序关闭；
- `ContinuationWebSocketFrame`：一个大消息的后续分片。

Frame 有边界，所以不再使用 TCP 层的长度字段解码器来区分 WebSocket 消息；但一个业务消息仍可能被分片，`WebSocketFrameAggregator` 用于重新聚合。无论是否聚合，都必须限制单帧和完整消息大小。

## 15.6 广播与引用计数

收到的入站 `TextWebSocketFrame` 会在 `SimpleChannelInboundHandler` 返回后自动释放。广播时示例提取字符串，再创建新的出站 Frame：

```java
@Override
protected void channelRead0(
        ChannelHandlerContext ctx, TextWebSocketFrame frame) {
    String message = ctx.channel().remoteAddress()
            + "：" + frame.text();
    ONLINE.writeAndFlush(new TextWebSocketFrame(message));
}
```

不要把入站 `frame` 直接保存到队列中异步发送。若业务广播非常频繁，还要考虑慢连接：检查 `isWritable()`、限制单连接待写字节，并定义丢弃、断开或离线补偿策略。

## 15.7 鉴权应该放在哪里

浏览器原生 WebSocket API 不方便自定义任意握手 Header，常见方案包括：

1. 使用已有登录 Cookie，在 Upgrade 请求阶段校验；
2. 使用短时效、一次性的握手票据放在查询参数中；
3. 握手成功后要求第一条业务消息完成认证，认证前禁止其他消息；
4. 使用双方约定的 WebSocket 子协议承载版本协商，而不是存放长期密钥。

不要在日志中打印完整 Token，也不要把长期凭证直接放在容易被代理、历史记录和监控采集的 URL 中。鉴权失败要返回明确结果并关闭连接，同时限制重试频率。

## 15.8 心跳、重连与消息可靠性

WebSocket 解决的是双向帧传输，不自动提供以下能力：

- 断线后的指数退避重连；
- 登录恢复和订阅恢复；
- 消息 ACK、幂等与去重；
- 离线消息；
- 已送达、已读语义；
- 跨节点广播。

浏览器客户端可在 `close/error` 后退避重连；服务端可配合 `IdleStateHandler` 发现长期无有效活动的连接。需要可靠投递时，在业务协议中增加 `messageId`、ACK 和重放窗口，不能把一次 `writeAndFlush` 成功当成对方已经处理。

## 15.9 多节点部署

单机的 `ChannelGroup` 只认识当前 JVM 的连接。多实例部署需要：

```text
用户发送消息
  -> 当前 Netty 节点
  -> 消息总线 / 会话路由
  -> 目标用户所在节点
  -> 目标 Channel
```

负载均衡器还必须支持 HTTP Upgrade，并设置合理的长连接空闲超时。是否使用粘性会话取决于会话状态和跨节点路由设计，不能把粘性会话当作消息可靠性的替代品。

## 15.10 官方 API

- [WebSocketServerProtocolHandler](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/WebSocketServerProtocolHandler.html)
- [WebSocketServerProtocolConfig](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/WebSocketServerProtocolConfig.html)
- [WebSocketFrameAggregator](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/WebSocketFrameAggregator.html)
- [TextWebSocketFrame](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/TextWebSocketFrame.html)
- [ChannelGroup](https://netty.io/4.2/api/io/netty/channel/group/ChannelGroup.html)

## 15.11 知识问答

**问：WebSocket 和普通 TCP 自定义协议是什么关系？**

答：二者最终都基于传输连接，但 WebSocket 标准化了 HTTP 握手、Frame 边界、控制帧和浏览器 API；自定义 TCP 协议自由度更高，却需要自己处理边界、升级、安全与客户端兼容。

**问：为什么收到 `channelActive` 还不能发送业务消息？**

答：它只表示 TCP 已连接；WebSocket 可能尚未完成 Upgrade，用户也可能尚未认证。

**问：有 Ping/Pong 就能保证消息不丢吗？**

答：不能。它只能帮助发现连接状态。业务可靠性仍需要消息 ID、ACK、持久化和重放设计。

### 动手题

1. 给聊天室增加昵称登录，认证成功前拒绝聊天消息。
2. 限制消息为 4 KiB，并对超限、非法 UTF-8 和非文本消息设计关闭策略。
3. 给浏览器客户端实现带抖动的指数退避重连，并在重连后恢复昵称。

------

上一章：[[14-Netty实现HTTP服务]]　下一章：[[16-ServerBootstrap启动与连接建立源码链路]]　返回：[[../Java网络IO与Netty学习指南|学习指南]]
