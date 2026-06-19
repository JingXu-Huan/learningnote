# JWT 集成 Spring Security（完整实战）

## 整体流程

```
登录请求 POST /login
    ↓
LoginController 接收 username + password
    ↓
AuthenticationManager.authenticate() 校验密码
    ↓
校验通过 → 生成 JWT → 返回给前端

后续请求 GET /api/xxx  (Header: Authorization: Bearer xxx)
    ↓
JwtAuthenticationFilter（自定义过滤器）拦截请求
    ↓
从 Header 取出 Token → 解析出 username
    ↓
查数据库拿到用户信息和权限
    ↓
构建 Authentication 对象，放进 SecurityContext
    ↓
后续过滤器和 Controller 就能知道"当前用户是谁"了
```

------

## 1. JwtAuthenticationFilter（核心：自定义过滤器）

> 这是整个集成的核心。它的职责：**从请求头取出 JWT，解析用户信息，告诉 Spring Security "当前请求是哪个用户发的"。**

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 从请求头取出 Authorization
        String authHeader = request.getHeader("Authorization");

        // 2. 如果没有 Token 或格式不对，直接放行（后面的过滤器会处理未认证的情况）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 取出 Token（去掉 "Bearer " 前缀，注意有空格）
        String token = authHeader.substring(7);

        try {
            // 4. 解析 Token，取出用户名
            String username = jwtUtil.getUsername(token);

            // 5. 如果用户名有效，且当前还没有认证信息
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 6. 从数据库查询用户详情（包含权限）
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 7. 构建认证对象
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,        // 用户详情（包含权限列表）
                        null,               // 密码（已经认证过了，不需要）
                        userDetails.getAuthorities()  // 权限列表
                    );

                // 8. 放进 SecurityContext（后续流程就能知道当前用户是谁了）
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (ExpiredJwtException e) {
            // Token 过期，不放认证信息，后面的过滤器会返回 401
        } catch (JwtException e) {
            // Token 无效（签名错误、格式错误等）
        }

        // 9. 继续过滤器链
        filterChain.doFilter(request, response);
    }
}
```

### 逐行理解这个过滤器

```java
// 为什么继承 OncePerRequestFilter 而不是 Filter？
// OncePerRequestFilter 保证每个请求只执行一次，避免重复过滤
```

```java
// 为什么要判断 SecurityContextHolder.getContext().getAuthentication() == null？
// 防止重复设置。如果已经认证过了（比如通过其他方式），就不需要再设置一次
```

```java
// 为什么 Token 无效时不直接返回 401？
// 职责分离。这个过滤器只负责"解析 Token 并设置认证信息"。
// 如果 Token 无效，认证信息就是 null，后面的 FilterSecurityInterceptor 会判断：
// "这个接口需要认证，但你没有认证信息" → 返回 401
```

------

## 2. SecurityConfig（注册过滤器）

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/refresh").permitAll()
                .requestMatchers("/admin/**").hasAuthority("sys:admin:manage")
                .anyRequest().authenticated()
            )
            // ★ 关键：把 JWT 过滤器加到 UsernamePasswordAuthenticationFilter 之前
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 配置异常处理（返回 JSON 而不是跳转页面）
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // 401：未认证（没带 Token 或 Token 无效）
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(401);
                    response.getWriter().write("{\"code\":401,\"msg\":\"未登录或Token已过期\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // 403：已认证但权限不足
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(403);
                    response.getWriter().write("{\"code\":403,\"msg\":\"权限不足\"}");
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### addFilterBefore 是什么意思

```
原本的过滤器链：
UsernamePasswordAuthenticationFilter → ... → FilterSecurityInterceptor

加入 JWT 过滤器后：
JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ... → FilterSecurityInterceptor
```

> 为什么放在 `UsernamePasswordAuthenticationFilter` 之前？因为 JWT 过滤器要先解析 Token 并设置认证信息，这样后面的过滤器才知道当前用户是谁。

------

## 3. LoginController（登录接口）

```java
@RestController
public class LoginController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO) {

        // 1. 把用户名密码包装成 Token
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword());

        try {
            // 2. 认证（Spring Security 自动调用 UserDetailsService + PasswordEncoder）
            Authentication authenticate = authenticationManager.authenticate(authToken);

            // 3. 认证通过，取出用户信息和权限
            LoginUser loginUser = (LoginUser) authenticate.getPrincipal();
            List<String> permissions = loginUser.getPermissions();

            // 4. 生成 JWT
            String token = jwtUtil.generateToken(loginUser.getUsername(), permissions);

            // 5. 返回给前端
            return Result.success(token);

        } catch (BadCredentialsException e) {
            return Result.error("用户名或密码错误");
        }
    }
}
```

### 登录流程走了一遍什么

```
前端 POST /login { username: "Tom", password: "123" }
    ↓
LoginController 收到
    ↓
authenticationManager.authenticate()
    ↓
    内部调用 UserDetailsService.loadUserByUsername("Tom")
    → 返回 LoginUser（包含数据库里的加密密码和权限）
    ↓
    内部调用 PasswordEncoder.matches("123", 数据库加密密码)
    ↓
    ✅ 通过 → 返回完整的 Authentication 对象
    ↓
从 Authentication 取出 LoginUser → 生成 JWT → 返回
```

------

## 4. Controller 中获取当前用户

```java
@RestController
@RequestMapping("/api")
public class UserController {

    // 方式一：用 @AuthenticationPrincipal 注解直接拿 LoginUser
    @GetMapping("/me")
    public Result getCurrentUser(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(loginUser.getUser());
    }

    // 方式二：从 SecurityContext 手动取
    @GetMapping("/me2")
    public Result getCurrentUser2() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        return Result.success(loginUser.getUser());
    }
}
```

------

## 5. 方法级权限控制

```java
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('sys:admin:manage')")  // 整个 Controller 需要管理员权限
public class AdminController {

    @GetMapping("/users")
    public Result listUsers() {
        // 只有拥有 sys:admin:manage 权限的用户才能访问
        return Result.success(userService.listAll());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('sys:user:delete')")  // 方法级覆盖类级注解
    public Result deleteUser(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success();
    }
}
```

### 权限表达式速查

| 表达式 | 含义 |
|--------|------|
| `hasAuthority('sys:user:add')` | 是否有某个权限（推荐） |
| `hasRole('ADMIN')` | 是否有某个角色（自动加 `ROLE_` 前缀） |
| `hasAnyAuthority('sys:user:add', 'sys:user:edit')` | 是否有任一权限 |
| `hasAnyRole('ADMIN', 'MANAGER')` | 是否有任一角色 |
| `@PreAuthorize("#id == authentication.principal.user.id")` | 只能操作自己的数据 |

------

## 6. 完整项目结构参考

```
src/main/java/com/example/
├── config/
│   └── SecurityConfig.java           // Security 配置
├── filter/
│   └── JwtAuthenticationFilter.java  // JWT 过滤器（核心）
├── util/
│   └── JwtUtil.java                  // JWT 工具类
├── domain/
│   ├── LoginUser.java                // 实现 UserDetails
│   └── LoginDTO.java                 // 登录请求参数
├── service/
│   └── UserDetailsServiceImpl.java   // 实现 UserDetailsService
├── controller/
│   ├── LoginController.java          // 登录接口
│   └── UserController.java           // 业务接口
└── mapper/
    └── UserMapper.java               // 查数据库
```

------

## 7. 测试接口

### 登录

```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"Tom","password":"123456"}'

# 返回
{"code":200,"data":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJUb20iLCJhdXRo..."}
```

### 带 Token 访问接口

```bash
curl http://localhost:8080/api/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJUb20i..."

# 返回
{"code":200,"data":{"id":1,"username":"Tom","email":"tom@example.com"}}
```

### 不带 Token 访问受保护接口

```bash
curl http://localhost:8080/api/me

# 返回 401
{"code":401,"msg":"未登录或Token已过期"}
```

### 权限不足

```bash
curl http://localhost:8080/admin/users \
  -H "Authorization: Bearer eyJhbGci...(普通用户Token)"

# 返回 403
{"code":403,"msg":"权限不足"}
```

---

## 🔗 相关笔记

- [[JWT详解]] —— JWT 结构、签名、密钥管理详解
- [[SpringSecurity概述]] —— Spring Security 过滤器链与 UserDetailsService
- [[../SpringBoot/AOP]] —— JWT 过滤器本质上是 AOP 的另一种体现
- [[../../../实习方法论/SpringBoot/SPEL表达式]] —— @PreAuthorize 中使用的 SpEL 表达式
