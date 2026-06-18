# 一、AOP核心注解（@Aspect体系）

## 1️⃣ `@Aspect`（切面声明）

* 作用：标识一个类是**切面类**
* 本质：让 Spring 识别该类包含“横切逻辑”

```java
@Aspect
@Component
public class LogAspect {
}
```

👉 没有它，下面所有通知注解都不会生效

---

## 2️⃣ `@Pointcut`（切点表达式）

* 作用：定义“拦截规则”（哪些方法会被增强）
* 本质：复用的表达式抽象

```java
@Pointcut("execution(* com.example.service.*.*(..))")
public void servicePointcut() {}
```

👉 常见表达式：

* `execution(...)`：方法级拦截（最常用）
* `@annotation(...)`：拦截带某注解的方法
* `within(...)`：按类/包范围

---

# 二、五大通知注解（Advice）

## 3️⃣ `@Before`（前置通知）

* 执行时机：目标方法 **执行前**
* 典型用途：权限校验、日志记录

```java
@Before("servicePointcut()")
public void before() {
    System.out.println("方法执行前");
}
```

---

## 4️⃣ `@After`（后置通知）

* 执行时机：**方法执行结束后（无论成功或异常）**
* 类似 finally

```java
@After("servicePointcut()")
public void after() {
    System.out.println("方法结束");
}
```

---

## 5️⃣ `@AfterReturning`（返回后通知）

* 执行时机：**方法正常返回后**
* 可拿到返回值

```java
@AfterReturning(value = "servicePointcut()", returning = "result")
public void afterReturning(Object result) {
    System.out.println("返回值：" + result);
}
```

👉 适用场景：

* 统一日志
* 结果包装
* 数据脱敏

---

## 6️⃣ `@AfterThrowing`（异常通知）

* 执行时机：**方法抛异常时**
* 可捕获异常对象

```java
@AfterThrowing(value = "servicePointcut()", throwing = "ex")
public void afterThrowing(Exception ex) {
    System.out.println("异常：" + ex.getMessage());
}
```

---

## 7️⃣ `@Around`（环绕通知 ⭐最强）

* 执行时机：**包裹整个方法执行**
* 可以控制：

  * 是否执行目标方法
  * 返回值修改
  * 异常处理
  * 性能统计

```java
@Around("servicePointcut()")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.currentTimeMillis();

    Object result = pjp.proceed(); // 执行目标方法

    long cost = System.currentTimeMillis() - start;
    System.out.println("耗时：" + cost);

    return result;
}
```

👉 这是 AOP 的“控制中心”

---

# 三、AOP启动与配置类注解

## 8️⃣ `@EnableAspectJAutoProxy`

* 作用：开启 Spring AOP 自动代理机制
* Spring Boot 通常**默认已开启**

```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {}
```

---

## 9️⃣ `@Order`（切面优先级）

* 作用：多个切面时控制执行顺序
* 数字越小，优先级越高

```java
@Aspect
@Order(1)
public class LogAspect {}
```

---

# 四、常见组合结构（工程标准写法）

```java
@Aspect
@Component
@Order(1)
public class LogAspect {

    @Pointcut("execution(* com.example.service.*.*(..))")
    public void pointcut() {}

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("start");
        Object obj = pjp.proceed();
        System.out.println("end");
        return obj;
    }
}
```

---

# 五、执行顺序（重点理解）

一次完整调用链：

```
@Around（前半）
  → @Before
    → 目标方法
  → @AfterReturning / @AfterThrowing
@After
@Around（后半）
```

---

# 六、补充：更现代的 AOP 用法

## 1️⃣ 按注解切入（更常见）

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {}
```

```java
@Pointcut("@annotation(com.xxx.Log)")
public void logPointcut() {}
```

---

## 2️⃣ 实战场景映射

| 场景     | 注解                        |
| ------ | ------------------------- |
| 日志记录   | @Before / @AfterReturning |
| 性能监控   | @Around                   |
| 异常统一处理 | @AfterThrowing            |
| 权限校验   | @Before / @Around         |
| 事务管理   | Spring Transaction AOP    |

---

如果你想继续深入，我可以帮你拆：

* 🔥 Spring AOP vs AspectJ 区别（代理机制底层）
* 🔥 JDK动态代理 vs CGLIB
* 🔥 @Transactional 本质就是 AOP
* 🔥 自定义注解 + AOP 做权限系统（企业级写法）
