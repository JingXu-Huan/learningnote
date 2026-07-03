# Spring 源码原理分析文档

## 一、前言

Spring 是 Java 后端开发中最重要的基础框架之一。很多开发者平时使用 Spring Boot、Spring MVC、Spring Data、MyBatis、RedisTemplate、事务注解等功能时，往往只关注“怎么用”，而忽略了 Spring 底层到底做了什么。

从源码角度看，Spring 的核心并不是某一个注解，也不是某一个配置文件，而是一套围绕 **IoC 容器、Bean 生命周期、依赖注入、扩展点、动态代理、事务拦截、Web 请求分发** 构建起来的基础设施。

理解 Spring 源码，不是为了背源码，而是为了回答几个关键问题：

1. 为什么加上 `@Component`、`@Service`、`@Controller` 后，对象就能交给 Spring 管理？
2. 为什么 `@Autowired` 可以自动注入依赖？
3. 为什么 `@Transactional` 有时候会失效？
4. 为什么 AOP 本质上依赖代理？
5. Spring Boot 为什么能做到“约定大于配置”？
6. 一个 HTTP 请求进入 Spring MVC 后，内部到底经历了什么？

本文围绕 Spring 的核心源码主线，对其原理进行系统分析。

---

# 二、Spring 的核心思想

## 2.1 IoC：控制反转

IoC，全称是 Inversion of Control，中文通常叫“控制反转”。

在传统 Java 开发中，对象通常由程序员自己创建：

```java
UserService userService = new UserServiceImpl();
```

这样做的问题是，对象之间的依赖关系完全写死在代码中。例如 `UserController` 依赖 `UserService`，`UserService` 又依赖 `UserMapper`，对象创建和对象组装都由业务代码自己完成，耦合度较高。

Spring 的做法是：程序员不再直接负责对象的创建和依赖组装，而是把对象交给 Spring 容器管理。

也就是说，原来由程序控制对象创建，现在由 Spring 容器控制对象创建，这就是“控制反转”。

---

## 2.2 DI：依赖注入

DI，全称是 Dependency Injection，即依赖注入。

IoC 是思想，DI 是实现方式。

例如：

```java
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
}
```

这里的 `UserMapper` 并不是由 `UserService` 自己 new 出来的，而是由 Spring 容器在创建 `UserService` 的过程中自动注入进去。

从源码角度看，Spring 会完成以下事情：

1. 扫描类路径，找到需要管理的类；
2. 把类解析成 `BeanDefinition`；
3. 根据 `BeanDefinition` 创建 Bean 对象；
4. 给 Bean 填充属性；
5. 执行初始化逻辑；
6. 把最终 Bean 放入单例池；
7. 后续其他对象需要依赖它时，从容器中取出并注入。

---

# 三、Spring 容器的核心结构

## 3.1 BeanFactory

`BeanFactory` 是 Spring 最基础的容器接口。

它的核心职责是：管理 Bean，并根据名称或类型获取 Bean。

常见方法包括：

```java
Object getBean(String name);

<T> T getBean(Class<T> requiredType);
```

从设计上看，`BeanFactory` 更偏底层，它只定义了 IoC 容器最基本的能力。

---

## 3.2 ApplicationContext

`ApplicationContext` 是 `BeanFactory` 的高级扩展，也是我们平时真正使用最多的 Spring 容器。

它在 `BeanFactory` 的基础上增加了很多企业级功能，例如：

1. 国际化支持；
2. 事件发布机制；
3. 资源加载；
4. 环境变量抽象；
5. 自动注册后置处理器；
6. 更完整的生命周期管理。

常见实现类包括：

```java
AnnotationConfigApplicationContext
ClassPathXmlApplicationContext
AnnotationConfigServletWebServerApplicationContext
```

普通 Spring 项目常见的是 `AnnotationConfigApplicationContext`。

Spring Boot Web 项目中，常见的是 `AnnotationConfigServletWebServerApplicationContext`。

---

# 四、Spring 容器启动主线：refresh 方法

理解 Spring 源码，最核心的入口是：

```java
AbstractApplicationContext#refresh()
```

`refresh()` 是 Spring 容器启动的总流程方法。Spring 容器初始化、BeanDefinition 加载、BeanFactory 创建、BeanPostProcessor 注册、单例 Bean 实例化，基本都围绕这个方法展开。

可以把 `refresh()` 理解为 Spring 容器启动的“总导演”。

其核心流程可以概括为：

```java
public void refresh() {
    prepareRefresh();
    ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
    prepareBeanFactory(beanFactory);
    postProcessBeanFactory(beanFactory);
    invokeBeanFactoryPostProcessors(beanFactory);
    registerBeanPostProcessors(beanFactory);
    initMessageSource();
    initApplicationEventMulticaster();
    onRefresh();
    registerListeners();
    finishBeanFactoryInitialization(beanFactory);
    finishRefresh();
}
```

下面逐步分析。

---

## 4.1 prepareRefresh：准备刷新

这个阶段主要做容器启动前的准备工作。

典型任务包括：

1. 设置容器启动时间；
2. 设置容器激活状态；
3. 初始化环境变量；
4. 校验必要属性；
5. 准备事件集合。

此时还没有真正创建业务 Bean。

---

## 4.2 obtainFreshBeanFactory：获取 BeanFactory

这个阶段会创建或刷新底层的 `BeanFactory`。

在传统 XML 配置中，这一步会加载 XML 文件并解析 Bean 定义。

在注解驱动场景中，Spring 会基于配置类、扫描路径、注解元数据等方式注册 BeanDefinition。

核心对象是：

```java
DefaultListableBeanFactory
```

它是 Spring 默认最重要的 BeanFactory 实现。

它既能保存 BeanDefinition，也能创建 Bean，还能处理依赖查找、依赖注入、单例缓存等逻辑。

---

## 4.3 prepareBeanFactory：准备 BeanFactory

这一步会给 BeanFactory 设置一些基础组件，例如：

1. 类加载器；
2. 表达式解析器；
3. 属性编辑器；
4. 环境对象；
5. 内置依赖；
6. 一些基础 BeanPostProcessor。

这一步相当于给 BeanFactory 装配基础能力。

---

## 4.4 invokeBeanFactoryPostProcessors：执行 BeanFactoryPostProcessor

这是 Spring 源码中非常重要的扩展点。

`BeanFactoryPostProcessor` 的作用是：在 Bean 实例化之前，修改 BeanDefinition。

其中最重要的实现之一是：

```java
ConfigurationClassPostProcessor
```

它负责处理：

```java
@Configuration
@ComponentScan
@Bean
@Import
@PropertySource
```

也就是说，我们平时写的配置类、包扫描、`@Bean` 方法，很多都是在这个阶段被解析的。

例如：

```java
@Configuration
@ComponentScan("com.example")
public class AppConfig {
}
```

Spring 会解析 `AppConfig`，然后根据 `@ComponentScan` 扫描指定包下的类，把符合条件的类注册成 BeanDefinition。

---

## 4.5 registerBeanPostProcessors：注册 BeanPostProcessor

`BeanPostProcessor` 是 Spring Bean 生命周期中最重要的扩展点之一。

它的作用是：在 Bean 初始化前后进行增强。

典型接口：

```java
public interface BeanPostProcessor {

    Object postProcessBeforeInitialization(Object bean, String beanName);

    Object postProcessAfterInitialization(Object bean, String beanName);
}
```

AOP 代理对象的创建，也与 BeanPostProcessor 密切相关。

例如：

```java
AnnotationAwareAspectJAutoProxyCreator
```

它本身就是一个 BeanPostProcessor，负责在 Bean 初始化后判断当前 Bean 是否需要被 AOP 增强。如果需要增强，就创建代理对象并返回。

这也是为什么 AOP 和事务不是简单地“修改原对象”，而是经常返回一个代理对象。

---

## 4.6 finishBeanFactoryInitialization：实例化非懒加载单例 Bean

这是 Spring 容器启动中非常关键的一步。

到这个阶段，Spring 会开始真正创建非懒加载的单例 Bean。

核心方法大致是：

```java
preInstantiateSingletons()
```

然后进入 Bean 创建流程：

```java
getBean()
  -> doGetBean()
    -> createBean()
      -> doCreateBean()
```

也就是说，真正创建 Bean 的主线在：

```java
AbstractBeanFactory#doGetBean()
AbstractAutowireCapableBeanFactory#createBean()
AbstractAutowireCapableBeanFactory#doCreateBean()
```

---

# 五、BeanDefinition：Bean 的元信息

Spring 并不是一扫描到类就直接创建对象，而是先把类解析成 `BeanDefinition`。

`BeanDefinition` 可以理解为 Bean 的“设计图纸”。

它里面保存了 Bean 的各种元信息，例如：

1. Bean 的 class 类型；
2. Bean 的作用域；
3. 是否懒加载；
4. 构造方法参数；
5. 属性依赖；
6. 初始化方法；
7. 销毁方法；
8. 是否 primary；
9. 是否 abstract。

例如：

```java
@Component
public class UserService {
}
```

Spring 扫描到这个类后，不会马上 new `UserService`，而是先生成一个对应的 `BeanDefinition`，放入 BeanFactory 中。

后续真正需要创建 Bean 时，Spring 再根据 BeanDefinition 进行实例化、属性填充、初始化和代理增强。

---

# 六、Bean 的完整生命周期

Spring Bean 的生命周期可以分为以下几个阶段：

```text
1. 加载 BeanDefinition
2. 实例化 Bean
3. 属性填充
4. Aware 回调
5. BeanPostProcessor 初始化前处理
6. 初始化方法
7. BeanPostProcessor 初始化后处理
8. 放入单例池
9. 使用 Bean
10. 容器关闭时执行销毁逻辑
```

---

## 6.1 实例化

实例化指的是创建对象本身。

类似于：

```java
UserService userService = new UserService();
```

Spring 内部可能通过反射调用构造方法，也可能通过工厂方法创建对象。

涉及的典型逻辑包括：

1. 推断构造方法；
2. 处理构造方法注入；
3. 反射创建对象；
4. 处理 CGLIB 增强类。

---

## 6.2 属性填充

对象创建出来后，字段还没有注入。

例如：

```java
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
}
```

此时 Spring 会进入属性填充阶段，找到 `userMapper` 这个依赖，然后从容器中获取对应 Bean，再注入到 `UserService` 中。

源码主线一般在：

```java
populateBean()
```

`@Autowired` 的处理主要与下面这个后置处理器有关：

```java
AutowiredAnnotationBeanPostProcessor
```

它会解析字段、构造器、方法上的 `@Autowired`，然后完成依赖注入。

---

## 6.3 Aware 回调

如果 Bean 实现了一些 Aware 接口，Spring 会把容器相关对象回调给它。

例如：

```java
public class UserService implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
```

常见 Aware 接口包括：

```java
BeanNameAware
BeanFactoryAware
ApplicationContextAware
EnvironmentAware
ResourceLoaderAware
```

Aware 的作用是让普通 Bean 感知到 Spring 容器中的某些基础对象。

---

## 6.4 初始化

初始化阶段主要包括：

1. 执行 `@PostConstruct` 方法；
2. 执行 `InitializingBean#afterPropertiesSet()`；
3. 执行自定义 init-method；
4. 执行 BeanPostProcessor 的前置和后置处理。

例如：

```java
@PostConstruct
public void init() {
    System.out.println("初始化逻辑");
}
```

这个阶段通常用于完成依赖注入之后的初始化工作，例如加载缓存、建立连接、校验配置等。

---

## 6.5 初始化后增强

Bean 初始化完成后，Spring 会调用：

```java
postProcessAfterInitialization()
```

很多代理对象就是在这个阶段创建的。

例如，如果某个 Bean 的方法匹配了 AOP 切点，Spring 可能不会直接返回原始对象，而是返回一个代理对象。

所以你从容器中拿到的 Bean，有时候并不是原始类实例，而是代理类实例。

---

# 七、Spring 如何解决循环依赖

循环依赖是 Spring 源码中非常经典的问题。

例如：

```java
@Service
public class A {
    @Autowired
    private B b;
}

@Service
public class B {
    @Autowired
    private A a;
}
```

A 依赖 B，B 又依赖 A，这就是循环依赖。

Spring 对单例 Bean 的 setter 注入循环依赖，主要通过三级缓存解决。

---

## 7.1 三级缓存

Spring 单例池相关的三个缓存大致如下：

```java
singletonObjects
earlySingletonObjects
singletonFactories
```

可以理解为：

| 缓存                    | 含义                       |
| --------------------- | ------------------------ |
| singletonObjects      | 一级缓存，保存完整初始化后的单例 Bean    |
| earlySingletonObjects | 二级缓存，保存提前暴露的早期 Bean      |
| singletonFactories    | 三级缓存，保存可以创建早期 Bean 引用的工厂 |

---

## 7.2 循环依赖处理过程

以 A 依赖 B，B 又依赖 A 为例：

1. Spring 开始创建 A；
2. A 实例化完成，但属性还没有填充；
3. Spring 把 A 的早期引用工厂放入三级缓存；
4. A 开始注入 B；
5. Spring 发现 B 还没创建，于是开始创建 B；
6. B 实例化完成后，开始注入 A；
7. Spring 发现 A 正在创建中，于是从三级缓存中拿到 A 的早期引用；
8. B 注入 A 成功，B 初始化完成；
9. A 继续注入 B；
10. A 初始化完成；
11. 最终 A 和 B 都进入一级缓存。

---

## 7.3 为什么构造器循环依赖解决不了

如果是构造器注入：

```java
public class A {
    public A(B b) {}
}

public class B {
    public B(A a) {}
}
```

这种情况下，A 创建时必须先创建 B，B 创建时又必须先创建 A。

由于对象连实例化都没有完成，就不存在“提前暴露早期引用”的机会，因此 Spring 无法解决这种构造器循环依赖。

所以实际开发中，构造器注入虽然更清晰，但要避免形成强循环依赖。

---

# 八、AOP 源码原理

## 8.1 AOP 的基本概念

AOP，全称是 Aspect Oriented Programming，即面向切面编程。

它的核心思想是：把日志、权限、事务、监控等横切逻辑，从业务代码中抽离出来，通过代理方式织入到目标方法执行前后。

常见概念包括：

| 概念        | 含义   |
| --------- | ---- |
| Aspect    | 切面   |
| JoinPoint | 连接点  |
| Pointcut  | 切点   |
| Advice    | 通知   |
| Target    | 目标对象 |
| Proxy     | 代理对象 |
| Weaving   | 织入   |

例如：

```java
@Aspect
@Component
public class LogAspect {

    @Before("execution(* com.example.service.*.*(..))")
    public void before() {
        System.out.println("方法执行前记录日志");
    }
}
```

---

## 8.2 Spring AOP 的本质

Spring AOP 的本质是动态代理。

它并不是直接修改目标类源码，而是在运行期创建一个代理对象。

调用流程大致是：

```text
调用方 -> 代理对象 -> 拦截器链 -> 目标对象方法
```

如果目标对象实现了接口，Spring 默认可以使用 JDK 动态代理。

如果目标对象没有实现接口，Spring 通常使用 CGLIB 创建子类代理。

---

## 8.3 AOP 代理创建流程

AOP 代理对象的创建与下面这个类关系密切：

```java
AnnotationAwareAspectJAutoProxyCreator
```

它是一个 BeanPostProcessor。

在 Bean 初始化后，Spring 会判断当前 Bean 是否需要被增强：

```java
postProcessAfterInitialization()
  -> wrapIfNecessary()
    -> getAdvicesAndAdvisorsForBean()
    -> createProxy()
```

大致流程是：

1. 找到所有切面类；
2. 解析切面中的通知方法；
3. 根据切点表达式判断是否匹配当前 Bean；
4. 如果匹配，则为当前 Bean 创建代理对象；
5. 后续容器中保存的是代理对象，而不是原始对象。

---

## 8.4 为什么同类方法调用会导致 AOP 失效

例如：

```java
@Service
public class UserService {

    public void methodA() {
        methodB();
    }

    @Transactional
    public void methodB() {
        // 数据库操作
    }
}
```

如果外部调用：

```java
userService.methodA();
```

`methodA()` 是通过代理对象进入的，但是 `methodA()` 内部调用 `methodB()` 时，本质上是：

```java
this.methodB();
```

也就是说，内部调用没有再次经过代理对象，因此 AOP 拦截器不会生效。

这就是 `@Transactional`、`@Async`、`@Cacheable` 等注解在同类方法内部调用时容易失效的根本原因。

解决方案通常有：

1. 把被增强方法拆到另一个 Bean 中；
2. 通过代理对象调用自身方法；
3. 使用 AspectJ 编译期或加载期织入；
4. 调整代码结构，避免自调用依赖代理增强。

---

# 九、事务源码原理

## 9.1 @Transactional 的本质

`@Transactional` 并不是魔法，它本质上也是基于 AOP 实现的。

当某个方法被 `@Transactional` 标注后，Spring 会为它创建事务拦截逻辑。

调用流程大致是：

```text
调用方
  -> 事务代理对象
    -> TransactionInterceptor
      -> 开启事务
      -> 执行业务方法
      -> 提交事务或回滚事务
```

核心类包括：

```java
TransactionInterceptor
TransactionAspectSupport
PlatformTransactionManager
DataSourceTransactionManager
```

---

## 9.2 事务执行流程

事务方法执行时，大致流程如下：

```text
1. 获取事务属性
2. 获取事务管理器
3. 创建或加入事务
4. 执行业务方法
5. 如果执行成功，提交事务
6. 如果抛出异常，根据规则判断是否回滚
7. 清理事务上下文
```

伪代码可以理解为：

```java
try {
    beginTransaction();
    Object result = method.invoke();
    commitTransaction();
    return result;
} catch (Throwable ex) {
    rollbackTransactionIfNecessary(ex);
    throw ex;
}
```

---

## 9.3 事务为什么会失效

常见事务失效场景包括：

### 1. 方法不是通过代理对象调用

例如同类方法内部调用：

```java
this.saveUser();
```

这种调用不会经过代理对象，因此事务不会生效。

### 2. 方法权限不合适

在代理模式下，事务方法通常应设计为可被代理正常拦截的方法。实际开发中，建议把事务方法设计为 `public`，并通过外部 Bean 调用。

### 3. 异常被吞掉

例如：

```java
@Transactional
public void save() {
    try {
        userMapper.insert(user);
    } catch (Exception e) {
        log.error("保存失败", e);
    }
}
```

异常被 catch 掉后，没有继续抛出，Spring 事务拦截器感知不到异常，自然不会回滚。

### 4. 默认只对运行时异常回滚

默认情况下，Spring 更常见的是对 `RuntimeException` 和 `Error` 进行回滚。如果希望 checked exception 也回滚，需要显式配置：

```java
@Transactional(rollbackFor = Exception.class)
```

### 5. 数据库引擎不支持事务

例如 MySQL 的 MyISAM 引擎不支持事务，即使 Spring 事务配置正确，也无法实现真正的数据库事务回滚。

---

# 十、Spring MVC 源码原理

## 10.1 DispatcherServlet

Spring MVC 的核心入口是：

```java
DispatcherServlet
```

它使用的是前端控制器模式。

所有请求先进入 `DispatcherServlet`，然后由它统一完成请求分发。

整体流程如下：

```text
浏览器请求
  -> DispatcherServlet
    -> HandlerMapping
      -> HandlerAdapter
        -> Controller
      -> 返回 ModelAndView 或 ResponseBody
    -> ViewResolver
  -> 响应客户端
```

---

## 10.2 请求处理流程

`DispatcherServlet` 的核心方法是：

```java
doDispatch()
```

大致流程为：

```text
1. 根据请求查找 Handler
2. 根据 Handler 查找 HandlerAdapter
3. 执行拦截器 preHandle
4. 调用 Controller 方法
5. 执行拦截器 postHandle
6. 处理返回值
7. 解析视图或写出 JSON
8. 执行 afterCompletion
```

常见核心组件包括：

| 组件                       | 作用                    |
| ------------------------ | --------------------- |
| HandlerMapping           | 根据请求路径查找处理器           |
| HandlerAdapter           | 适配并执行 Controller 方法   |
| HandlerInterceptor       | 拦截请求                  |
| ViewResolver             | 解析视图                  |
| HandlerExceptionResolver | 处理异常                  |
| HttpMessageConverter     | 处理 JSON、XML、字符串等请求响应体 |

---

## 10.3 @RequestMapping 是怎么生效的

例如：

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/{id}")
    public UserVO getById(@PathVariable Long id) {
        return userService.getById(id);
    }
}
```

Spring MVC 启动时，会扫描 Controller 中的请求映射信息。

关键组件是：

```java
RequestMappingHandlerMapping
```

它会解析：

```java
@RequestMapping
@GetMapping
@PostMapping
@PutMapping
@DeleteMapping
```

然后建立请求路径与 Controller 方法之间的映射关系。

当请求 `/user/1` 到来时，Spring MVC 就能根据映射关系找到对应的 Controller 方法。

---

## 10.4 JSON 返回是怎么实现的

当方法上有：

```java
@ResponseBody
```

或者类上有：

```java
@RestController
```

Spring MVC 不会走传统视图解析，而是通过 `HttpMessageConverter` 把返回对象写入 HTTP 响应体。

例如返回：

```java
return user;
```

Spring MVC 会通过 Jackson 等 JSON 序列化工具，把 Java 对象转换成 JSON 字符串返回给浏览器。

---

# 十一、Spring Boot 自动配置原理

严格来说，Spring Boot 不是 Spring Framework 源码的一部分，但它建立在 Spring Framework 之上。

Spring Boot 最重要的思想是：

```text
约定大于配置
自动配置
起步依赖
内嵌服务器
```

---

## 11.1 SpringApplication.run

Spring Boot 应用入口通常是：

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

`SpringApplication.run()` 会完成以下事情：

1. 推断应用类型；
2. 创建 Spring 容器；
3. 加载环境变量；
4. 加载启动监听器；
5. 加载 ApplicationContextInitializer；
6. 刷新 Spring 容器；
7. 启动内嵌 Web 服务器；
8. 发布启动完成事件。

最终仍然会进入 Spring Framework 的核心方法：

```java
refresh()
```

所以 Spring Boot 的底层仍然离不开 Spring 容器启动流程。

---

## 11.2 @SpringBootApplication

`@SpringBootApplication` 是一个组合注解。

它大致包含：

```java
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
```

其中：

| 注解                       | 作用        |
| ------------------------ | --------- |
| @SpringBootConfiguration | 标识当前类是配置类 |
| @EnableAutoConfiguration | 开启自动配置    |
| @ComponentScan           | 开启组件扫描    |

---

## 11.3 自动配置的基本逻辑

Spring Boot 会根据 classpath 中是否存在某些类、是否配置了某些属性、容器中是否已经有某些 Bean，来决定是否自动创建 Bean。

例如：

```java
@ConditionalOnClass
@ConditionalOnMissingBean
@ConditionalOnProperty
```

这些条件注解决定了自动配置是否生效。

例如，如果 classpath 中存在 Redis 相关依赖，并且配置了 Redis 连接信息，Spring Boot 就可能自动创建 RedisTemplate、连接工厂等 Bean。

这就是为什么引入 starter 后，很多功能不用手动配置也能使用。

---

# 十二、Spring 常见扩展点

Spring 强大的原因之一，是它提供了大量扩展点。

## 12.1 BeanFactoryPostProcessor

作用：修改 BeanDefinition。

执行时机：Bean 实例化之前。

典型用途：

1. 修改 BeanDefinition 属性；
2. 注册新的 BeanDefinition；
3. 解析配置类；
4. 处理占位符。

---

## 12.2 BeanPostProcessor

作用：增强 Bean 实例。

执行时机：Bean 初始化前后。

典型用途：

1. 处理 `@Autowired`；
2. 处理 `@PostConstruct`；
3. 创建 AOP 代理；
4. 创建事务代理；
5. 创建异步代理。

---

## 12.3 FactoryBean

`FactoryBean` 是一种特殊 Bean。

普通 Bean 是自己被容器管理。

`FactoryBean` 管理的是它生产出来的对象。

例如：

```java
public class MyFactoryBean implements FactoryBean<User> {

    @Override
    public User getObject() {
        return new User();
    }

    @Override
    public Class<?> getObjectType() {
        return User.class;
    }
}
```

当你从容器中获取这个 Bean 时，默认拿到的是 `getObject()` 返回的对象，而不是 FactoryBean 本身。

如果想获取 FactoryBean 本身，需要使用：

```java
&beanName
```

---

# 十三、源码阅读建议

Spring 源码庞大，不建议一开始就从头到尾看。

推荐阅读顺序如下：

## 13.1 第一阶段：IoC 容器

重点类：

```java
ApplicationContext
BeanFactory
DefaultListableBeanFactory
AbstractApplicationContext
AbstractBeanFactory
AbstractAutowireCapableBeanFactory
BeanDefinition
RootBeanDefinition
```

重点方法：

```java
refresh()
getBean()
doGetBean()
createBean()
doCreateBean()
populateBean()
initializeBean()
```

目标：理解 Bean 是如何被加载、创建、注入和初始化的。

---

## 13.2 第二阶段：扩展点

重点类：

```java
BeanFactoryPostProcessor
BeanDefinitionRegistryPostProcessor
BeanPostProcessor
ConfigurationClassPostProcessor
AutowiredAnnotationBeanPostProcessor
CommonAnnotationBeanPostProcessor
```

目标：理解 Spring 为什么能解析注解，为什么能自动注入，为什么能在 Bean 初始化前后做增强。

---

## 13.3 第三阶段：AOP

重点类：

```java
AnnotationAwareAspectJAutoProxyCreator
AbstractAutoProxyCreator
ProxyFactory
JdkDynamicAopProxy
CglibAopProxy
ReflectiveMethodInvocation
MethodInterceptor
```

重点方法：

```java
postProcessAfterInitialization()
wrapIfNecessary()
createProxy()
invoke()
proceed()
```

目标：理解代理对象如何创建，拦截器链如何执行。

---

## 13.4 第四阶段：事务

重点类：

```java
TransactionInterceptor
TransactionAspectSupport
PlatformTransactionManager
DataSourceTransactionManager
TransactionAttributeSource
```

重点方法：

```java
invoke()
invokeWithinTransaction()
createTransactionIfNecessary()
commitTransactionAfterReturning()
completeTransactionAfterThrowing()
```

目标：理解事务如何开启、提交、回滚，以及为什么会失效。

---

## 13.5 第五阶段：Spring MVC

重点类：

```java
DispatcherServlet
RequestMappingHandlerMapping
RequestMappingHandlerAdapter
HandlerMethod
InvocableHandlerMethod
HandlerExceptionResolver
HttpMessageConverter
```

重点方法：

```java
doDispatch()
getHandler()
getHandlerAdapter()
handle()
invokeHandlerMethod()
writeWithMessageConverters()
```

目标：理解一个 HTTP 请求从进入容器到返回 JSON 的全过程。

---

# 十四、总结

Spring 源码的主线可以概括为一句话：

> Spring 先把类解析成 BeanDefinition，再通过 BeanFactory 创建 Bean，并在 Bean 生命周期中利用各种后置处理器完成依赖注入、初始化、AOP 代理、事务增强等能力；在 Web 场景下，DispatcherServlet 负责统一接收请求并分发给对应的 Controller 方法。

更具体地说：

1. **IoC 容器** 负责对象创建和依赖管理；
2. **BeanDefinition** 是 Bean 的元信息；
3. **refresh()** 是容器启动总流程；
4. **getBean()** 是获取和创建 Bean 的核心入口；
5. **BeanPostProcessor** 是 Bean 增强的关键扩展点；
6. **AOP** 的本质是动态代理；
7. **事务** 的本质是基于 AOP 的方法拦截；
8. **Spring MVC** 的核心是 DispatcherServlet；
9. **Spring Boot** 是对 Spring Framework 的自动配置封装；
10. 理解 Spring 源码，重点不是死记类名，而是掌握对象创建、依赖注入、生命周期扩展和代理增强这几条主线。

对于 Java 后端开发者来说，Spring 源码最值得掌握的不是每一行实现细节，而是它的设计思想：

```text
把复杂对象管理交给容器；
把通用增强逻辑交给代理；
把可变流程开放为扩展点；
把业务代码从基础设施代码中解耦。
```

理解这些之后，再看 Spring、Spring Boot、Spring MVC、Spring Security、Spring Transaction、Spring Data 等框架时，就会发现它们底层其实都在复用同一套核心思想。
