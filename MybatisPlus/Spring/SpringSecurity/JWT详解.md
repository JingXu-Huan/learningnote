# JWT 详解

## 1. 为什么需要 JWT

传统 Web 项目用 **Session** 来记住用户：

```
登录 → 服务端创建 Session → 返回 SessionId（存在 Cookie 里）→ 下次请求带上 Cookie
```

**前后端分离项目的问题**：
- 前端可能是 App、小程序、Vue、React，不一定支持 Cookie
- 多台服务器时，Session 要共享（Redis 存 Session，增加复杂度）
- 跨域时 Cookie 传递麻烦

**JWT 的解决思路**：把用户信息加密后直接给前端，前端每次请求带上这个 Token，服务端不需要存任何东西。

```
登录 → 服务端生成 JWT → 返回给前端 → 前端存在 localStorage → 下次请求放在 Header 里
```

| 对比 | Session | JWT |
|------|---------|-----|
| 状态 | 有状态（服务端存 Session） | 无状态（服务端不存） |
| 存储位置 | Cookie（浏览器自动带） | Header（前端手动加） |
| 跨域 | 麻烦 | 天然支持 |
| 多服务器 | 需要共享 Session | 不需要，Token 自带信息 |
| 主动踢人 | 可以（删 Session） | 不能（需要额外方案） |

------

## 2. JWT 的三段结构

JWT 长这样（三段用 `.` 分隔）：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJUb20iLCJleHAiOjE3MDAwMDAwMDB9.abc123签名
|_________________|.|___________________________________________|.|____________|
       Header                              Payload                       Signature
```

### Header（头部）

```json
{
  "alg": "HS256",    // 签名算法
  "typ": "JWT"       // Token 类型
}
```

Base64 编码后变成 `eyJhbGciOiJIUzI1NiJ9`。

### Payload（载荷 / 数据体）

```json
{
  "sub": "Tom",                    // 主题（通常放用户ID）
  "exp": 1700000000,               // 过期时间（Unix 时间戳）
  "iat": 1699996400,               // 签发时间
  "authorities": ["sys:user:add"]  // 自定义字段：权限列表
}
```

> **重要**：Payload 只是 Base64 编码，不是加密！任何人都能解码看到内容。所以**绝对不能放密码等敏感信息**。

### Signature（签名）

```
HMACSHA256(base64(header) + "." + base64(payload), 密钥)
```

签名 = 把前两段和密钥混在一起做哈希运算。作用是**防篡改**：如果 Payload 被改了，签名就对不上。

------

## 3. 生成和解析 JWT（使用 JJWT 库）

### 引入依赖

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

> **为什么是三个依赖？** `jjwt-api` 是接口，`jjwt-impl` 是实现，`jjwt-jackson` 负责 JSON 序列化。这是 JJWT 0.11+ 的拆分设计。

### 生成 Token

```java
public class JwtUtil {

    // 密钥（实际项目中放配置文件，不要硬编码）
    private static final String SECRET_KEY = "your-256-bit-secret-key-here-must-be-at-least-32-chars";

    // Token 有效期（毫秒）
    private static final long EXPIRATION = 1000 * 60 * 60 * 24; // 24小时

    /**
     * 生成 JWT
     */
    public static String generateToken(String username, List<String> permissions) {
        // 1. 生成安全的密钥
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

        // 2. 构建 Token
        return Jwts.builder()
                .subject(username)                                    // 主题：用户名
                .claim("authorities", permissions)                    // 自定义字段：权限
                .issuedAt(new Date())                                 // 签发时间
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))  // 过期时间
                .signWith(key)                                        // 签名
                .compact();                                           // 生成字符串
    }
}
```

### 解析 Token

```java
/**
 * 解析 JWT，返回 Claims（载荷里的所有字段）
 */
public static Claims parseToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    return Jwts.parser()
            .verifyWith(key)                    // 用同一个密钥验证签名
            .build()
            .parseSignedClaims(token)           // 解析
            .getPayload();                      // 拿到 Payload
}

/**
 * 从 Token 中取出用户名
 */
public static String getUsername(String token) {
    return parseToken(token).getSubject();
}

/**
 * 判断 Token 是否过期
 */
public static boolean isTokenExpired(String token) {
    try {
        return parseToken(token).getExpiration().before(new Date());
    } catch (ExpiredJwtException e) {
        return true;  // 已过期
    }
}
```

### 解析时可能抛出的异常

| 异常 | 含义 | 处理建议 |
|------|------|----------|
| `ExpiredJwtException` | Token 已过期 | 返回 401，让前端跳转登录页 |
| `MalformedJwtException` | Token 格式不对 | 返回 401 |
| `JwtException` | 签名验证失败（被篡改） | 返回 401 |

------

## 4. 密钥的秘密（最容易踩坑的地方）

### 密钥长度要求

| 算法 | 密钥最低长度 |
|------|------------|
| HS256 | 32 字节（256 位） |
| HS384 | 48 字节 |
| HS512 | 64 字节 |

> **坑**：如果密钥太短（比如 `"abc"`），JJWT 会直接报错 `The signing key's size is X bits which is not secure enough for the HS256 algorithm`。

### 正确的密钥生成方式

```java
// ❌ 错误：硬编码一个短字符串
private static final String SECRET = "123456";

// ✅ 正确方式一：配置文件里存一个足够长的字符串
jwt:
  secret: "my-very-long-secret-key-that-is-at-least-32-characters-long"

// ✅ 正确方式二：用 Keys 工具类随机生成（适合开发环境）
SecretKey key = Jwts.SIG.HS256.key().build();
String base64Key = Encoders.BASE64.encode(key.getEncoded());
// 把 base64Key 存到配置文件
```

### 密钥放在配置文件里

```yaml
# application.yml
jwt:
  secret: "my-very-long-secret-key-that-is-at-least-32-characters-long"
  expiration: 86400000  # 24小时（毫秒）
```

```java
@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secret;
    private long expiration;
}
```

------

## 5. 刷新 Token（Token 续期）

JWT 是无状态的，一旦签发就不能改。Token 过期了怎么办？

### 双 Token 方案（推荐）

```
AccessToken  → 短有效期（2小时），用于访问接口
RefreshToken → 长有效期（7天），用于换取新的 AccessToken
```

```
前端请求接口
    ↓
AccessToken 有效？→ 正常返回
    ↓
AccessToken 过期了
    ↓
前端拿 RefreshToken 请求 /refresh 接口
    ↓
服务端验证 RefreshToken → 签发新的 AccessToken
    ↓
前端拿到新 AccessToken，重试原来的请求
```

```java
@PostMapping("/refresh")
public Result refreshToken(@RequestBody String refreshToken) {
    // 1. 验证 RefreshToken
    Claims claims = JwtUtil.parseToken(refreshToken);
    String username = claims.getSubject();

    // 2. 重新查数据库获取最新权限（防止权限变更后旧 Token 还有效）
    List<String> permissions = userService.getPermissions(username);

    // 3. 签发新的 AccessToken
    String newAccessToken = JwtUtil.generateToken(username, permissions);
    return Result.success(newAccessToken);
}
```

> **为什么 AccessToken 要短？** 即使 AccessToken 泄露，攻击窗口也很短。RefreshToken 虽然长，但它只用于 /refresh 接口，暴露面小。

------

## 6. 常见坑总结

| 坑 | 原因 | 解决方案 |
|----|------|----------|
| 密钥太短报错 | HS256 要求至少 32 字节 | 用足够长的密钥字符串 |
| 解析 Token 报 `SignatureException` | 生成和解析用的不是同一个密钥 | 把密钥放配置文件，统一读取 |
| Token 过期后无法主动踢人 | JWT 无状态，服务端不知道哪些 Token 还有效 | 用 Redis 存黑名单，或者用双 Token 方案缩短有效期 |
| Payload 里的信息被前端看到 | Base64 不是加密，任何人都能解码 | 不在 Payload 放密码、身份证等敏感信息 |
| 跨域时前端收不到 Token | Token 放在响应体里，前端需要读取 | 确保返回格式统一，前端从 response body 取 |
| Token 太长 | Payload 里放了太多权限数据 | 只放用户 ID，权限每次从数据库实时查 |

------

## 7. JWT 放在请求的哪里

前端发请求时，Token 放在 HTTP Header 的 `Authorization` 字段：

```
GET /api/users
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...
```

> **Bearer** 是一个规范，意思是"持有这个 Token 的人就是已认证的"。后端解析时要把 `Bearer ` 前缀去掉（注意有空格）。

```javascript
// 前端 axios 拦截器：自动在每个请求头加 Token
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});
```

---

## 🔗 相关笔记

- [[SpringSecurity概述]] —— Spring Security 过滤器链与认证流程
- [[JWT集成SpringSecurity]] —— JWT + Spring Security 完整集成方案
- [[../../../计算机网络/计算机网络知识总结]] —— HTTP 无状态性、HTTPS/TLS 加密基础
