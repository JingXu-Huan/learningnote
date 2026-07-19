# Spring Security 权限控制详解 😎😎😎

## 目录

- [一、先分清：认证与授权](#一先分清认证与授权)
- [二、一次请求如何完成权限判断](#二一次请求如何完成权限判断)
- [三、权限能精细到什么程度](#三权限能精细到什么程度)
- [四、RBAC：后台系统最常用的模型](#四rbac后台系统最常用的模型)
- [五、数据库表设计](#五数据库表设计)
- [六、把权限装入 Spring Security](#六把权限装入-spring-security)
- [七、URL 级权限控制](#七url-级权限控制)
- [八、方法级权限控制](#八方法级权限控制)
- [九、数据级权限控制](#九数据级权限控制)
- [十、前端按钮权限与后端权限](#十前端按钮权限与后端权限)
- [十一、JWT 项目中的权限更新](#十一jwt-项目中的权限更新)
- [十二、常见坑与最佳实践](#十二常见坑与最佳实践)

------

## 一、先分清：认证与授权

权限控制经常和登录混在一起说，但它们是两个阶段：

| 概念 | 英文 | 要回答的问题 | 例子 |
| --- | --- | --- | --- |
| 认证 | Authentication | “你是谁？” | 用户名密码校验、解析 JWT |
| 授权 | Authorization | “你能做什么？” | 是否能删除用户、能查看哪些订单 |

用户登录成功后，Spring Security 会得到一个 `Authentication` 对象。它至少包含：

```text
当前用户（principal） + 身份状态（authenticated） + 权限集合（authorities）
```

例如管理员登录后可拥有：

```text
ROLE_ADMIN
sys:user:list
sys:user:add
sys:user:delete
```

之后每次访问受保护资源，Spring Security 都会依据这些 `authorities` 做授权判断。

------

## 二、一次请求如何完成权限判断

以“携带 JWT 请求删除用户”为例：

```text
浏览器/前端
    │ Authorization: Bearer <token>
    ▼
Spring Security 过滤器链
    │
    ├─ 1. JWT 过滤器：校验签名、过期时间，解析用户身份
    ├─ 2. 查询或读取用户权限，构造 Authentication
    ├─ 3. 放入 SecurityContextHolder
    ├─ 4. AuthorizationFilter：匹配 URL 规则并判断权限
    ▼
Controller → Service（可继续执行方法级、数据级校验）
```

如果未登录，通常返回 **401 Unauthorized**；已登录但没有权限，通常返回 **403 Forbidden**。

> 注意：401 表示“尚未完成身份认证”，403 表示“身份已知，但不允许访问”。

Spring Security 的请求授权由 `AuthorizationFilter` 和 `AuthorizationManager` 协作完成。规则按声明顺序匹配，**第一条命中的规则生效**，因此更具体的规则要放在前面。

------

## 三、权限能精细到什么程度

Spring Security 不限制权限模型；从粗到细通常可以做到下面这些层次。

| 粒度 | 示例 | 常用方式 | 适合场景 |
| --- | --- | --- | --- |
| 应用/模块 | 是否能进入“系统管理” | 菜单权限、角色 | 前端路由、模块入口 |
| URL | `GET /api/users/**` | `requestMatchers` | 接口统一拦截 |
| HTTP 方法 | `GET /api/users` 与 `DELETE /api/users/{id}` | 方法 + 路径匹配 | RESTful API |
| Controller / Service 方法 | `deleteUser()` | `@PreAuthorize` | 关键业务操作 |
| 按钮/操作 | 新增、删除、导出、审核 | 权限标识 | 管理后台 |
| 单条数据 | 只能修改自己创建的订单 | 自定义表达式/业务校验 | 订单、文章、工单 |
| 数据范围 | 仅本人、本部门、下级部门、全部 | SQL 条件 + 数据权限 | 多部门管理系统 |
| 字段 | 手机号仅本人和管理员可见 | DTO 脱敏、字段过滤 | 隐私数据、财务数据 |
| 租户 | 只能访问本租户的数据 | tenantId 条件 | SaaS 多租户 |

因此“权限精细到哪个部分”没有固定上限。Spring Security 擅长处理 URL 和方法授权；单条数据、部门范围、字段范围则通常要与业务层、ORM/SQL 查询一起实现。

------

## 四、RBAC：后台系统最常用的模型

### 1. 什么是 RBAC

RBAC（Role-Based Access Control，基于角色的访问控制）将“用户”和“具体权限”解耦：

```text
用户 ──多对多── 角色 ──多对多── 权限
```

例如：

```text
张三 → 运营人员 → 订单查看、订单导出
李四 → 系统管理员 → 用户管理、角色管理、权限管理
```

这比直接给每个用户逐项授权更易维护。新增员工时只需分配角色；调整某类员工的能力时只需修改角色关联的权限。

### 2. 角色与权限的命名

推荐把角色和权限分开命名：

```text
角色：ROLE_ADMIN、ROLE_OPERATOR
权限：sys:user:list、sys:user:add、sys:user:delete、order:export
```

在 Spring Security 中：

- `hasAuthority("sys:user:add")`：精确判断某个权限字符串；
- `hasRole("ADMIN")`：框架会按默认规则查找 `ROLE_ADMIN`。

所以调用 `hasRole("ADMIN")` 时，数据库/权限集合中通常应存 `ROLE_ADMIN`；不要写成 `hasRole("ROLE_ADMIN")`，否则可能被重复加前缀。

### 3. 为什么不要只用角色

小项目只有“管理员、普通用户”时，角色足够。但后台系统往往有“查看、新增、编辑、删除、导出、审核”等差异；把所有组合都做成角色会导致角色爆炸。

更实用的方案是：**角色负责人员分组，权限标识负责具体操作。**

------

## 五、数据库表设计

一套典型 RBAC 表结构如下：

```text
sys_user                 用户
sys_role                 角色
sys_permission           权限 / 菜单 / 按钮
sys_user_role            用户-角色关联表
sys_role_permission      角色-权限关联表
```

示例字段：

| 表 | 核心字段 | 说明 |
| --- | --- | --- |
| `sys_user` | `id`、`username`、`password`、`status` | 密码存 BCrypt 哈希，不能明文存储 |
| `sys_role` | `id`、`role_key`、`role_name` | 如 `ROLE_ADMIN` |
| `sys_permission` | `id`、`permission_key`、`type`、`path` | 如 `sys:user:add`，`type` 可为菜单/按钮/API |
| `sys_user_role` | `user_id`、`role_id` | 用户可有多个角色 |
| `sys_role_permission` | `role_id`、`permission_id` | 一个角色可含多个权限 |

查询用户所有权限时，通常通过用户、角色、权限表的关联查询得到并去重：

```sql
SELECT DISTINCT p.permission_key
FROM sys_user_role ur
JOIN sys_role_permission rp ON ur.role_id = rp.role_id
JOIN sys_permission p ON rp.permission_id = p.permission_id
WHERE ur.user_id = #{userId}
  AND p.status = 1;
```

若前端需要菜单树，可以在 `sys_permission` 中额外维护 `parent_id`、`sort`、`component`、`visible` 等字段；但“菜单是否显示”不是后端接口鉴权的替代品。

------

## 六、把权限装入 Spring Security

### 1. `GrantedAuthority`

Spring Security 将角色和操作权限统一抽象为 `GrantedAuthority`。最常见的实现类是 `SimpleGrantedAuthority`：

```java
List<String> permissionCodes = permissionService.listCodesByUserId(userId);

List<GrantedAuthority> authorities = permissionCodes.stream()
        .map(SimpleGrantedAuthority::new)
        .toList();
```

如果同时需要角色和细粒度操作权限，可以全部放入：

```java
List<GrantedAuthority> authorities = List.of(
        new SimpleGrantedAuthority("ROLE_ADMIN"),
        new SimpleGrantedAuthority("sys:user:list"),
        new SimpleGrantedAuthority("sys:user:delete")
);
```

### 2. `UserDetailsService` 的职责

在账号密码登录中，`UserDetailsService` 负责根据用户名查询用户，并返回带权限的 `UserDetails`：

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) {
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<GrantedAuthority> authorities = permissionService
                .listCodesByUserId(user.getId())
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
```

认证成功后，权限会被放入 `Authentication`，后续 URL 和方法授权就能直接读取。

------

## 七、URL 级权限控制

URL 权限适合做接口的第一道防线。Spring Security 6+ 推荐通过 `SecurityFilterChain` 配置：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 前后端分离的无状态 JWT API 常见配置
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/**")
                            .hasAuthority("sys:user:list")
                        .requestMatchers(HttpMethod.POST, "/api/users")
                            .hasAuthority("sys:user:add")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**")
                            .hasAuthority("sys:user:delete")
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
```

这里已经可以精确到“某个 HTTP 方法 + 某个 URL”：

```text
GET    /api/users/**  → sys:user:list
POST   /api/users     → sys:user:add
DELETE /api/users/**  → sys:user:delete
```

### URL 配置的注意点

1. **规则有顺序**：把 `/api/users/**` 的通用规则写在前面，可能会抢先匹配，导致后面的删除规则失效。
2. **`permitAll()` 也要谨慎**：登录、验证码、公开文档可以放行；管理接口不要因调试方便而放行。
3. **不能只依赖 URL 规则**：同一个 Service 方法可能被定时任务、消息消费者、其他 Controller 调用，关键业务规则还应放在方法或业务层。

------

## 八、方法级权限控制

### 1. 开启方法安全

```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
```

### 2. 常用注解

```java
@PreAuthorize("hasAuthority('sys:user:list')")
public List<UserVO> listUsers() {
    return userService.list();
}

@PreAuthorize("hasAuthority('sys:user:delete')")
public void deleteUser(Long id) {
    userService.removeById(id);
}

@PreAuthorize("hasRole('ADMIN')")
public void rebuildSearchIndex() {
    // 仅管理员可执行
}
```

`@PreAuthorize` 在方法执行**前**校验，是最常用的注解。除此之外还有：

| 注解 | 触发时机 | 用途 |
| --- | --- | --- |
| `@PreAuthorize` | 方法前 | 无权限则不执行方法 |
| `@PostAuthorize` | 方法返回后 | 可依据返回对象再决定是否允许返回 |
| `@PreFilter` | 调用方法前 | 过滤集合入参 |
| `@PostFilter` | 方法返回后 | 过滤集合返回值 |

### 3. 从 Controller 到 Service 的建议

- URL 权限：放在 `SecurityFilterChain`，统一保护接口边界；
- 关键业务权限：放在 Service 的 `@PreAuthorize`，避免绕过 Controller 后失去保护；
- 数据范围：放在 Service 或 Mapper 查询条件中。

这样即使同一业务被 REST 接口、定时任务、RPC 调用等不同入口复用，也更容易保持安全边界。

------

## 九、数据级权限控制

操作权限只能解决“能不能删除订单”；数据权限解决的是“能删除哪一张订单”。

### 1. 单条数据：是否是资源拥有者

例如，普通用户只能删除自己创建的文章，管理员可以删除任意文章。

先定义一个供 SpEL 调用的 Bean：

```java
@Component("articlePermission")
public class ArticlePermission {

    public boolean canDelete(Authentication authentication, Long articleId) {
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        return articleService.isOwner(articleId, loginUser.getUserId());
    }
}
```

然后在 Service 方法上组合“操作权限 + 资源归属”判断：

```java
@PreAuthorize("hasAuthority('article:delete') " +
        "and @articlePermission.canDelete(authentication, #articleId)")
public void deleteArticle(Long articleId) {
    articleMapper.deleteById(articleId);
}
```

### 2. 数据范围：本人、本部门、下级部门、全部

后台管理系统常见的数据范围：

| 数据范围 | 查询条件示意 |
| --- | --- |
| 仅本人 | `create_by = 当前用户ID` |
| 本部门 | `dept_id = 当前部门ID` |
| 本部门及下级 | `dept_id IN (部门树中的所有下级部门)` |
| 自定义部门 | `dept_id IN (角色配置的部门集合)` |
| 全部数据 | 不追加范围条件 |

这类控制一般不能只用注解完成，最终必须落实到查询 SQL 中。例如查询订单时：

```sql
SELECT *
FROM orders
WHERE deleted = 0
  AND tenant_id = #{tenantId}
  AND dept_id IN
      <foreach collection="allowedDeptIds" item="deptId" open="(" separator="," close=")">
          #{deptId}
      </foreach>;
```

> 数据范围必须在后端查询条件中强制生效，不能只由前端传一个 `deptId` 决定，否则用户可以篡改请求参数越权查询。

### 3. 字段级权限

字段权限适合处理手机号、身份证号、薪资等敏感数据。常见策略：

- 无权限时直接不返回该字段；
- 返回脱敏值，例如 `138****1234`；
- 管理员可见完整值，普通用户仅能见自己的完整值；
- 导出接口单独校验 `xxx:export-sensitive` 权限。

实现位置通常是 VO/DTO 转换层或序列化层。不要只在前端把字段隐藏，因为接口响应中的原始数据仍可能泄露。

------

## 十、前端按钮权限与后端权限

前端可以根据权限集合隐藏按钮：

```text
拥有 sys:user:delete → 显示“删除”按钮
没有该权限          → 隐藏或禁用“删除”按钮
```

这能改善体验，但它不是安全控制。用户仍可以通过浏览器开发者工具、Postman 或脚本直接请求删除接口。

正确分工是：

| 层级 | 作用 |
| --- | --- |
| 前端路由/菜单 | 控制页面入口和展示体验 |
| 前端按钮 | 控制操作入口和交互提示 |
| Spring Security URL/方法校验 | 阻止未授权请求 |
| Service/SQL 数据权限 | 阻止跨用户、跨部门、跨租户越权 |

后端授权才是最终裁决者。

------

## 十一、JWT 项目中的权限更新

JWT 常把用户 ID、用户名、角色或权限写进 Token。这样每次请求不必查询数据库，但会产生一个问题：**管理员刚修改了用户权限，旧 Token 里的权限不会自动改变。**

常见方案：

| 方案 | 思路 | 优缺点 |
| --- | --- | --- |
| Token 中直接存权限 | 请求无需查库 | 修改权限后需等待 Token 过期或强制失效 |
| Token 只存用户 ID | 每次从 Redis/数据库加载权限 | 权限实时性较好，但增加查询开销 |
| 权限版本号 | Token 带版本，服务端版本不同即失效 | 兼顾性能与主动失效能力 |
| 黑名单/会话表 | 注销、踢人或改权后使 Token 失效 | 需要额外存储与清理策略 |

实际项目常用“短期 Access Token + 可续期 Refresh Token”，同时把用户权限或会话版本放入 Redis 缓存。管理员修改角色、禁用账号、踢人时，清理缓存或递增版本号，即可让旧 Token 失效。

------

## 十二、常见坑与最佳实践

### 1. 密码绝不能明文保存

使用 `BCryptPasswordEncoder`：

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

注册时 `encode`，登录校验时用 `matches`；不要自己用 MD5/SHA-1 直接处理密码。

### 2. 不要相信前端传来的身份和权限

后端应从 `SecurityContextHolder` 取当前用户，而非相信请求中的 `userId`、`role`、`isAdmin`：

```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
```

### 3. 统一权限编码规范

推荐使用 `模块:资源:动作`：

```text
sys:user:list
sys:user:add
sys:user:update
sys:user:delete
order:order:export
```

编码一旦发布，应尽量稳定；重命名权限编码会影响数据库、前端按钮、注解和测试。

### 4. 防止水平越权和垂直越权

- **水平越权**：普通用户 A 修改用户 B 的订单；靠资源归属、数据范围校验防御。
- **垂直越权**：普通用户调用管理员接口；靠 URL/方法权限防御。

### 5. 审计关键操作

对登录、授权失败、删除、导出、审批、角色变更等操作记录审计日志，至少包含操作者、时间、接口、目标对象、结果和失败原因。

### 6. 为权限规则写测试

```java
@Test
@WithMockUser(authorities = "sys:user:delete")
void deleteUserWithPermissionShouldSucceed() throws Exception {
    mockMvc.perform(delete("/api/users/1"))
            .andExpect(status().isOk());
}

@Test
@WithMockUser(authorities = "sys:user:list")
void deleteUserWithoutPermissionShouldBeForbidden() throws Exception {
    mockMvc.perform(delete("/api/users/1"))
            .andExpect(status().isForbidden());
}
```

------

## 总结

Spring Security 的权限控制，本质是把当前用户的角色与权限放进 `Authentication`，再在请求、方法和业务数据三个层面逐层校验：

```text
URL 权限：能不能访问这个接口？
方法权限：能不能执行这项业务操作？
数据权限：能操作哪些数据、能看哪些字段？
```

对于常规后台系统，推荐采用 **RBAC（用户—角色—权限）+ URL 拦截 + `@PreAuthorize` + SQL 数据范围** 的组合。这样既能实现菜单、按钮、接口权限，也能覆盖本人/部门/租户/字段等更精细的权限控制。

## 参考资料

- [Spring Security 官方文档：Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Spring Security 官方文档：Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
