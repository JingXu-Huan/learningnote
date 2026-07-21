# Spring Security FilterChain：核心层与扩展点

> 适用范围：Spring Security 6.x / 7.x，Servlet（Spring MVC）技术栈。  
> 本文重点不是背诵所有 Filter，而是建立一套能用于开发、调试和扩展的心智模型。

![Spring Security 过滤器链](./spring-security-filter-chain.png)

---

## 1. 先记住一条主线

一次请求进入 Spring MVC Controller 之前，大致会经过下面这条链路：

```text
客户端请求
   ↓
Servlet 容器 FilterChain
   ↓
DelegatingFilterProxy
   ↓
FilterChainProxy
   ↓ 选择第一个匹配的 SecurityFilterChain
SecurityContext → 安全防护 → 认证 → 匿名身份 → 异常转换 → 授权
   ↓
DispatcherServlet / Controller
```

Spring Security 的核心并不是某一个 Filter，而是：

1. 使用 `FilterChainProxy` 管理多条安全过滤器链；
2. 使用 `SecurityContext` 保存当前请求的身份；
3. 使用认证组件确认“你是谁”；
4. 使用授权组件判断“你能做什么”；
5. 使用异常处理组件把 Java 异常转换为登录跳转、401 或 403。

可以把整个过程压缩成下面四个阶段：

```text
加载身份 → 尝试认证 → 处理认证/授权异常 → 执行授权
```

---

## 2. 最外层：DelegatingFilterProxy

Servlet 容器只认识标准的 `jakarta.servlet.Filter`，并不知道 Spring 容器里有哪些 Bean。

`DelegatingFilterProxy` 的作用就是在两个容器之间搭桥：

```text
Servlet 容器
    ↓
DelegatingFilterProxy
    ↓ 查找 Spring Bean
springSecurityFilterChain
```

这里的 `springSecurityFilterChain` 实际上通常就是一个 `FilterChainProxy`。

### 这一层解决的问题

- Servlet Filter 的生命周期由 Web 容器管理；
- Spring Security 的组件由 Spring `ApplicationContext` 管理；
- `DelegatingFilterProxy` 负责把请求转交给 Spring 管理的安全组件。

业务开发中通常不需要手动操作这一层。遇到“整个 Spring Security 完全没有生效”时，才需要检查它是否被正确注册。

---

## 3. 总入口：FilterChainProxy

`FilterChainProxy` 是 Spring Security Servlet 架构的真正入口。

它本身也是一个 Filter，但内部维护的是多条 `SecurityFilterChain`：

```text
FilterChainProxy
 ├─ SecurityFilterChain 1：/api/**
 ├─ SecurityFilterChain 2：/admin/**
 └─ SecurityFilterChain 3：/**
```

每个 `SecurityFilterChain` 包含两部分：

```text
RequestMatcher + List<Filter>
```

也就是：

- 当前链匹配哪些请求；
- 匹配成功后执行哪些安全 Filter。

### 关键规则：只执行第一条匹配的链

假设配置如下：

```java
@Bean
@Order(1)
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth
            .anyRequest().authenticated()
        );
    return http.build();
}

@Bean
SecurityFilterChain webChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .anyRequest().permitAll()
        );
    return http.build();
}
```

请求 `/api/users` 时，第一条链已经匹配，后面的链不会再执行。

因此，多链配置最重要的原则是：

> 范围越具体的链，优先级越高；兜底链放最后。

### `securityMatcher` 和 `requestMatchers` 的区别

这是很容易混淆的一点。

```java
http.securityMatcher("/api/**");
```

决定的是：

> 这整条 `SecurityFilterChain` 是否处理当前请求。

而：

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/public/**").permitAll()
    .anyRequest().authenticated()
);
```

决定的是：

> 请求已经进入当前链后，使用哪条授权规则。

可以简单记成：

```text
securityMatcher：选链
requestMatchers：链内授权
```

---

## 4. 第一核心层：SecurityContext

### 4.1 SecurityContextHolder

`SecurityContextHolder` 是获取当前登录用户的统一入口：

```java
Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
```

默认情况下，它使用 `ThreadLocal` 保存当前线程的安全上下文。

常见对象关系如下：

```text
SecurityContextHolder
    └─ SecurityContext
          └─ Authentication
               ├─ principal
               ├─ credentials
               ├─ authorities
               └─ authenticated
```

`Authentication` 有两个不同阶段的含义：

1. 认证前：表示用户提交的认证材料；
2. 认证后：表示已经确认的当前用户身份。

例如用户名密码登录时：

```text
认证前：UsernamePasswordAuthenticationToken(username, password)
认证后：UsernamePasswordAuthenticationToken(userDetails, null, authorities)
```

### 4.2 SecurityContextHolderFilter

这个 Filter 在请求前半段加载 `SecurityContext`，并设置到 `SecurityContextHolder`。

它通常从 `SecurityContextRepository` 中读取上下文。

需要注意：

> `SecurityContextHolderFilter` 只负责加载，不自动负责保存。

如果你在自定义认证逻辑中手动设置了 `SecurityContextHolder`，并且希望身份在后续请求中继续存在，就要显式调用：

```java
securityContextRepository.saveContext(context, request, response);
```

### 4.3 SecurityContextRepository

它决定认证结果如何跨请求保存。

常见实现：

| 实现 | 用途 |
|---|---|
| `HttpSessionSecurityContextRepository` | 将登录状态放进 Session |
| `RequestAttributeSecurityContextRepository` | 在同一次请求的不同 dispatch 之间保存上下文 |
| `DelegatingSecurityContextRepository` | 委托给多个 Repository |
| `NullSecurityContextRepository` | 不保存，适合每次请求重新认证的无状态场景 |

有状态 Web 登录通常依赖 Session；JWT、Bearer Token API 通常每次请求都携带凭证，不依赖 Session 保存身份。

---

## 5. 第二核心层：安全防护 Filter

这类 Filter 一般位于认证 Filter 之前，因为请求在认证之前也可能已经存在安全风险。

### 5.1 HeaderWriterFilter

负责写入安全相关响应头，例如：

- 防止页面被恶意 iframe 嵌套；
- 内容类型嗅探保护；
- 缓存控制；
- HSTS 等。

优先使用 `HttpSecurity.headers(...)` 配置，不要为了加几个 Header 就重写整个 Filter。

### 5.2 CorsFilter

CORS 预检请求通常不会携带登录 Cookie 或 Authorization Header，因此跨域处理必须发生在认证判断之前。

推荐通过配置 `CorsConfigurationSource` 接入：

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

然后：

```java
http.cors(Customizer.withDefaults());
```

### 5.3 CsrfFilter

CSRF 重点防御的是：

> 浏览器自动携带身份凭证，攻击者诱导浏览器发送修改数据的请求。

因此：

- 使用 Session/Cookie 登录的浏览器应用，不要无脑关闭 CSRF；
- 只接受 Authorization Header，且服务端完全无状态的 API，通常可以关闭；
- “前后端分离”本身不是关闭 CSRF 的充分理由，关键要看认证凭证是否会被浏览器自动携带。

---

## 6. 第三核心层：认证 Filter

不同认证方式对应不同 Filter，例如：

| 认证方式 | 常见 Filter |
|---|---|
| 表单登录 | `UsernamePasswordAuthenticationFilter` |
| HTTP Basic | `BasicAuthenticationFilter` |
| OAuth2 Login | `OAuth2LoginAuthenticationFilter` |
| Bearer Token/JWT | `BearerTokenAuthenticationFilter` |
| 自定义协议 | 自定义 Filter 或通用 `AuthenticationFilter` |

虽然 Filter 不同，但核心流程基本一致：

```text
从请求提取凭证
   ↓
封装成未认证 Authentication
   ↓
AuthenticationManager.authenticate(...)
   ↓
AuthenticationProvider 执行认证
   ↓
返回已认证 Authentication
   ↓
放入 SecurityContext
```

### 6.1 AuthenticationManager

Filter 一般不直接查询数据库或校验密码，而是把认证任务交给：

```java
AuthenticationManager
```

最常见实现是：

```java
ProviderManager
```

### 6.2 ProviderManager

`ProviderManager` 内部维护多个 `AuthenticationProvider`：

```text
ProviderManager
 ├─ DaoAuthenticationProvider
 ├─ JwtAuthenticationProvider
 ├─ LdapAuthenticationProvider
 └─ 自定义 AuthenticationProvider
```

它会依次询问 Provider：

```java
boolean supports(Class<?> authenticationType)
```

支持当前 `Authentication` 类型的 Provider 才会尝试认证。

### 6.3 AuthenticationProvider

`AuthenticationProvider` 是最常用、也最推荐的认证扩展点之一。

当你的需求只是“认证规则不同”，通常不需要复制整个 Filter。可以保留请求解析流程，只替换 Provider。

示例：自定义 Header Token 认证 Provider：

```java
public final class HeaderTokenAuthenticationProvider
        implements AuthenticationProvider {

    private final TokenService tokenService;

    public HeaderTokenAuthenticationProvider(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        String token = (String) authentication.getCredentials();
        UserDetails user = tokenService.verify(token);

        if (user == null) {
            throw new BadCredentialsException("Invalid token");
        }

        return UsernamePasswordAuthenticationToken.authenticated(
            user,
            null,
            user.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return HeaderTokenAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
```

注册：

```java
http.authenticationProvider(
    new HeaderTokenAuthenticationProvider(tokenService)
);
```

### 6.4 UserDetailsService 和 PasswordEncoder

在传统用户名密码认证中，常见链路是：

```text
UsernamePasswordAuthenticationFilter
   ↓
ProviderManager
   ↓
DaoAuthenticationProvider
   ├─ UserDetailsService：加载用户
   └─ PasswordEncoder：校验密码
```

因此：

- 改用户加载逻辑：扩展 `UserDetailsService`；
- 改密码算法：配置 `PasswordEncoder`；
- 改整个认证协议：扩展 Filter/Converter；
- 改认证决策：扩展 `AuthenticationProvider`。

---

## 7. 第四核心层：AnonymousAuthenticationFilter

如果前面的认证 Filter 都没有建立身份，`AnonymousAuthenticationFilter` 会放入一个匿名 `Authentication`。

这并不代表用户真的通过了认证，而是为了让后续授权逻辑统一处理：

```text
没有 Authentication
       ↓
AnonymousAuthenticationToken
```

这样授权系统可以直接表达：

```java
.requestMatchers("/public/**").permitAll()
.requestMatchers("/profile/**").authenticated()
```

而不需要到处判断 `authentication == null`。

---

## 8. 第五核心层：ExceptionTranslationFilter

名字虽然叫“异常转换”，但它不是全局业务异常处理器。

它主要处理安全链路中的两类异常：

```text
AuthenticationException
AccessDeniedException
```

大致流程：

```text
下游抛出安全异常
       ↓
ExceptionTranslationFilter
       ├─ 未认证：AuthenticationEntryPoint
       └─ 已认证但权限不足：AccessDeniedHandler
```

### 8.1 AuthenticationEntryPoint

处理“你还没有登录”或认证无效的情况，通常返回 401，或者跳转登录页。

REST API 常见配置：

```java
AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("""
        {"code":401,"message":"Unauthorized"}
        """);
};
```

### 8.2 AccessDeniedHandler

处理“已经有身份，但权限不够”的情况，通常返回 403：

```java
AccessDeniedHandler deniedHandler = (request, response, ex) -> {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("""
        {"code":403,"message":"Forbidden"}
        """);
};
```

注册：

```java
http.exceptionHandling(ex -> ex
    .authenticationEntryPoint(entryPoint)
    .accessDeniedHandler(deniedHandler)
);
```

### 一个很常见的坑

如果自定义 Filter 放在 `ExceptionTranslationFilter` 之前，并且直接抛异常，异常不一定会被它捕获，因为异常必须从它后面的链路向外冒泡。

自定义认证 Filter 更稳妥的做法是：

- 使用 `AuthenticationFailureHandler` 处理认证失败；或
- 在 Filter 内直接调用 `AuthenticationEntryPoint`；或
- 使用 Spring Security 已有的认证 Filter 基类。

---

## 9. 第六核心层：AuthorizationFilter

`AuthorizationFilter` 默认位于 Spring Security FilterChain 的后部，负责最终请求级授权。

它会：

1. 从 `SecurityContextHolder` 获取 `Authentication`；
2. 把当前请求和身份交给 `AuthorizationManager`；
3. 授权通过则继续执行 Controller；
4. 授权失败则抛出 `AccessDeniedException`。

典型配置：

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/login", "/public/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/articles/**").hasAuthority("article:read")
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
);
```

### AuthorizationManager 扩展点

当 URL + Role 不足以描述权限时，可以实现自定义 `AuthorizationManager`。

例如：请求头中的租户必须属于当前用户：

```java
AuthorizationManager<RequestAuthorizationContext> tenantAuthorization =
        (authenticationSupplier, context) -> {
            Authentication authentication = authenticationSupplier.get();
            String tenantId = context.getRequest().getHeader("X-Tenant-Id");

            boolean allowed = tenantService.hasAccess(
                authentication.getName(), tenantId
            );

            return new AuthorizationDecision(allowed);
        };
```

配置到指定请求：

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/tenant/**").access(tenantAuthorization)
    .anyRequest().authenticated()
);
```

对于 Spring Security 6/7，新代码应优先使用 `AuthorizationManager`，而不是旧的 `AccessDecisionManager`、`AccessDecisionVoter` 模型。

---

## 10. 常见辅助层

### 10.1 LogoutFilter

处理退出登录请求，常见职责：

- 清除 `SecurityContext`；
- 使 Session 失效；
- 清理 Cookie；
- 执行自定义 `LogoutHandler`；
- 调用 `LogoutSuccessHandler`。

扩展退出逻辑时，优先配置 DSL：

```java
http.logout(logout -> logout
    .logoutUrl("/api/logout")
    .addLogoutHandler(customLogoutHandler)
    .logoutSuccessHandler(customLogoutSuccessHandler)
);
```

### 10.2 RequestCacheAwareFilter

典型表单登录场景中：

1. 用户访问受保护页面；
2. 系统缓存原请求；
3. 跳转登录页；
4. 登录成功后恢复原请求。

纯 REST API 通常不需要这种跳转语义，可以考虑关闭 Request Cache：

```java
http.requestCache(cache -> cache.disable());
```

### 10.3 SessionManagementFilter / Session 策略

常见用途：

- Session 固定攻击防护；
- 并发 Session 控制；
- 无状态 API；
- Session 创建策略。

无状态 API：

```java
http.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
);
```

注意：`STATELESS` 不等于自动拥有 JWT 能力，它只是告诉 Spring Security 不要使用 Session 保存登录状态。

---

## 11. 扩展点一：添加自定义 Filter

### 11.1 推荐继承 OncePerRequestFilter

```java
public final class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = Optional
            .ofNullable(request.getHeader("X-Trace-Id"))
            .orElseGet(() -> UUID.randomUUID().toString());

        response.setHeader("X-Trace-Id", traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理 ThreadLocal、MDC 等资源
        }
    }
}
```

### 11.2 三种插入方式

```java
http.addFilterBefore(filter, SomeFilter.class);
http.addFilterAfter(filter, SomeFilter.class);
http.addFilterAt(filter, SomeFilter.class);
```

含义：

| 方法 | 含义 | 建议 |
|---|---|---|
| `addFilterBefore` | 放在目标 Filter 前 | 最常用 |
| `addFilterAfter` | 放在目标 Filter 后 | 需要目标阶段已经完成时使用 |
| `addFilterAt` | 放到目标 Filter 的槽位 | 容易改变默认行为，谨慎使用 |

### 11.3 如何选择位置

不要机械记忆“JWT Filter 永远放到某个 Filter 前面”，而是先判断你的 Filter 依赖哪些阶段。

| 自定义 Filter 类型 | 推荐位置思路 |
|---|---|
| 日志、Trace、限流 | 根据是否需要当前用户决定放在认证前或认证后 |
| 自定义认证 | 在安全防护之后、最终授权之前 |
| 依赖当前用户的租户校验 | 在认证完成之后 |
| 自定义授权 | 优先考虑 `AuthorizationManager`，而不是手写 Filter |

官方给出的通用顺序可以理解为：

```text
SecurityContext 已加载
   ↓
Header / CORS / CSRF 等安全防护
   ↓
LogoutFilter
   ↓
各种 Authentication Filter
   ↓
AnonymousAuthenticationFilter
   ↓
ExceptionTranslationFilter
   ↓
AuthorizationFilter
```

### 11.4 避免 Filter 执行两次

如果自定义 Filter 同时满足以下条件：

- 被声明成 Spring Bean；
- 又通过 `http.addFilterBefore/After` 加入安全链；

Spring Boot 可能还会把它注册到 Servlet 容器 FilterChain，导致执行两次。

可以关闭容器自动注册：

```java
@Bean
FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(
        TraceIdFilter filter) {

    FilterRegistrationBean<TraceIdFilter> registration =
            new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
}
```

---

## 12. 扩展点二：AuthenticationFilter + AuthenticationConverter

自定义认证协议时，不一定要从 `OncePerRequestFilter` 手动编排所有逻辑。

Spring Security 提供了更通用的组合方式：

```text
AuthenticationConverter
        ↓ 从请求提取凭证
AuthenticationFilter
        ↓
AuthenticationManager
        ↓
AuthenticationProvider
        ↓
SuccessHandler / FailureHandler
```

各组件职责清晰：

- `AuthenticationConverter`：从 Header、Body、Cookie 中解析认证材料；
- `AuthenticationFilter`：负责认证流程编排；
- `AuthenticationProvider`：负责验证材料；
- `AuthenticationSuccessHandler`：认证成功响应；
- `AuthenticationFailureHandler`：认证失败响应。

当认证协议稍复杂时，这种拆分比在一个 Filter 里写完所有逻辑更容易测试和维护。

---

## 13. 扩展点三：自定义 SecurityFilterChain

典型项目可能同时存在：

- `/api/**`：JWT、无状态、返回 JSON；
- `/admin/**`：Session、后台页面；
- `/actuator/**`：单独的运维权限。

可以分别定义多条链：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(resource -> resource.jwt(
                Customizer.withDefaults()
            ));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests(auth -> auth
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    SecurityFilterChain fallbackChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
```

这种结构比在一条超长配置里塞入大量条件更清晰。

---

## 14. 扩展点四：自定义 HttpSecurity DSL

如果公司内部有一套统一认证规范，可以把重复配置封装成 `AbstractHttpConfigurer`。

```java
public final class InternalTokenDsl
        extends AbstractHttpConfigurer<InternalTokenDsl, HttpSecurity> {

    @Override
    public void configure(HttpSecurity http) {
        AuthenticationManager manager =
            http.getSharedObject(AuthenticationManager.class);

        InternalTokenFilter filter = new InternalTokenFilter(manager);
        http.addFilterAfter(filter, LogoutFilter.class);
    }

    public static InternalTokenDsl internalToken() {
        return new InternalTokenDsl();
    }
}
```

使用：

```java
http.with(
    InternalTokenDsl.internalToken(),
    Customizer.withDefaults()
);
```

适合封装：

- 公司统一 Token 协议；
- 统一 401/403 JSON；
- 多租户安全组件；
- 内部 SDK 的安全默认配置；
- 一组必须按固定顺序注册的 Filter 和 Provider。

---

## 15. JWT 项目的推荐做法

如果使用的是标准 Bearer JWT，优先使用 Spring Security Resource Server：

```java
http.oauth2ResourceServer(resource -> resource
    .jwt(Customizer.withDefaults())
);
```

它会自动接入：

- Bearer Token 提取；
- JWT 解码与签名校验；
- `JwtAuthenticationProvider`；
- `BearerTokenAuthenticationFilter`；
- 认证失败处理；
- Scope/Authority 转换扩展点。

只有在以下情况才更值得手写认证 Filter：

- Token 不符合 Bearer 规范；
- 凭证来自特殊 Header、Cookie 或二进制协议；
- 认证过程需要非标准挑战/响应；
- 需要兼容遗留协议。

不要把“解析 JWT、查 Redis、设置 SecurityContext”全部堆进一个几百行 Filter。更合理的拆分是：

```text
Converter：提取 Token
Provider：验证 Token
User/Permission Service：读取用户与权限
Success/Failure Handler：输出结果
SecurityContextRepository：决定是否持久化
```

---

## 16. 调试 FilterChain

### 16.1 打印启动时的过滤器链

```yaml
logging:
  level:
    org.springframework.security: DEBUG
```

启动时会看到类似：

```text
Will secure any request with [
  SecurityContextHolderFilter,
  HeaderWriterFilter,
  CsrfFilter,
  LogoutFilter,
  UsernamePasswordAuthenticationFilter,
  AnonymousAuthenticationFilter,
  ExceptionTranslationFilter,
  AuthorizationFilter
]
```

### 16.2 断点位置

排查问题时，可以优先在这些位置打断点：

```text
FilterChainProxy#doFilter
SecurityContextHolderFilter#doFilter
具体 AuthenticationFilter#doFilter
ProviderManager#authenticate
具体 AuthenticationProvider#authenticate
ExceptionTranslationFilter#doFilter
AuthorizationFilter#doFilter
```

### 16.3 常见排查顺序

认证失败：

```text
请求是否进入正确的 SecurityFilterChain
→ 自定义 Filter 是否在链中
→ Filter 是否提取到凭证
→ Authentication 类型是否被 Provider.supports 支持
→ Provider 是否返回 authenticated Authentication
→ SecurityContext 是否被设置
```

授权失败：

```text
SecurityContext 中是否有 Authentication
→ authorities 是否正确
→ ROLE_ 前缀是否一致
→ requestMatchers 顺序是否正确
→ 是否进入了意外的 SecurityFilterChain
→ 401 还是 403
```

---

## 17. 最常见的坑

### 坑 1：把认证和授权混在一起

认证 Filter 应回答：

```text
这个请求代表谁？
```

授权组件应回答：

```text
这个用户是否可以访问当前资源？
```

如果在 JWT Filter 里直接判断 `/admin/**`，会让安全规则难以维护。

### 坑 2：只设置 SecurityContextHolder，不考虑持久化

手动登录成功后：

```java
SecurityContextHolder.setContext(context);
```

只保证当前线程能获取身份。需要 Session 持久化时，还必须通过 `SecurityContextRepository` 保存。

### 坑 3：随意关闭 CSRF

应根据“认证凭证是否由浏览器自动携带”判断，而不是根据“是否前后端分离”判断。

### 坑 4：多条 SecurityFilterChain 顺序错误

宽泛的 `/**` 放在最前面，会吃掉后面的 `/api/**` 链。

### 坑 5：`permitAll` 不等于绕过 FilterChain

`permitAll` 只是授权通过，请求仍可能经过：

- SecurityContext；
- Header；
- CORS；
- CSRF；
- 自定义 Filter；
- AnonymousAuthenticationFilter。

### 坑 6：把自定义 Filter 同时注册两次

既是 Servlet Filter Bean，又加入 Spring Security 链，可能执行两遍。

### 坑 7：把所有异常都交给 `@ControllerAdvice`

安全异常经常发生在进入 Controller 之前。401/403 应优先配置：

- `AuthenticationEntryPoint`；
- `AccessDeniedHandler`；
- `AuthenticationFailureHandler`。

---

## 18. 一张表总结核心扩展点

| 想扩展什么 | 优先选择 |
|---|---|
| 用户从哪里加载 | `UserDetailsService` |
| 密码如何校验 | `PasswordEncoder` |
| 某种凭证如何认证 | `AuthenticationProvider` |
| 如何从请求提取凭证 | `AuthenticationConverter` |
| 完整自定义认证流程 | `AuthenticationFilter` 或 `OncePerRequestFilter` |
| 认证成功/失败如何响应 | `AuthenticationSuccessHandler` / `AuthenticationFailureHandler` |
| 未登录如何响应 | `AuthenticationEntryPoint` |
| 权限不足如何响应 | `AccessDeniedHandler` |
| URL 权限规则 | `authorizeHttpRequests` |
| 动态授权规则 | `AuthorizationManager` |
| 登录状态如何保存 | `SecurityContextRepository` |
| 多套安全策略 | 多个 `SecurityFilterChain` + `securityMatcher` |
| 复用整套安全配置 | `AbstractHttpConfigurer` |
| 调整 Filter 顺序 | `addFilterBefore` / `addFilterAfter` |

---

## 19. 最终心智模型

不用死记所有 Filter 名字，只要掌握下面的模型：

```text
1. FilterChainProxy 选择一条 SecurityFilterChain
2. SecurityContextHolderFilter 加载已有身份
3. Header/CORS/CSRF 等组件保护请求
4. Authentication Filter 尝试建立身份
5. AuthenticationManager 委托 Provider 完成认证
6. AnonymousAuthenticationFilter 为未登录请求补匿名身份
7. ExceptionTranslationFilter 负责 401/403 的转换入口
8. AuthorizationFilter 使用 AuthorizationManager 做最终授权
9. Controller 执行业务逻辑
10. 请求结束后安全上下文被清理
```

开发自定义组件前先问三个问题：

```text
这个组件是在做认证、授权，还是请求增强？
它依赖 SecurityContext 中已经存在用户吗？
它应该位于异常处理和最终授权之前还是之后？
```

回答清楚这三个问题，Filter 的职责和插入位置通常就确定了。

---

## 参考资料

- [Spring Security Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Servlet Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [Persisting Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)
- [Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)
- [HttpSecurity API](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/config/annotation/web/builders/HttpSecurity.html)
