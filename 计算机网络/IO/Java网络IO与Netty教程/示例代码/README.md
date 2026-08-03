# Java 网络 IO 与 Netty 教程代码

## 示例与端口

| 示例 | 入口类 | 端口 |
| --- | --- | --- |
| BIO Echo | `note.io.bio.BioEchoServer` / `BioEchoClient` | 9000 |
| NIO Reactor Echo | `note.io.nio.NioEchoServer` / `BioEchoClient 9001` | 9001 |
| AIO Echo | `note.io.aio.AioEchoServer` / `BioEchoClient 9002` | 9002 |
| Netty 文本 Echo | `note.io.netty.echo.NettyEchoServer` / `NettyEchoClient` | 9003 |
| Netty 自定义协议 | `note.io.netty.protocol.ProtocolServer` / `ProtocolClient` | 9004 |

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

每个服务端都需要手动停止。示例强调机制，没有实现完整的生产停机编排。

## 推荐阅读顺序

1. 依次运行 BIO、NIO、AIO Echo，比较线程名与控制流。
2. 运行 Netty Echo，对照第 6～9 章。
3. 运行自定义协议，再执行 `mvn test`，对照第 10、13 章。
4. 把 `ReliableHandlers` 加入自己的 Pipeline，实验心跳和背压。

