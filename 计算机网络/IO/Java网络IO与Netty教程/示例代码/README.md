# Java 网络 IO 与 Netty 教程代码

## 示例与端口

| 示例 | 入口类 | 端口 |
| --- | --- | --- |
| BIO Echo | `note.io.bio.BioEchoServer` / `BioEchoClient` | 9000 |
| NIO Reactor Echo | `note.io.nio.NioEchoServer` / `BioEchoClient 9001` | 9001 |
| AIO Echo | `note.io.aio.AioEchoServer` / `BioEchoClient 9002` | 9002 |
| Netty 文本 Echo | `note.io.netty.echo.NettyEchoServer` / `NettyEchoClient` | 9003 |
| Netty 自定义协议 | `note.io.netty.protocol.ProtocolServer` / `ProtocolClient` | 9004 |
| Netty 聊天室 | `note.io.netty.chat.NettyChatServer` / `NettyChatClient` | 9005 |
| Netty HTTP | `note.io.netty.http.NettyHttpServer` | 9006 |
| Netty WebSocket | `note.io.netty.websocket.NettyWebSocketServer` | 9007 |

## 运行

建议从 IDE 分别启动 Server 和 Client。命令行可先编译：

```powershell
Set-Location -LiteralPath 'D:\skil\learningnote\计算机网络\IO\Java网络IO与Netty教程\示例代码'
mvn -q -DskipTests compile
mvn -q test
```

使用 Maven Exec 插件临时运行（无需修改 `pom.xml`）：

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java '-Dexec.mainClass=note.io.netty.echo.NettyEchoServer'
```

另开一个终端：

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java '-Dexec.mainClass=note.io.netty.echo.NettyEchoClient'
```

聊天室需要同时启动一个服务端和两个客户端：

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java '-Dexec.mainClass=note.io.netty.chat.NettyChatServer'
```

再开两个终端，分别运行：

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java '-Dexec.mainClass=note.io.netty.chat.NettyChatClient'
```

在任意客户端输入文字并回车，其他在线客户端会收到广播。客户端控制台输入 `Ctrl+Z` 后回车退出。

每个服务端都需要手动停止。示例强调机制，没有实现完整的生产停机编排。

## 推荐阅读顺序

1. 先读 `00B-Netty从0到1案例式入门.md` 开头的白话故事，再运行 Netty Echo。
2. 依次运行 BIO、NIO、AIO Echo，比较线程名与控制流。
3. 运行 Netty 聊天室，开两个客户端观察 `ChannelGroup` 广播。
4. 运行自定义协议，再执行 `mvn test`，对照第 10、13 章。
5. 把 `ReliableHandlers` 加入自己的 Pipeline，实验心跳和背压。
6. 运行 HTTP 服务，用 PowerShell 调用 `/health` 与 `/echo`。
7. 运行 WebSocket 服务，在两个浏览器 Console 中连接 `/ws` 并互发消息。
