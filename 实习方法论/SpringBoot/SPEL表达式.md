Spring 里**常见且支持 SpEL 的注解**主要集中在这几类：安全、缓存、依赖注入、条件装配。

**一、几个常用注解**

1. `@PreAuthorize`
   用于**方法执行前**做权限判断。

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("@orderAuthService.canView(#orderId)")
public OrderDetail getDetail(Long orderId) { ... }
```

为什么可以这样写：

- 因为 Spring Security 在执行方法前，会专门解析 `@PreAuthorize` 里的字符串
- 这不是普通字符串，而是“安全表达式”

2. `@PostAuthorize`
   用于**方法执行后**再判断，适合依赖返回值的场景。

```java
@PostAuthorize("returnObject.ownerId == authentication.name")
public OrderDetail getDetail(Long orderId) { ... }
```

为什么可以这样写：

- 方法已经执行完了，所以表达式里能拿到返回值 `returnObject`

3. `@Cacheable`
   用于缓存结果，最常见的是 `key`、`condition`、`unless` 支持 SpEL。

```java
@Cacheable(value = "user", key = "#userId")
public User getUser(Long userId) { ... }

@Cacheable(value = "user", key = "#user.id", unless = "#result == null")
public User save(User user) { ... }
```

为什么可以这样写：

- Spring Cache 在执行方法时，会先构造“方法参数上下文”
- 然后解析 `key`、`condition`、`unless` 表达式

4. `@CacheEvict`
   用于删缓存，也常用 SpEL 指定 key。

```java
@CacheEvict(value = "user", key = "#userId")
public void deleteUser(Long userId) { ... }
```

5. `@CachePut`
   用于强制更新缓存。

```java
@CachePut(value = "user", key = "#result.id")
public User updateUser(User user) { ... }
```

为什么可以这样写：

- 因为它和 `@Cacheable` 一样，底层都支持表达式求值

6. `@Value`
   用于字段注入或配置取值。

```java
@Value("#{systemProperties['user.home']}")
private String userHome;

@Value("#{2 * 3}")
private int size;
```

为什么可以这样写：

- Spring 在创建 Bean、注入属性时，会解析 `#{...}` 里的表达式

7. `@EventListener`
   它的 `condition` 也支持 SpEL。

```java
@EventListener(condition = "#event.success == true")
public void handle(OrderEvent event) { ... }
```

为什么可以这样写：

- Spring 事件机制会在分发事件时，对条件表达式做判断

8. `@Scheduled`
   严格说它常见的是占位符 `${...}`，**不是标准 SpEL 主场景**。
   所以别把所有能写动态值的注解都当成 SpEL 注解。

---

**二、这些注解为什么能支持 SpEL**

核心原因只有一句：

**这些注解背后的 Spring 组件，在运行时会主动把注解里的字符串交给表达式解析器处理。**

也就是它们不是“天然支持”，而是框架作者在实现时，专门写了类似这样的逻辑：

- 读取注解属性
- 创建表达式上下文
- 用 `ExpressionParser` 解析字符串
- 执行表达式并拿结果

所以：

- 支持 SpEL，不是因为“这是注解”
- 而是因为“这个注解的实现者，选择把它当表达式解析”

---

**三、SpEL 的常见语法**

1. 字面量

```java
'hello'
123
true
null
```

2. 访问方法参数

```java
#userId
#order.id
#p0
#a0
```

说明：

- `#userId`：按参数名取值
- `#p0` / `#a0`：第 1 个参数
- `#order.id`：取对象属性

3. 调用 Spring Bean

```java
@orderAuthService.canView(#orderId)
@ss.hasPermi('system:user:list')
```

说明：

- `@beanName` 表示从 Spring 容器取 Bean

4. 访问返回值

```java
#result
returnObject
```

常见于：

- `@Cacheable(unless = "#result == null")`
- `@PostAuthorize("returnObject != null")`

5. 访问当前认证对象

```java
authentication
principal
```

常见于 Spring Security：

```java
@PreAuthorize("authentication != null")
@PreAuthorize("principal.username == #username")
```

6. 逻辑运算

```java
and
or
not
```

例如：

```java
@PreAuthorize("hasRole('ADMIN') and @shopAuth.canAccess(#shopId)")
```

7. 比较运算

```java
== != > < >= <=
```

例如：

```java
@PreAuthorize("#userId == principal.userId")
```

8. 调用静态方法

```java
T(java.lang.Math).max(1, 2)
T(java.util.UUID).randomUUID().toString()
```

说明：

- `T(类名)` 用来引用类

9. 三元表达式

```java
#user != null ? #user.id : 0
```

10. 集合判断

```java
#ids.contains(#userId)
```

---

**四、几个最常见的例子**

权限判断：

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("@permService.hasPermi('order:query')")
@PreAuthorize("@authService.canRead(#id)")
```

缓存：

```java
@Cacheable(value = "order", key = "#id")
@CacheEvict(value = "order", key = "#id")
@Cacheable(value = "order", key = "#id", unless = "#result == null")
```

配置注入：

```java
@Value("#{systemProperties['java.io.tmpdir']}")
@Value("#{8 * 60}")
```

事件监听：

```java
@EventListener(condition = "#event.type == 'CREATE'")
```

---

**五、什么时候适合用 SpEL**

适合：

- 权限表达式
- 缓存 key
- 简单条件判断
- 轻量级 Bean 调用

不适合：

- 超长复杂业务规则
- 多次数据库查询
- 跨多个远程服务编排
- 难以测试和排查的逻辑

经验上可以这么记：

**SpEL 适合“声明规则”，不适合“承载复杂业务”。**

---

**六、一句话总结**

常用支持 SpEL 的注解有 `@PreAuthorize`、`@PostAuthorize`、`@Cacheable`、`@CacheEvict`、`@CachePut`、`@Value`、`@EventListener(condition=...)`。
它们之所以能这样写，是因为 Spring 在这些注解的实现里，专门把注解属性当表达式解析了。
SpEL 常用语法就是：`#参数`、`@Bean`、`#result`、`T(类名)`、逻辑/比较/三元运算。

如果你愿意，我可以下一步直接给你整理一版”SpEL 语法速查表.md”。

---

## 🔗 相关笔记

- [[../../MybatisPlus/Spring/SpringSecurity/SpringSecurity概述]] —— @PreAuthorize 中使用的 SpEL 表达式
- [[../../MybatisPlus/Spring/SpringSecurity/JWT集成SpringSecurity]] —— JWT + Spring Security 中的权限表达式
- [[../../MybatisPlus/Spring/SpringBoot/AOP]] —— SpEL 与 AOP 注解的配合使用
