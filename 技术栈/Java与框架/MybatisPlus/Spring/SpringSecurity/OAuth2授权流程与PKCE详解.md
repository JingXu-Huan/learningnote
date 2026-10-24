# OAuth 2.0 授权流程与 PKCE 详解：从浏览器跳转到令牌刷新

OAuth 2.0 让应用获得访问资源的有限权限。理解它的关键，是分清谁在授权、谁在换取令牌、令牌发给谁，以及资源服务器最后检查什么。

全文使用一个贯穿案例：**用户允许“照片打印网站”读取云盘照片，但不允许它删除照片，也不把云盘密码交给照片网站。** 文中的域名、接口路径、权限名和有效期均为教学示例；具体平台的配置和参数以其官方文档为准。

主线采用**授权码模式 + PKCE**，先解释有后端的网站，再对照纯前端应用、第三方登录与服务间调用。安全要求结合 RFC 9700；资料核对日期：2026-10-24。

## 阅读目录

- [1. OAuth 2.0 解决什么问题](#purpose)
- [2. 四个角色与两个通信通道](#roles)
- [3. 授权码模式完整时序图](#authorization-code)
- [4. 把流程拆成实际 HTTP 请求](#http)
- [5. 为什么先给授权码，再换令牌](#code-and-token)
- [6. PKCE 怎样防止授权码被冒用](#pkce)
- [7. state、nonce、PKCE 各管什么](#transaction-binding)
- [8. 资源服务器怎样校验访问令牌](#resource-validation)
- [9. 访问令牌过期与刷新令牌轮换](#refresh)
- [10. OAuth 2.0 与 OIDC 第三方登录](#oidc)
- [11. 有后端的网站与纯前端应用](#client-types)
- [12. 服务间调用与设备授权](#other-flows)
- [13. 怎样选择授权流程](#selection)
- [14. 对应到 Spring Security](#spring-security)
- [15. 退出登录、撤销授权与常见排错](#troubleshooting)
- [16. 面试复述与练习](#practice)
- [17. 官方资料与延伸阅读](#references)

---

<a id="purpose"></a>

## 1. OAuth 2.0 解决什么问题

没有委托授权时，第三方应用可能要求用户直接交出另一个平台的账号密码。这样既难以限制权限，也难以单独撤回某个应用的访问能力。

在照片案例中，授权应当表达为：“允许这个应用读取这些照片”，而不是“这个应用可以完全代替用户”。

**图 1：权限沿着授权边界逐步收窄。**

```mermaid
flowchart TB
    U["用户拥有云盘资源"] --> A["授权照片网站访问"]
    A --> S["授权范围：photos.read"]
    S --> T["签发有限权限的访问令牌"]
    T --> R["读取允许访问的照片"]
    T -. "没有对应权限" .-> D["不能据此删除照片"]
```

需要同时区分三个问题：

| 问题                 | 所属层面                | 照片案例                     |
| -------------------- | ----------------------- | ---------------------------- |
| 用户是谁             | 身份认证 Authentication | 云盘确认当前登录者           |
| 应用获得哪些访问权   | OAuth 委托授权          | 照片网站获得读取照片的权限   |
| 某张照片能不能被读取 | 资源与业务权限          | 照片归属、租户与分享规则检查 |

OAuth 流程中通常会出现用户登录，但 OAuth 2.0 本身并没有定义完整、通用的第三方登录身份协议。需要标准化确认用户身份时，应结合后文的 OIDC。

协议依据：[RFC 6749 的角色与授权框架](https://www.rfc-editor.org/rfc/rfc6749.html#section-1)、[OIDC 的身份层定义](https://openid.net/specs/openid-connect-core-1_0.html#Introduction)。

<a id="roles"></a>

## 2. 四个角色与两个通信通道

### 四个角色不是四台固定的机器

| 角色       | 英文                 | 本文例子     | 主要职责                           |
| ---------- | -------------------- | ------------ | ---------------------------------- |
| 资源所有者 | Resource Owner       | 用户         | 决定是否允许访问                   |
| 客户端     | Client               | 照片网站     | 发起授权，换取令牌并调用 API       |
| 授权服务器 | Authorization Server | 云盘授权服务 | 处理用户授权、客户端认证和令牌签发 |
| 资源服务器 | Resource Server      | 云盘照片 API | 校验令牌并执行资源权限检查         |

**Client 是协议角色，不等同于浏览器。** 对有后端的网站来说，后端通常负责兑换和保存令牌。授权服务和资源服务也可能部署在同一个系统中，但职责仍然不同。

**图 2：角色之间的职责。**

```mermaid
flowchart LR
    U["资源所有者：用户"] -->|"确认授权"| A["授权服务器"]
    C["客户端：照片网站"] -->|"申请令牌"| A
    A -->|"签发访问令牌"| C
    C -->|"令牌 + 读取请求"| R["资源服务器：照片 API"]
    R -->|"照片数据"| C
```

### 浏览器跳转与后端请求是两条通道

**图 3：前通道经过浏览器，后通道由客户端直接请求服务。**

```mermaid
flowchart TB
    subgraph F["前通道：浏览器导航与跳转"]
        W["照片网站"] --> B["浏览器"]
        B --> P["云盘登录和授权页面"]
        P --> Q["浏览器访问网站回调：code + state"]
    end
    subgraph K["后通道：本例由网站后端发起"]
        C["照片网站后端"] -->|"code + verifier"| A["Token 接口"]
        A -->|"access_token"| C
        C -->|"Bearer 令牌"| R["照片 API"]
    end
    Q --> C
```

“后通道”描述通信方式，不意味着所有应用都有独立后端。纯前端应用也可以在平台支持相应跨域请求时直接调用 Token 接口，其安全边界见第 11 节。

<a id="authorization-code"></a>

## 3. 授权码模式完整时序图

**图 4：先看主线，再回到每一步的参数。**

```mermaid
sequenceDiagram
    actor B as 用户与浏览器
    participant C as 照片网站后端
    participant A as 云盘授权服务器
    participant R as 云盘照片 API
    B->>C: 1. 点击连接云盘
    Note over C: 生成 state、verifier 和 challenge<br/>保存本次授权事务
    C-->>B: 2. 重定向到授权端点
    B->>A: client_id、scope、state、challenge
    A-->>B: 登录与授权页面
    B->>A: 3. 完成登录并同意授权
    A-->>B: 4. 重定向到登记的回调地址，携带 code、state
    B->>C: 浏览器访问回调地址
    Note over C: 校验 state，取出本次事务的 verifier
    C->>A: 5. code、verifier 和所需客户端认证
    Note over A: 校验授权码、客户端、回调地址和 PKCE
    A-->>C: access_token，可选 refresh_token
    C->>R: 6. 携带 access_token 请求照片
    R-->>C: 校验令牌和业务权限后返回照片
    C-->>B: 展示照片
```

这六步中有三个不同的结果：

1. 用户授权后，客户端得到 **code**。
2. 授权服务器验证兑换请求后，客户端得到 **access_token**。
3. 资源服务器验证 API 请求后，客户端才得到 **照片数据**。

已有云盘登录会话、已有授权记录或企业管理策略时，平台可能省略部分页面交互。并不要求每次都输入密码、每次都点击一次同意。

流程依据：[RFC 6749 §4.1](https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1)。

<a id="http"></a>

## 4. 把流程拆成实际 HTTP 请求

以下报文是结构示例，尖括号表示待替换值；省略了与教学主线无关的请求头。授权请求中的查询参数分行展示，真正发送时应组成一个正确编码的 URL。

### 第一步：注册应用并准备一次授权事务

照片网站在云盘平台登记：

```text
应用标识：client_id = photo-web
回调地址：https://photo.example/oauth/callback
权限名称：photos.read
```

`client_id` 用来标识应用，通常不是秘密。能够安全保管客户端凭据的后端应用，可以按平台注册方式使用 `client_secret`、私钥等进行客户端认证。静态前端文件和移动端安装包无法可靠保管通用客户端秘密。

每次授权还要创建独立的事务数据，例如 `state`、`code_verifier`，并与发起本次请求的浏览器会话安全关联。不能把这些值写成所有用户共用的常量。

### 第二步：把浏览器送往授权端点

```text
https://auth.drive.example/authorize
  ?response_type=code
  &client_id=photo-web
  &redirect_uri=https%3A%2F%2Fphoto.example%2Foauth%2Fcallback
  &scope=photos.read
  &state=<本次事务的随机值>
  &code_challenge=<由 verifier 计算的 challenge>
  &code_challenge_method=S256
```

| 参数                    | 回答的问题                     | 容易混淆的地方                   |
| ----------------------- | ------------------------------ | -------------------------------- |
| `response_type=code`    | 授权端点返回什么               | 此处要授权码，不是直接要访问令牌 |
| `client_id`             | 哪个应用在申请                 | 不是用户 ID                      |
| `redirect_uri`          | 完成后回到哪里                 | 不是任意填写的下一页地址         |
| `scope`                 | 申请哪些权限                   | 最终授予权限可能更少             |
| `state`                 | 对应客户端的哪次请求           | 必须安全绑定并校验               |
| `code_challenge`        | 兑换时要验证哪份证明           | 不是原始 verifier                |
| `code_challenge_method` | 怎样从 verifier 得到 challenge | 本文使用 `S256`                  |

授权端点用于浏览器导航，不能把“向授权端点发一个 AJAX 请求”当作用户授权流程的完整替代。

### 第三步：接收回调并验证事务

同意授权后的回调示例：

```http
GET /oauth/callback?code=<一次性授权码>&state=<原样返回的值> HTTP/1.1
Host: photo.example
```

网站先检查 `state` 是否对应当前浏览器发起、尚未消费的事务，再使用事务中保存的 verifier 继续兑换。用户拒绝时，可能收到 `error=access_denied`，也应正确关联和终止本次事务。

不要在回调页面加载不必要的第三方资源，不要把完整回调 URL 和授权码写进普通日志。业务返回地址应保存在受约束的事务数据中，不能造成任意跳转。

### 第四步：后端兑换令牌

下面展示采用 HTTP Basic 客户端认证的机密客户端。占位值代表按平台注册方式编码后的客户端凭据；真实平台也可能要求其他认证方法。

```http
POST /token HTTP/1.1
Host: auth.drive.example
Authorization: Basic <客户端凭据的编码结果>
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=<授权码>&redirect_uri=https%3A%2F%2Fphoto.example%2Foauth%2Fcallback&code_verifier=<原始verifier>
```

公共客户端通常没有这样的共享秘密认证，应按平台要求提交 `client_id` 等参数；**PKCE 不会把公共客户端变成能够保密的机密客户端，也不替代机密客户端本来需要的认证。**

示例响应：

```json
{
  "access_token": "access_token_example",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "refresh_token_example",
  "scope": "photos.read"
}
```

这里的 900 秒只是示例。`refresh_token` 是否签发由平台策略决定，不能假设每次都有。

### 第五步：使用访问令牌请求资源

```http
GET /photos HTTP/1.1
Host: api.drive.example
Authorization: Bearer access_token_example
```

Bearer 令牌通常意味着持有它即可使用相应访问能力，因此应通过 HTTPS 传输，并避免出现在 URL、前端报错上报、截图和普通日志中。

请求语义参见 [RFC 6749 §4.1](https://www.rfc-editor.org/rfc/rfc6749.html#section-4.1)、[PKCE 请求参数](https://www.rfc-editor.org/rfc/rfc7636.html#section-4)、[Bearer 请求头用法](https://www.rfc-editor.org/rfc/rfc6750.html#section-2.1)；安全边界参见 [OWASP OAuth2 Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)。

<a id="code-and-token"></a>

## 5. 为什么先给授权码，再换令牌

授权码把浏览器中的授权交互与访问令牌的签发分成两段。在本例中，浏览器传递短期、一次性的兑换凭证，长期使用的访问能力由网站后端通过 Token 接口获取。

**图 5：三类凭证有不同用途与接收方。**

```mermaid
flowchart TB
    C["授权码 code"] -->|"交给 Token 接口兑换"| T["访问令牌 access_token"]
    T -->|"交给资源 API"| R["允许访问的资源"]
    F["刷新令牌 refresh_token"] -->|"交给 Token 接口刷新"| N["新的访问令牌"]
```

| 凭证            | 交给谁                | 主要用途       | 能否反复使用                 |
| --------------- | --------------------- | -------------- | ---------------------------- |
| `code`          | 授权服务器 Token 接口 | 兑换令牌       | 一次性，且短期有效           |
| `access_token`  | 目标资源服务器        | 调用 API       | 在有效期与策略允许范围内使用 |
| `refresh_token` | 授权服务器 Token 接口 | 获取新访问令牌 | 取决于轮换和失效策略         |

授权码本身仍然是敏感凭证。它需要绑定客户端与授权事务，并通过 PKCE 等措施防止被冒用；不能因为“不是访问令牌”就随意公开。

OAuth 也没有规定访问令牌一定是 JWT。客户端应把访问令牌当作平台签发的凭证使用，避免依赖自行解析出的内部字段。JWT Access Token 的专门规范见 [RFC 9068](https://www.rfc-editor.org/rfc/rfc9068.html#section-6)。

<a id="pkce"></a>

## 6. PKCE 怎样防止授权码被冒用

PKCE 全称 Proof Key for Code Exchange，可以理解为：“兑换授权码时，还要拿出本次请求预先约定的证明。”

### 先给摘要，兑换时再提交原始秘密

客户端用安全随机源生成每次事务独立的 `code_verifier`，保存在本次授权事务中。使用 S256 时：

```text
code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))
```

Base64URL 编码不保留结尾的 `=` 填充。verifier 应符合 RFC 7636 的字符集和 43～128 字符长度要求；一种常见生成方法是对 32 字节安全随机数做无填充的 Base64URL 编码。

**图 6：授权时提交 challenge，兑换时提交 verifier。**

```mermaid
flowchart TB
    V["生成随机 code_verifier，并保存"] --> H["SHA-256，再做 Base64URL 编码"]
    H --> C["code_challenge"]
    C --> A["授权请求携带 challenge 和 S256"]
    A --> S["服务器把 challenge 与授权码关联"]
    V --> T["兑换请求提交原始 verifier 与 code"]
    S --> K["服务器计算并比较"]
    T --> K
    K --> Q{"计算结果与 challenge 一致？"}
    Q -->|"是，且其他校验通过"| OK["签发令牌"]
    Q -->|"否"| NO["拒绝兑换"]
```

### 截获 code 为什么还不够

**图 7：假设攻击者仅截获授权码，没有获得 verifier。**

```mermaid
sequenceDiagram
    participant C as 正常客户端
    participant A as 授权服务器
    participant X as 攻击者
    C->>A: 发起授权，提交 challenge
    Note over A: challenge 与随后签发的 code 绑定
    A-->>C: 通过回调返回 code
    Note over X: 假设只截获了 code
    X->>A: 用 code 和错误的 verifier 兑换
    A-->>X: PKCE 校验失败，拒绝
    C->>A: 用 code 和正确的 verifier 兑换
    A-->>C: 其他校验通过后签发令牌
```

PKCE 防的是这类授权码冒用，不是所有令牌窃取。攻击者若已能读取客户端保存的 verifier，或已窃取最终访问令牌，还需要其他防护处理相应风险。

### 用标准测试向量验证计算过程

下面使用 RFC 7636 附录 B 的公开测试向量，只用于验证算法，实际授权必须生成新的随机值。

```python
import base64
import hashlib

verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
challenge = base64.urlsafe_b64encode(
    hashlib.sha256(verifier.encode("ascii")).digest()
).rstrip(b"=").decode("ascii")

assert challenge == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
print(challenge)
```

算法与测试向量依据：[RFC 7636 §4](https://www.rfc-editor.org/rfc/rfc7636.html#section-4)、[附录 B](https://www.rfc-editor.org/rfc/rfc7636.html#appendix-B)。

<a id="transaction-binding"></a>

## 7. state、nonce、PKCE 各管什么

这几个值都可能与一次授权事务相关，但校验位置和主要作用不同。

| 值              | 主要校验者  | 校验位置      | 关注的问题                            |
| --------------- | ----------- | ------------- | ------------------------------------- |
| `state`         | 客户端      | 授权回调      | 回调能否关联到本浏览器发起的事务      |
| `code_verifier` | 授权服务器  | 兑换授权码    | 兑换方能否提供与 challenge 对应的证明 |
| `nonce`         | OIDC 客户端 | ID Token 验证 | 返回的身份令牌是否对应本次认证请求    |

**图 8：以 state 为索引保存并消费一次事务，是一种常见实现方式。**

```mermaid
flowchart TB
    S["发起授权"] --> T["保存事务：state、verifier、issuer、回调配置"]
    T --> B["绑定到发起请求的浏览器会话"]
    B --> C["收到授权回调"]
    C --> V{"state 对应当前会话中的未过期事务？"}
    V -->|"否"| R["拒绝回调，记录不含秘密的错误信息"]
    V -->|"是"| O["原子消费事务，取出 verifier"]
    O --> E["向该事务对应的 Token 接口兑换"]
```

工程上还要处理：同一个用户开多个标签页、重复刷新回调页、事务过期、授权拒绝，以及多个身份提供方同时接入的情况。只保存一份全局 verifier 或一个“最后一次 state”，容易造成串号或并发失败。

这里把三者分开解释，是为了看清责任。规范也允许在满足条件时依赖 PKCE 或 OIDC nonce 提供 CSRF 防护，不能把“所有流程无条件必须同时带这三个值”当成标准要求。多授权服务器场景还要防止把一个平台的响应交给另一个平台的 Token 接口。

安全要求见 [RFC 9700 §2.1](https://www.rfc-editor.org/rfc/rfc9700.html#section-2.1)、[OIDC nonce 说明](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)。

<a id="resource-validation"></a>

## 8. 资源服务器怎样校验访问令牌

Token 接口返回成功，只说明令牌签发成功。是否能读取某张照片，仍要由资源服务器判断。

### JWT 与不透明令牌的常见验证方式

**图 9：两条验证路径最终都要落到业务权限。**

```mermaid
flowchart TB
    Q["收到带 Bearer 令牌的 API 请求"] --> T{"按已配置的令牌方案验证"}
    T -->|"JWT"| J["使用可信密钥验签，检查算法、类型与声明"]
    T -->|"不透明令牌"| I["查询受保护的 introspection 接口或服务端记录"]
    J --> V{"有效期、签发者、目标服务等校验通过？"}
    I --> V
    V -->|"否"| E["拒绝请求，通常为 401"]
    V -->|"是"| S{"scope 与资源归属等业务权限通过？"}
    S -->|"否"| F["拒绝访问，通常为 403"]
    S -->|"是"| R["执行业务并返回资源"]
```

上图的分支由服务端配置决定，不能因为来访字符串“长得像 JWT”，就切换到一个未经约束的验证器。

| 检查                       | 防止什么问题                      |
| -------------------------- | --------------------------------- |
| 可信签发者 `iss`           | 把其他签发者的令牌当作本系统令牌  |
| 目标受众 `aud`             | 把发给其他 API 的令牌用于当前服务 |
| 签名、允许的算法与令牌类型 | 伪造、篡改或令牌类型混用          |
| 有效期及生效时间           | 使用过期或尚未生效的令牌          |
| `scope` 等授权信息         | 使用读取权限执行删除操作          |
| 数据归属、租户与业务状态   | 同一接口内读取他人或其他租户数据  |

JWT 解码成功不等于验签成功，验签成功也不等于业务授权成功。具体声明要求应遵循所采用的令牌规范和平台契约。

不透明令牌的 introspection 可以返回 `active`、权限及有效期等信息。缓存结果能减少远程请求，但会延迟感知失效，缓存期限需要结合有效期与风险确定。JWT 也可以结合服务端状态检查；不透明令牌也不一定每次都跨网络查询，不能把两者简单理解成“绝对无状态”与“必定查库”。

依据：[RFC 9068 的 JWT Access Token 验证](https://www.rfc-editor.org/rfc/rfc9068.html#section-4)、[RFC 7662 的令牌自省与缓存权衡](https://www.rfc-editor.org/rfc/rfc7662.html#section-2.2)、[RFC 6750 的错误响应](https://www.rfc-editor.org/rfc/rfc6750.html#section-3.1)。

<a id="refresh"></a>

## 9. 访问令牌过期与刷新令牌轮换

### access_token 过期后，不一定需要用户立即重新操作

如果平台签发了仍有效的 `refresh_token`，客户端可以向 Token 接口申请新的访问令牌。刷新请求不能扩大原先授予的权限。

**图 10：刷新发生在客户端与授权服务器之间。**

```mermaid
sequenceDiagram
    participant C as 客户端
    participant R as 资源服务器
    participant A as 授权服务器
    C->>R: 使用旧 access_token 调用 API
    R-->>C: 401，令牌失效
    Note over C: 确认属于可刷新的令牌错误
    C->>A: refresh_token 与所需客户端认证
    alt 刷新凭证有效
        A-->>C: 新 access_token，可选新 refresh_token
        Note over C: 安全更新凭证，受控重试原请求
        C->>R: 使用新 access_token 调用
        R-->>C: 返回资源
    else 刷新凭证失效或被撤销
        A-->>C: invalid_grant 等错误
        Note over C: 清理失效状态，按场景重新授权
    end
```

客户端已知访问令牌即将过期时，也可以提前刷新，不必故意先制造一次 401。401 还有签名错误、受众错误等原因，不能遇到任何 401 都无限刷新。

不要无条件自动重放付款、下单等有副作用的业务请求。是否重试，应结合接口幂等性、错误发生阶段和幂等键判断。

### 轮换让旧刷新令牌失去继续使用的资格

**图 11：轮换与重复使用检测的概念模型。**

```mermaid
flowchart TB
    R0["持有刷新令牌 RT0"] --> U["成功使用 RT0 刷新"]
    U --> R1["签发新访问令牌与 RT1"]
    U --> X["RT0 失效，记录轮换关系"]
    X --> D["再次收到 RT0"]
    D --> P["按平台重用检测、宽限和撤销策略处理"]
    P --> F["可能终止关联刷新链，要求重新授权"]
    R1 --> N["后续使用 RT1"]
```

这是概念模型，具体宽限窗口与撤销粒度依平台实现而定。重复使用可能来自凭证泄露，也可能来自客户端并发刷新，不能仅凭一次失败断言已被攻击。

客户端应按会话合并同时发生的刷新操作，避免多个请求一起使用旧 RT；新凭证要原子更新。多实例后端还要考虑跨实例协调和存储一致性。

规范要求公共客户端的刷新令牌使用发送者约束或轮换等机制防止重放。刷新令牌没有统一固定的“永久有效”语义。

依据：[RFC 6749 §6](https://www.rfc-editor.org/rfc/rfc6749.html#section-6)、[RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)。

<a id="oidc"></a>

## 10. OAuth 2.0 与 OIDC 第三方登录

OAuth 的主问题是“应用能访问什么”；OpenID Connect 在其上增加身份层，标准化表达“本次认证的用户是谁”。请求中的 `scope=openid` 是 OIDC 的重要标志，常与 `profile` 等范围一起使用。

### ID Token 与 Access Token 有不同接收方

| 对象            | 接收方                                   | 用途                       |
| --------------- | ---------------------------------------- | -------------------------- |
| ID Token        | 发起登录的 OIDC 客户端                   | 验证认证结果与用户身份声明 |
| Access Token    | 相应资源 API，包括适用时的 UserInfo 接口 | 访问受保护资源             |
| 本站会话 Cookie | 本站浏览器与后端                         | 维持本站后续请求的登录状态 |

**图 12：第三方身份认证完成后，本站仍要建立自己的会话。**

```mermaid
sequenceDiagram
    actor B as 用户与浏览器
    participant C as 本站后端
    participant I as OIDC 身份提供方
    participant D as 本站账号与会话存储
    B->>C: 使用第三方账号登录
    C-->>B: 跳转，申请 openid 等范围
    B->>I: 完成认证和必要授权
    I-->>B: 返回授权码
    B->>C: 访问回调地址
    C->>I: 使用授权码与 PKCE 证明兑换
    I-->>C: ID Token 与 Access Token
    Note over C: 验证签名、issuer、audience、有效期<br/>以及适用时的 nonce、azp 等条件
    C->>D: 按 issuer 与 subject 查找或绑定本站账号
    D-->>C: 本站用户
    C-->>B: 建立本站会话，设置会话 Cookie
    B->>C: 后续携带本站会话访问
```

一个稳妥的身份映射起点是 `(iss, sub)`，而不是直接把邮箱当作跨平台永不变化的唯一用户标识。账号绑定还要防止把未验证的属性或另一个用户的授权结果绑定到当前账号。

`nonce` 若在请求中使用，客户端需要验证 ID Token 中的对应值。UserInfo 返回的 `sub` 也要与 ID Token 中的主体一致。身份校验应交给成熟的 OIDC 实现处理，不能只把 JWT Payload 解出来就认为登录成功。

ID Token 不应作为通用业务 API 的访问令牌；Access Token 也不能脱离平台契约直接当作完整的登录身份证明。

依据：[OIDC 授权码流程](https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth)、[ID Token 验证](https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation)、[UserInfo 主体检查](https://openid.net/specs/openid-connect-core-1_0.html#UserInfoResponseValidation)。

<a id="client-types"></a>

## 11. 有后端的网站与纯前端应用

### 谁能保管秘密，谁就承担对应责任

| 形态             | 客户端类型       | 令牌通常在哪里处理           | 需要关注                           |
| ---------------- | ---------------- | ---------------------------- | ---------------------------------- |
| 服务端网站或 BFF | 通常为机密客户端 | 后端兑换、保存并调用上游     | 会话保护、CSRF、后端凭据安全       |
| 直接接入的 SPA   | 公共客户端       | 浏览器 JavaScript 兑换和使用 | PKCE、跨域支持、XSS 与令牌生命周期 |
| 移动端或桌面应用 | 通常为公共客户端 | 应用按平台机制处理           | PKCE、系统浏览器、回调被截获风险   |

BFF 是 Backend for Frontend，即面向前端的后端层。它可以让浏览器只接触本站会话，由后端持有上游访问令牌。

**图 13：两种架构中的令牌位置。**

```mermaid
flowchart TB
    subgraph BFF["有后端的 BFF 方案"]
        B1["浏览器"] -->|"本站会话 Cookie"| C1["本站后端：保存上游令牌"]
        C1 -->|"Access Token"| R1["上游 API"]
    end
    subgraph SPA["SPA 直接接入方案"]
        B2["浏览器 JavaScript：处理令牌"] -->|"code + verifier"| A2["授权服务器 Token 接口"]
        A2 -->|"Access Token"| B2
        B2 -->|"Access Token"| R2["上游 API"]
    end
```

把 `client_secret` 写进前端环境变量、构建产物或安装包，不会让它变成秘密。PKCE 保护授权码兑换，但也不会自动解决 XSS、恶意依赖和访问令牌泄露。

对于 BFF，会话 Cookie 通常需要 `Secure`、`HttpOnly` 和合适的 `SameSite` 策略，状态变更接口仍要进行相应 CSRF 防护。SameSite 选择需要兼顾真实授权回调方式；“前后端分离”“使用 JWT”都不是直接关闭 CSRF 的充分理由。

这些是根据凭证暴露面作出的架构分析，不代表任意项目都必须选择同一种存储方式。进一步参考 [OWASP OAuth2 Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)、[RFC 8252 原生应用最佳实践](https://www.rfc-editor.org/rfc/rfc8252.html)。

<a id="other-flows"></a>

## 12. 服务间调用与设备授权

### Client Credentials：应用以自身身份调用

定时任务向内部报表 API 拉取数据时，可能不需要某个用户打开浏览器同意。此时可以让任务服务作为机密客户端，以自己的凭据获取访问权限。

**图 14：没有用户参与的客户端凭据流程。**

```mermaid
sequenceDiagram
    participant C as 报表任务服务
    participant A as 授权服务器
    participant R as 数据 API
    C->>A: grant_type=client_credentials<br/>客户端认证与所需 scope
    A-->>C: Access Token
    C->>R: Bearer 令牌与数据请求
    R-->>C: 按应用权限返回数据
```

这里的权限主体通常是调用应用，不应把它冒充成“某个用户已授权”。资源服务器仍要限制服务能访问的数据范围。该流程通常通过再次进行客户端认证取得新令牌，不依赖刷新令牌。

### Device Authorization：输入受限设备借助另一台设备完成授权

电视、终端等设备缺少合适的浏览器或输入能力时，可以显示用户码和验证地址，让用户在手机或电脑上确认。

**图 15：用户码用于交互，设备码用于轮询。**

```mermaid
sequenceDiagram
    participant D as 电视或受限设备
    participant A as 授权服务器
    actor U as 用户手机浏览器
    D->>A: 申请设备授权
    A-->>D: device_code、user_code、验证地址、轮询间隔
    D-->>U: 显示验证地址和 user_code
    U->>A: 访问验证地址，输入用户码并登录授权
    loop 遵守服务器给出的间隔
        D->>A: 使用 device_code 查询 Token 接口
        A-->>D: 等待、减速、过期或授权成功
    end
    Note over D,A: 成功取得令牌或终止后停止轮询
```

用户码与设备码不能混用；轮询需要正确处理 `authorization_pending`、`slow_down`、拒绝和过期。用户还应核对正在授权的设备与操作，避免被诱导给陌生设备授权。

依据：[Client Credentials](https://www.rfc-editor.org/rfc/rfc6749.html#section-4.4)、[RFC 8628 设备授权](https://www.rfc-editor.org/rfc/rfc8628.html#section-3)。

<a id="selection"></a>

## 13. 怎样选择授权流程

**图 16：按参与者和设备条件选择。**

```mermaid
flowchart TB
    S["应用需要取得访问权限"] --> U{"需要用户参与授权？"}
    U -->|"否，应用以自身身份调用"| C["Client Credentials"]
    U -->|"是"| B{"能够进行合适的浏览器授权？"}
    B -->|"是"| A["Authorization Code + PKCE"]
    B -->|"设备输入或浏览器受限"| D["评估 Device Authorization"]
    A --> I{"还要标准化用户身份认证？"}
    I -->|"是"| O["使用 OIDC，并验证 ID Token"]
    I -->|"否"| R["按 OAuth 授权访问资源"]
```

这张图覆盖常见入门场景，不包含 Token Exchange、JWT Bearer 等扩展授权。

阅读旧教材时还会遇到“四种授权模式”的分类，但不能把历史分类直接当成今天的推荐清单：

| 流程                                | 当前应怎样理解                                                                 |
| ----------------------------------- | ------------------------------------------------------------------------------ |
| Authorization Code                  | 用户参与授权的常见主线，结合 PKCE                                              |
| Client Credentials                  | 适用于机密客户端以自身身份访问                                                 |
| Device Authorization                | 为受限设备提供另一设备上的授权交互                                             |
| Implicit                            | 安全最佳实践要求避免在授权响应中直接发访问令牌等高风险用法，优先采用授权码流程 |
| Resource Owner Password Credentials | RFC 9700 明确要求不得使用，不能作为新系统的常规方案                            |

“用户直接向本站提交密码进行本站登录”与“第三方应用收集用户在另一个平台的密码来兑换令牌”不是同一个设计问题。

依据：[RFC 9700 §2.1.2 与 §2.4](https://www.rfc-editor.org/rfc/rfc9700.html#section-2.1.2)。

<a id="spring-security"></a>

## 14. 对应到 Spring Security

先确定当前服务扮演的协议角色，再选择 Spring Security 功能，避免把所有需求都做成一个自定义 JWT Filter。

**图 17：应用的三个角色，对应三组职责。**

```mermaid
flowchart TB
    W["接入外部账号或外部 API 的网站"] --> C["OAuth2 Client / OAuth2 Login"]
    C --> F["发起授权、处理回调、兑换令牌"]
    P["接收 Bearer 令牌的业务 API"] --> R["OAuth2 Resource Server"]
    R --> V["验证 JWT 或不透明令牌，执行接口授权"]
    I["向其他应用提供授权的平台"] --> A["Authorization Server 能力"]
    A --> T["管理客户端、授权同意、签发令牌"]
```

| Spring 概念                  | 对应协议对象或行为                    |
| ---------------------------- | ------------------------------------- |
| `ClientRegistration`         | 应用注册信息、端点与客户端配置        |
| `OAuth2AuthorizationRequest` | 一次发往授权端点的请求信息            |
| `OAuth2AuthorizedClient`     | 已授权客户端及其关联令牌信息          |
| `oauth2Login()`              | OAuth2/OIDC 登录接入                  |
| `oauth2Client()`             | 客户端访问受保护资源所需支持          |
| `oauth2ResourceServer()`     | 对携带访问令牌的入站 API 请求进行认证 |

Spring 的 OAuth2 Login 常见入口是 `/oauth2/authorization/{registrationId}`，默认回调模板是 `/login/oauth2/code/{registrationId}`。这些是框架约定，可以配置；它们不是 OAuth 标准要求所有平台必须采用的路径。

网站可以既使用登录会话，又作为客户端替用户调用外部 API；同一个部署也可以同时承担多个角色。但 `oauth2Login()` 不会自动把该应用变成给其他系统签发令牌的授权服务器。

本节给出概念映射，不提供可直接复制的生产配置。接入时还需要结合实际 Spring 版本确认 PKCE、客户端认证、会话存储、代理后的回调地址和令牌持久化行为。

官方入口：[OAuth2 Login 核心配置](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)、[授权入口与回调配置](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html)、[OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)。

<a id="troubleshooting"></a>

## 15. 退出登录、撤销授权与常见排错

### 退出本站不等于撤销所有上游访问能力

**图 18：退出相关操作影响不同状态。**

```mermaid
flowchart TB
    L["用户点击退出或断开连接"] --> S["清理本站会话"]
    L --> T["按需求清理本站保存的上游令牌"]
    L --> R["按平台能力调用撤销接口"]
    L --> I["若要求退出身份提供方，使用其注销机制"]
    S --> B["浏览器后续不能继续使用本站旧会话"]
    R --> A["令牌及关联授权状态按服务器策略失效"]
    I --> P["身份提供方的登录会话另行处理"]
```

仅删除浏览器里的字符串，不会让已被复制的令牌自动失效。仅撤销刷新令牌，也不能脱离平台策略保证所有已发出的访问令牌立即失效。使用本地 JWT 验证、缓存自省结果或跨节点状态同步时，还要明确撤销传播的时间边界。

令牌撤销协议见 [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009.html#section-2)；它与本站会话清理、身份提供方的注销是不同机制。

### 按失败发生的位置排查

| 现象                          | 先检查什么                                                  | 不要直接得出的结论                |
| ----------------------------- | ----------------------------------------------------------- | --------------------------------- |
| 授权页面提示回调不合法        | 注册地址、实际 `redirect_uri`、HTTPS、路径和编码            | 不是先改成通配符放行              |
| `state` 不匹配                | 会话是否丢失、事务过期、并发标签页、Cookie 回调策略         | 不能跳过校验来“修复”              |
| 兑换时 `invalid_client`       | 客户端认证方式、凭据、应用类型                              | 不要把秘密搬到前端尝试            |
| 兑换时 `invalid_grant`        | code 是否过期或重复使用、PKCE、回调绑定；刷新时检查 RT 状态 | 不一定是密码错误                  |
| 授权成功但 API 返回 401       | 是否用了 Access Token、目标 API、签发者、受众、有效期       | 不是所有 401 都能靠刷新解决       |
| API 返回权限不足              | 实际授予的 scope、资源归属、租户和业务规则                  | 不能只检查签名有效                |
| 刷新偶发失败                  | 是否并发使用旧 RT、轮换后是否原子保存                       | 未确认前不能断言凭证被盗          |
| SPA 请求 Token 接口被跨域拦截 | 平台是否支持该客户端和相应 CORS 配置                        | 不能靠在前端写 client_secret 绕过 |

记录错误码、事务标识与必要的时间信息即可；日志中应避免保存完整令牌、客户端秘密、verifier 和含授权码的完整 URL。

<a id="practice"></a>

## 16. 面试复述与练习

### 一分钟复述主流程

> 用户发起授权后，客户端把浏览器重定向到授权服务器，并携带客户端标识、回调地址、权限范围、事务关联信息和 PKCE challenge。用户在授权服务器完成认证与授权，浏览器随后把授权码带回客户端。客户端验证事务，再用授权码、verifier 和所需客户端认证兑换访问令牌。资源服务器接收访问令牌，完成令牌与业务权限检查后返回资源。访问令牌过期时，可按平台策略用刷新令牌取得新的访问令牌；需要标准化第三方身份登录时，结合 OIDC 验证 ID Token 并建立本站会话。

这段复述的重点是请求的参与者和凭证去向，不需要背固定的接口路径。

### 五个自测题

1. **拿到授权码后，能直接调用照片 API 吗？** 不能，授权码用于到 Token 接口兑换令牌。
2. **PKCE 能保证访问令牌不被窃取吗？** 不能，它主要约束授权码兑换，需要另外保护最终令牌。
3. **Access Token 一定是 JWT 吗？** 不一定，也可能是不透明令牌。
4. **有 `photos.read` 就能读取任意用户照片吗？** 不能，还要检查资源归属和业务规则。
5. **用第三方账号登录后，本站是不是不用会话管理？** 仍要管理本站后续请求的认证状态，以及账号绑定与退出策略。

### 三个动手练习

| 练习           | 操作                                             | 验收标准                                   |
| -------------- | ------------------------------------------------ | ------------------------------------------ |
| 观察浏览器跳转 | 在测试环境中记录授权端点与回调路径               | 能指出哪里经过浏览器，哪里由后端调用       |
| 验证 PKCE      | 运行第 6 节计算代码，再修改 verifier             | 标准向量得到预期 challenge，修改后结果改变 |
| 分析失败阶段   | 分别模拟拒绝授权、错误 state、过期令牌与权限不足 | 能定位到授权、回调、令牌或资源权限阶段     |

抓包和截图应使用测试账号与测试资源，分享前移除秘密值。本文的报文用于解释协议，不代表已经接入或联调任何真实云盘平台。

<a id="references"></a>

## 17. 官方资料与延伸阅读

| 资料                                                                                                                          | 适合核对的问题                               |
| ----------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| [RFC 6749：OAuth 2.0 授权框架](https://www.rfc-editor.org/rfc/rfc6749.html)                                                   | 角色、授权码、客户端凭据、令牌响应与刷新     |
| [RFC 7636：PKCE](https://www.rfc-editor.org/rfc/rfc7636.html)                                                                 | verifier、challenge、S256 与标准测试向量     |
| [RFC 6750：Bearer Token](https://www.rfc-editor.org/rfc/rfc6750.html)                                                         | 请求头携带令牌、401 与权限不足响应           |
| [RFC 9700：OAuth 2.0 安全最佳实践](https://www.rfc-editor.org/rfc/rfc9700.html)                                               | PKCE、精确回调匹配、旧模式限制、刷新令牌保护 |
| [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)                                              | ID Token、nonce、UserInfo 与身份校验         |
| [RFC 7662：Token Introspection](https://www.rfc-editor.org/rfc/rfc7662.html)                                                  | 不透明令牌验证、active 与缓存时效性          |
| [RFC 9068：JWT Access Token Profile](https://www.rfc-editor.org/rfc/rfc9068.html)                                             | 访问令牌的 JWT 结构和资源服务器验证          |
| [RFC 7009：Token Revocation](https://www.rfc-editor.org/rfc/rfc7009.html)                                                     | 撤销接口及关联令牌处理                       |
| [RFC 8628：Device Authorization](https://www.rfc-editor.org/rfc/rfc8628.html)                                                 | 设备码、用户码、轮询间隔与终止条件           |
| [RFC 8252：原生应用 OAuth 最佳实践](https://www.rfc-editor.org/rfc/rfc8252.html)                                              | 系统浏览器、公共客户端与原生应用回调         |
| [OWASP OAuth2 Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)                            | 实现过程中的安全核对                         |
| [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)               | 框架默认回调与客户端配置                     |
| [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html) | JWT 与不透明令牌的框架支持                   |

建议阅读顺序：先看本文图 4 的主流程，再看图 6 的 PKCE，接着看图 10 的刷新，最后区分图 12 的 OIDC 登录与图 14 的服务间授权。

站内延伸阅读：

- [Spring Security 概述](SpringSecurity概述.md)：认证、授权与过滤器链入门。
- [JWT 与 JJWT](../../../开发常用工具/JWT与JJWT.md)：令牌结构、声明验证与密钥管理。
- [Spring Security FilterChain 核心层与扩展点](Spring-Security-FilterChain-核心层与扩展点.md)：理解框架如何组织入站请求处理。
