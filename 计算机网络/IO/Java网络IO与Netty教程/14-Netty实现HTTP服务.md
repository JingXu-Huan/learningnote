# 十四、用 Netty 实现 HTTP 服务

> 白话翻译：TCP 只送来连续字节，HTTP 编解码器把字节还原成请求行、请求头和请求体；业务 Handler 最终处理的是 HTTP 对象，而不是自己拆字符串。

## 14.1 HTTP 在 Pipeline 中的位置

一个最小 HTTP/1.1 服务端可以使用下面的 Pipeline：

```text
Socket 字节
  -> HttpServerCodec
       入站：ByteBuf -> HttpRequest / HttpContent
       出站：HttpResponse / HttpContent -> ByteBuf
  -> HttpObjectAggregator
       多个 HTTP 对象 -> FullHttpRequest
  -> HttpContentCompressor
       按客户端 Accept-Encoding 压缩响应
  -> HttpRequestHandler
```

`HttpServerCodec` 同时包含请求解码器与响应编码器。HTTP 请求体可以分成多个 `HttpContent` 到达；`HttpObjectAggregator` 把它们聚合为一个 `FullHttpRequest`，业务代码因此更简单，但代价是需要在内存中保存完整请求体。

聚合器的最大长度不是随手填写的数字，它同时是资源边界：超出限制的请求应尽早拒绝。文件上传、大请求体和流式响应通常不应聚合，而应逐段处理 `HttpContent`。

## 14.2 教程代码：健康检查与 Echo API

完整代码：`示例代码/src/main/java/note/io/netty/http/NettyHttpServer.java`。

```java
ChannelPipeline pipeline = channel.pipeline();
pipeline.addLast(new HttpServerCodec());
pipeline.addLast(new HttpObjectAggregator(64 * 1024));
pipeline.addLast(new HttpContentCompressor());
pipeline.addLast(new HttpRequestHandler());
```

示例提供两个接口：

| 请求 | 响应 |
| --- | --- |
| `GET /health` | `{"status":"UP"}` |
| `POST /echo` | 原样返回 UTF-8 请求体 |

启动 `NettyHttpServer` 后，可在 PowerShell 中验证：

```powershell
Invoke-RestMethod -Uri 'http://localhost:9006/health'
Invoke-WebRequest -Method Post -Uri 'http://localhost:9006/echo' `
    -ContentType 'text/plain; charset=utf-8' -Body '你好，Netty'
```

## 14.3 一次请求的对象变化

假设客户端分三次发完一个 POST 请求：

```text
第 1 次读取：HttpRequest + 部分 HttpContent
第 2 次读取：HttpContent
第 3 次读取：LastHttpContent
                 |
                 v
        HttpObjectAggregator
                 |
                 v
          FullHttpRequest
```

`FullHttpRequest` 同时拥有请求行、Headers 和完整 Content。它也是引用计数对象。示例使用 `SimpleChannelInboundHandler<FullHttpRequest>`，`channelRead0` 返回后请求会被自动释放；不要把 `request.content()` 保存到异步线程后继续使用。确实需要跨回调保存时，应复制业务数据，或明确 `retain/release` 所有权。

## 14.4 路由、状态码与方法校验

不要只根据 URI 字符串做 `startsWith` 判断。查询参数、路径编码和尾斜杠会让简单字符串判断出现歧义。示例先使用 `QueryStringDecoder` 得到路径，再同时检查 `HttpMethod`：

```java
QueryStringDecoder uri = new QueryStringDecoder(request.uri());

if (request.method().equals(HttpMethod.GET)
        && uri.path().equals("/health")) {
    // 200 OK
} else if (request.method().equals(HttpMethod.POST)
        && uri.path().equals("/echo")) {
    // 200 OK
} else {
    // 404 Not Found 或 405 Method Not Allowed
}
```

业务服务至少要区分：

- 路径不存在：`404 Not Found`；
- 路径存在但方法不支持：`405 Method Not Allowed`，并返回 `Allow`；
- 请求格式错误：`400 Bad Request`；
- 请求体过大：`413 Content Too Large`；
- 业务内部失败：`500 Internal Server Error`，但不要把堆栈返回给客户端。

## 14.5 Keep-Alive 为什么要显式处理

HTTP/1.1 默认倾向复用 TCP 连接。处理响应时要保持三个信息一致：

1. 响应正文的 `Content-Length` 或分块编码正确；
2. 根据请求判断是否保持连接；
3. 不保持连接时，在响应写完后关闭 Channel，而不是先关闭再写。

```java
boolean keepAlive = HttpUtil.isKeepAlive(request);
HttpUtil.setContentLength(response, response.content().readableBytes());

if (keepAlive) {
    HttpUtil.setKeepAlive(response, true);
    ctx.writeAndFlush(response);
} else {
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
}
```

`writeAndFlush` 完成只表示本地出站操作结束，不代表客户端业务已经消费响应。

## 14.6 聚合、压缩和 TLS 的顺序

常见 HTTPS Pipeline：

```text
SslHandler
  -> HttpServerCodec
  -> HttpObjectAggregator（小请求才使用）
  -> HttpContentCompressor
  -> 认证 / 限流 / 路由 / 业务 Handler
```

`SslHandler` 必须先把 TLS 记录解密为明文字节，HTTP 解码器才能识别请求。认证、限流和业务校验应有清晰的先后关系；如果先聚合超大正文再认证，攻击者可能在认证前消耗大量内存。

## 14.7 生产边界

- 为初始行、Header、Chunk 和聚合正文分别设置上限；Header 校验保持开启。
- 配置读空闲、请求处理超时和写超时，不能只依赖 TCP keepalive。
- 不要在 EventLoop 中执行数据库、远程调用或大 JSON 序列化。
- 限制单连接流水线中的在途请求；HTTP/1.1 Pipeline 响应仍需保持顺序。
- 日志记录 method、标准化 path、status、latency、requestId，避免记录认证头和敏感正文。
- 面向公网时优先让成熟网关处理 TLS、HTTP/2、防护与流量治理；直接使用 Netty 适合协议网关、代理或有定制需求的基础设施。

## 14.8 官方 API

- [HttpServerCodec](https://netty.io/4.2/api/io/netty/handler/codec/http/HttpServerCodec.html)
- [HttpObjectAggregator](https://netty.io/4.2/api/io/netty/handler/codec/http/HttpObjectAggregator.html)
- [FullHttpRequest](https://netty.io/4.2/api/io/netty/handler/codec/http/FullHttpRequest.html)
- [HttpUtil](https://netty.io/4.2/api/io/netty/handler/codec/http/HttpUtil.html)
- [HttpContentCompressor](https://netty.io/4.2/api/io/netty/handler/codec/http/HttpContentCompressor.html)

## 14.9 知识问答

**问：为什么不能把 `HttpObjectAggregator` 的上限设置得非常大？**

答：聚合期间完整正文占用内存，慢请求还会延长占用时间。大上限乘以并发连接数会放大内存风险。

**问：为什么返回了内容却偶尔一直等？**

答：优先检查是否 flush、`Content-Length` 是否正确、是否错误声明 Keep-Alive，以及异常路径是否遗漏响应或关闭。

**问：Netty 能不能做完整 Web 框架？**

答：能提供网络与 HTTP 编解码基础，但路由、参数绑定、验证、异常映射和依赖注入需要自己建设。多数业务项目更适合使用基于 Netty 的成熟框架，定制网络基础设施时才直接操作这些 API。

### 动手题

1. 增加 `GET /hello?name=JingXu`，正确处理缺少参数与 UTF-8 编码。
2. 去掉聚合器，逐段统计 POST 正文长度，并在收到 `LastHttpContent` 后响应。
3. 用 `EmbeddedChannel` 测试 200、404、405、Keep-Alive 和超大请求体。

------

上一章：[[13-测试排错实战与面试问答]]　下一章：[[15-WebSocket长连接实战]]　返回：[[../Java网络IO与Netty学习指南|学习指南]]
