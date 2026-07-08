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

**六、开发经验**

1. 先分清它是不是 SpEL

开发里最容易混淆的是：

- `#{...}` 一般是 SpEL
- `${...}` 一般是配置占位符
- 注解里写的 `"hasRole('ADMIN')"`、`"#id"`、`"@bean.xxx(#p0)"`，虽然外面没有 `#{}`，本质上也是框架在按表达式解析

排查前先把这个边界分清，不然很容易找错方向。

2. 不要想当然地认为“注解天然支持表达式”

实战里遇到一个注解能不能写 SpEL，不要靠猜。
最稳的方法是直接看它背后的实现有没有这几个东西：

- `ExpressionParser`
- `EvaluationContext`
- `MethodBasedEvaluationContext`
- 某种 `ExpressionHandler` / `ExpressionEvaluator`

如果有，基本就是“框架显式支持表达式”；
如果没有，通常就只是普通字符串。

3. 查问题时，优先顺着“注解 -> 拦截器 -> 表达式解析器”往下看

真实项目里，SpEL 出问题一般不是语法本身，而是上下文不对。
推荐排查顺序：

1. 先看这个注解最终由谁处理
2. 再看它往表达式上下文里塞了哪些变量
3. 最后确认表达式里引用的 `#参数`、`#result`、`authentication`、`@bean` 是否真的存在

比如：

- `@PreAuthorize` 重点看安全上下文
- `@Cacheable` 重点看方法参数和返回值
- `@EventListener(condition = ...)` 重点看事件对象有没有按你以为的名字暴露出来

4. 参数名不稳定时，优先用 `#p0` / `#a0`

很多人写 `#userId`，本地能跑，换环境就失效。
原因往往不是 SpEL 错了，而是编译参数、代理方式、参数名保留策略不同，导致运行时拿不到真实参数名。

所以经验上：

- 能稳定拿到参数名时，用 `#userId` 可读性更好
- 对稳定性要求更高时，用 `#p0`、`#p1`、`#a0` 这类索引写法更稳

5. `@Bean` 调用只做轻逻辑，别把重业务塞进去

像这样：

```java
@PreAuthorize("@orderAuthService.canView(#orderId)")
```

是很常见的。
但这里面的 Bean 方法最好满足几个原则：

- 返回值清晰，最好就是 `boolean`
- 尽量无副作用
- 不做复杂远程调用
- 不在里面偷偷改数据

否则表达式虽然短，真实逻辑却藏得很深，后期排查会非常痛苦。

6. SpEL 失败时，先怀疑“上下文没有这个变量”，再怀疑语法

开发里更常见的问题是：

- `#result` 当前阶段还不能用
- `returnObject` 只能在后置判断里用
- `authentication` 当前线程里根本没有登录态
- `@beanName` 写错了，Spring 容器里没有这个 Bean

也就是说，很多报错看起来像“表达式写错”，本质上其实是“表达式运行环境不成立”。

7. 适合把规则写在注解上，不适合把业务写在注解上

比较适合放进 SpEL 的是：

- 权限判断
- 缓存 key
- 是否缓存
- 是否监听
- 很轻的规则分支

不适合放进去的是：

- 多层 if/else 业务判断
- 依赖数据库多次查询的逻辑
- 依赖多个外部系统结果的逻辑
- 需要单测覆盖很多分支的复杂规则

一句实战经验：

**注解上的表达式应该让人“一眼看懂意图”，而不是“进去读半天业务”。**

8. 看源码时，重点记“变量是谁放进去的”

SpEL 真正难的不是语法，而是“这个变量哪来的”。
以后你在项目里看到一段表达式，最该追的问题不是：

- 这段语法什么意思

而是：

- `#id` 是谁放进上下文的
- `#result` 在这个时机为什么能拿到
- `authentication` 从哪个线程上下文来的
- `@xxService` 为什么能直接被表达式调用

把“变量来源”看明白，SpEL 基本就不神秘了。

---

**七、一句话总结**

常用支持 SpEL 的注解有 `@PreAuthorize`、`@PostAuthorize`、`@Cacheable`、`@CacheEvict`、`@CachePut`、`@Value`、`@EventListener(condition=...)`。
它们之所以能这样写，是因为 Spring 在这些注解的实现里，专门把注解属性当表达式解析了。
SpEL 常用语法就是：`#参数`、`@Bean`、`#result`、`T(类名)`、逻辑/比较/三元运算。

如果你愿意，我可以下一步直接给你整理一版”SpEL 语法速查表.md”。

---

## 🔗 相关笔记

- [[技术栈/Java与框架/MybatisPlus/Spring/SpringSecurity/SpringSecurity概述]] —— @PreAuthorize 中使用的 SpEL 表达式
- [[技术栈/Java与框架/MybatisPlus/Spring/SpringSecurity/JWT集成SpringSecurity]] —— JWT + Spring Security 中的权限表达式
- [[技术栈/Java与框架/MybatisPlus/Spring/SpringBoot/AOP]] —— SpEL 与 AOP 注解的配合使用

