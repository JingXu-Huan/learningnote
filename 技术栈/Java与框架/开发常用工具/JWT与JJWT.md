# JWT 与 JJWT：Token 不是登录状态本身 🔐

你的 `auth2Demo` 和 `Campus-Water-IQ` 都涉及 JWT。JWT 更准确地说是一种**可携带声明的令牌格式**，JJWT 是 Java 中生成和解析 JWT 的库。

## JWT 的结构

```text
Header.Payload.Signature
```

- Header：算法、类型、密钥标识等元数据；
- Payload：`sub`、`exp`、`iat`、角色等声明；
- Signature：用于验证内容未被篡改。

Payload 默认只是 Base64URL 编码，不是加密。不要把密码、身份证号、银行卡号等敏感数据放进 JWT。

## JJWT 0.12.x 签发和解析

```java
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

String token = Jwts.builder()
    .subject(userId.toString())
    .claim("role", "admin")
    .issuedAt(new Date())
    .expiration(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
    .signWith(key)
    .compact();

Claims claims = Jwts.parser()
    .verifyWith(key)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

你的仓库中还存在 JJWT 0.11.x 的依赖，旧版本常见写法是 `Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)`。升级时不要只修改版本号，要同步检查解析 API、异常类型和密钥类型。

## 必须校验什么

解析成功不等于业务授权成功，至少要检查：

- 签名是否有效；
- `exp` 是否过期；
- `nbf` 是否已经生效；
- `iss` 是否是可信签发者；
- `aud` 是否匹配当前服务；
- `sub` 是否存在且格式正确；
- 角色、权限和租户信息是否仍然有效。

```java
Claims claims = Jwts.parser()
    .requireIssuer("campus-water-auth")
    .requireAudience("water-api")
    .verifyWith(key)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

## 不要从 Token 中直接信任权限

Token 中的角色是签发时的快照。用户被禁用、角色被回收或强制退出时，旧 Token 可能仍然有效。因此可以按安全等级选择：

- 短有效期 Access Token + Refresh Token；
- Redis 维护 Token 黑名单或会话版本；
- 权限变更时递增用户 `tokenVersion`；
- 高风险操作再次查询服务端权限。

## 密钥和算法

- HMAC 使用足够长度的随机密钥，不能使用用户名、短字符串或默认值；
- 非对称签名适合多服务验签：认证服务持有私钥，其他服务只持有公钥；
- 不要根据 Token Header 中的 `alg` 无条件信任算法；
- 密钥要支持轮换，结合 `kid` 找到对应版本的公钥；
- 签发和解析两侧的算法、密钥用途和编码必须一致。

## 网关与业务服务的职责

```text
客户端
  ↓ Authorization: Bearer <token>
网关：基础格式、签名、过期、黑名单
  ↓ 透传可信用户上下文
业务服务：资源权限、租户隔离、数据权限
```

不要只在网关校验然后让内部服务完全信任请求头；内部服务仍应验证来源或使用服务间可信凭证，避免绕过网关直接调用造成越权。

## 异常处理

统一捕获 `JwtException` 及其子类，区分日志级别和客户端响应：

- Token 缺失：未认证；
- 格式错误或签名失败：拒绝并记录安全事件；
- 过期：返回明确的过期错误，让客户端刷新或重新登录；
- 权限不足：认证成功但禁止访问。

不要把签名内容、密钥、完整 Token 打进日志。日志可以记录哈希后的 Token 指纹、用户 ID、请求路径和失败原因。

## 一句话总结

> JWT 解决的是“如何携带并验证声明”，不自动解决注销、权限实时性、密钥轮换和数据权限；JJWT 只负责密码学与解析，授权仍然是业务系统的职责。
