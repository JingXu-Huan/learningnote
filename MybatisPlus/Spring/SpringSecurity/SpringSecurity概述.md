# Spring Security 概述

## 1. 它是什么

Spring Security 是 Spring 生态的安全框架，核心解决两个问题：

| 问题 | 术语 | 通俗理解 |
|------|------|----------|
| 你是谁？ | **认证（Authentication）** | 登录：验证用户名密码是否正确 |
| 你能干什么？ | **授权（Authorization）** | 权限：登录用户能不能访问某个接口 |

------

## 2. 核心架构：过滤器链

Spring Security 本质就是一组 **Servlet 过滤器**，请求到达 Controller 之前，要经过层层检查：

```
客户端请求
    ↓
UsernamePasswordAuthenticationFilter   ← 处理登录请求（/login）
    ↓
BasicAuthenticationFilter              ← 处理 Basic 认证
    ↓
JwtAuthenticationFilter（自定义）       ← 解析 JWT Token（前后端分离项目）
    ↓
ExceptionTranslationFilter             ← 捕获认证/授权异常，返回 401/403
    ↓
FilterSecurityInterceptor              ← 最终权限校验（有没有角色/权限访问）
    ↓
Controller
```

> **理解要点**：每个过滤器各司其职，一个负责登录、一个负责权限检查、一个负责异常处理。我们可以插入自定义过滤器（比如 JWT 过滤器）来扩展功能。

------

## 3. 核心接口速查

### 认证相关

| 接口 | 作用 | 你需要做什么 |
|------|------|-------------|
| `UserDetailsService` | 从数据库加载用户信息 | **必须实现**：写一个方法根据用户名查数据库 |
| `UserDetails` | 封装用户信息（用户名、密码、权限列表） | **必须实现**：返回你的用户实体 |
| `PasswordEncoder` | 密码加密/校验 | 注入 Bean：通常用 `BCryptPasswordEncoder` |
| `AuthenticationManager` | 认证入口，调用 authenticate() | 一般不用手动调用，框架自动处理 |

### 授权相关

| 接口/注解 | 作用 |
|-----------|------|
| `@PreAuthorize("hasRole('ADMIN')")` | 方法级权限控制（推荐） |
| `@Secured("ROLE_ADMIN")` | 旧版方法级权限（不推荐） |
| `hasAuthority("sys:user:add")` | 基于权限字符串判断（更细粒度） |
| `hasRole("ADMIN")` | 基于角色判断（自动加 `ROLE_` 前缀） |

> **Role vs Authority 的区别**：
> - Role（角色）：`ADMIN`，框架自动变成 `ROLE_ADMIN`
> - Authority（权限）：`sys:user:add`，不加前缀，更细粒度
> - 实际项目中推荐用 Authority，灵活度更高

------

## 4. 最小配置（Spring Boot 3.x / Spring Security 6.x）

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // 开启 @PreAuthorize 注解
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF（前后端分离项目不需要，因为不用 Session）
            .csrf(csrf -> csrf.disable())
            // 关闭 Session（无状态，用 JWT 代替）
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求的访问规则
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register").permitAll()  // 放行登录注册
                .requestMatchers("/admin/**").hasRole("ADMIN")       // 管理员接口
                .anyRequest().authenticated()                        // 其余都要认证
            );
        return http.build();
    }

    // 密码加密器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 逐行解释

```java
.csrf(csrf -> csrf.disable())
```
CSRF 是防止跨站请求伪造的，依赖 Session。前后端分离用 JWT，没有 Session，所以关掉。

```java
.sessionManagement(session -> 
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
告诉 Spring Security 不要创建 Session，每次请求都靠 JWT 来识别用户。

```java
.requestMatchers("/login", "/register").permitAll()
```
这两个接口不需要认证，任何人都能访问（登录和注册嘛）。

```java
.anyRequest().authenticated()
```
除此之外的所有接口，必须携带有效的 Token 才能访问。

------

## 5. 实现 UserDetailsService（从数据库查用户）

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 从数据库查用户
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 2. 查该用户的权限列表
        List<String> permissions = userMapper.findPermissionsByUserId(user.getId());

        // 3. 封装成 Spring Security 认识的 UserDetails
        return new LoginUser(user, permissions);
    }
}
```

```java
// LoginUser：我们自己封装的用户信息，实现 UserDetails 接口
@Data
public class LoginUser implements UserDetails {

    private User user;
    private List<String> permissions;

    public LoginUser(User user, List<String> permissions) {
        this.user = user;
        this.permissions = permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 把权限字符串列表转成 GrantedAuthority 对象列表
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() { return user.getPassword(); }

    @Override
    public String getUsername() { return user.getUsername(); }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
```

> **为什么要封装 LoginUser？** 因为 Spring Security 只认 `UserDetails` 接口，你的 `User` 实体它不认识。`LoginUser` 就是两者之间的桥梁。

------

## 6. 认证流程（登录请求走了一遍什么）

```
POST /login  { username: "Tom", password: "123" }
    ↓
UsernamePasswordAuthenticationFilter 拦截
    ↓
把 username + password 包装成 UsernamePasswordAuthenticationToken
    ↓
调用 AuthenticationManager.authenticate(token)
    ↓
AuthenticationManager 找到 DaoAuthenticationProvider
    ↓
DaoAuthenticationProvider 调用 UserDetailsService.loadUserByUsername("Tom")
    ↓
拿到 UserDetails（包含数据库里存的加密密码）
    ↓
用 PasswordEncoder.matches("123", 数据库里的加密密码) 校验
    ↓
✅ 通过 → 返回 Authentication 对象（后续可用）
❌ 失败 → 抛出 BadCredentialsException
```

> **关键理解**：Spring Security 帮你做了"查用户 + 比对密码"这两步，你只需要提供 `UserDetailsService` 和 `PasswordEncoder`。

---

## 🔗 相关笔记

- [[JWT详解]] —— JWT 详解（Token 结构、签名、刷新）
- [[JWT集成SpringSecurity]] —— JWT + Spring Security 完整集成
- [[../../../计算机网络/计算机网络知识总结]] —— HTTP 认证机制、HTTPS/TLS 基础
- [[../../../实习方法论/SpringBoot/SPEL表达式]] —— @PreAuthorize 中使用的 SpEL 表达式
