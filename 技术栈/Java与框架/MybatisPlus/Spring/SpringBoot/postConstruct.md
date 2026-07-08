### 1️⃣ 基本定义

- `@PostConstruct` 来自 **Jakarta Annotations**（以前在 `javax.annotation` 包，现在在 `jakarta.annotation` 包里）。
- 用在 **方法** 上。
- 作用是：**在对象完成依赖注入（初始化）后，自动执行标注的方法**。

------

### 2️⃣ Spring 里的执行顺序

一个 Spring Bean 的生命周期大致是：

1. **实例化**（`new Bean()`）
2. **依赖注入**（`@Autowired` 注入属性）
3. **执行 `@PostConstruct` 标注的方法** ✅
4. 如果实现了 `InitializingBean.afterPropertiesSet()`，则执行它
5. 如果配置了 `init-method`，也会执行
6. 之后，Bean 才正式可用

------

### 3️⃣ 使用示例

```java
@Component
public class MyService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @PostConstruct
    public void init() {
        System.out.println("MyService 已经完成依赖注入，现在可以安全使用 redisTemplate 了");
    }
}
```

➡️ 这里 `init()` 会在 Bean 初始化后、但在对外可用之前执行一次。

------

### 4️⃣ 常见用途

- 初始化一些资源（缓存、连接、线程池）
- 检查配置是否正确
- 执行一些预加载逻辑

------

### 5️⃣ 注意事项

- 方法必须是 `void`，**不能有参数**
- **不能抛出检查型异常**（只能抛 `RuntimeException`）
- 如果有多个 `@PostConstruct` 方法，会都执行，但执行顺序不保证

------

✨ 总结：
 `@PostConstruct` 的作用是 **在 Bean 完成依赖注入后立刻执行一次初始化逻辑**，常用于需要依赖注入资源的初始化场景。