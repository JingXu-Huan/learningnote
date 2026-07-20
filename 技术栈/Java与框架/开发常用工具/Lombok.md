# Lombok：减少样板代码，但不要隐藏设计 🧩

`Lombok` 通过注解处理器在编译期生成 getter、setter、构造器、Builder 等代码。它减少了重复代码，但 IDE 展示的代码和真正参与编译的代码不是同一份，使用时要理解“生成了什么”。

## 常用注解

| 注解 | 生成内容 | 常见用途 |
|------|------|------|
| `@Getter` / `@Setter` | getter / setter | DTO、配置对象 |
| `@ToString` | `toString()` | 调试输出 |
| `@EqualsAndHashCode` | 相等性和哈希方法 | 值对象，需谨慎用于实体 |
| `@NoArgsConstructor` | 无参构造器 | ORM、序列化 |
| `@RequiredArgsConstructor` | `final` / `@NonNull` 参数构造器 | Spring 构造器注入 |
| `@AllArgsConstructor` | 全字段构造器 | 测试或内部对象，谨慎暴露 |
| `@Builder` | Builder API | 参数较多、创建过程复杂的对象 |
| `@Value` | 不可变类常用组合 | 配置快照、值对象 |
| `@Data` | 多个注解组合 | 简单 DTO，不建议到处使用 |
| `@Slf4j` | `log` 字段 | 日志记录 |

## 推荐的 Spring 注入方式

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
}
```

这种方式仍然是构造器注入，只是省去了构造器样板代码。依赖可以使用 `final` 明确表达，便于测试和保证对象初始化完整。

## `@Data` 不是万能注解

```java
// ❌ 实体类上直接使用 @Data 可能引入隐患
@Data
public class UserEntity {
    private Long id;
    private List<RoleEntity> roles;
}
```

可能的问题：

- 自动生成 `equals` / `hashCode` 可能包含关联对象，触发递归或懒加载；
- `toString()` 可能打印密码、Token、身份证号等敏感信息；
- 自动生成 setter 会让本应受约束的领域对象被任意修改；
- 继承关系下的 `equals` 语义需要单独设计。

更稳妥的写法是按需使用：

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {
    private Long id;
    private String username;

    public void changeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        this.username = username;
    }
}
```

## `@Builder` 的常见坑

### 1. 集合字段使用 `@Builder.Default`

```java
@Builder
public class OrderCreateCommand {
    private String userId;

    @Builder.Default
    private List<String> skuIds = new ArrayList<>();
}
```

没有 `@Builder.Default` 时，字段初始化值不一定会被 Builder 使用，未显式设置时仍可能得到 `null`。集合也可以使用 `@Singular`：

```java
@Builder
public class OrderCreateCommand {
    @Singular
    private List<String> skuIds;
}
```

### 2. `@Builder` 与构造器组合

在类上使用 `@Builder` 时，Lombok 可能生成包级构造器。需要无参构造器的 ORM 或序列化框架，要显式添加 `@NoArgsConstructor`，必要时同时提供受保护的全参构造器。

### 3. 不要把 Builder 当作校验器

Builder 只负责创建对象，不会自动保证字段合法。可以在 `build()` 前校验，或让命令对象进入 Service 后经过 Bean Validation 和业务校验。

## 日志和敏感字段

不要让 `@ToString`、`@Data` 或日志模板自动输出密码、Token、密钥、完整手机号等敏感数据。必要时排除字段：

```java
@Getter
@ToString(exclude = {"password", "accessToken"})
public class LoginCommand {
    private String username;
    private String password;
    private String accessToken;
}
```

## 编译和 IDE 排查

1. IDEA 中确认开启 Annotation Processing。
2. Maven 项目执行 `mvn clean compile`，不要只依赖 IDEA 的增量编译。
3. 检查 `lombok` 版本是否由 Spring Boot BOM 统一管理。
4. 与 MapStruct 一起使用时，同时配置 Lombok 和 MapStruct processor。
5. 运行 `delombok` 或查看编译产物，确认生成代码符合预期。

## 最佳实践

- DTO 可以适度使用 `@Data`，实体、值对象和领域对象优先按需加注解。
- 依赖注入优先 `@RequiredArgsConstructor` + `final` 字段。
- 不要用 `@SneakyThrows` 隐藏重要异常，也不要用 `@Cleanup` 替代明确的资源生命周期设计。
- 生成代码必须能被团队成员理解；公共库中的核心模型不要过度依赖魔法注解。
