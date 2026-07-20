# Jackson：JSON 处理与数据边界设计 📦

Jackson 常用于 Java 对象与 JSON 之间的序列化、反序列化，也是 Spring MVC 默认使用的 JSON 处理基础设施。真正容易出问题的地方不在 `readValue`，而在于**泛型、时间、null、字段命名、未知字段和多态数据**。

## 基础用法

```java
ObjectMapper mapper = new ObjectMapper();

String json = mapper.writeValueAsString(user);
User user = mapper.readValue(json, User.class);
```

实际项目中通常复用由 Spring 管理的 `ObjectMapper`，不要每次请求都 `new ObjectMapper()`，否则全局日期、模块和命名策略可能不一致。

## 泛型反序列化：使用 `TypeReference`

```java
String json = "[{\"id\":1,\"name\":\"Jing\"}]";

List<User> users = mapper.readValue(
    json, new TypeReference<List<User>>() {}
);
```

下面这种写法会丢失泛型信息：

```java
// ❌ 可能得到 List<LinkedHashMap>
List<User> users = mapper.readValue(json, List.class);
```

如果是动态泛型，也可以使用 `TypeFactory.constructParametricType` 构造 `JavaType`。

## 常用注解

| 注解 | 用途 |
|------|------|
| `@JsonProperty` | 指定 JSON 字段名 |
| `@JsonIgnore` | 忽略字段 |
| `@JsonFormat` | 指定日期或数字格式 |
| `@JsonAlias` | 兼容多个输入字段名 |
| `@JsonInclude` | 控制 null、空集合等是否输出 |
| `@JsonNaming` | 指定类级别命名策略 |
| `@JsonCreator` | 指定反序列化构造方式 |
| `@JsonValue` | 指定枚举或值对象的输出值 |
| `@JsonAnySetter` | 接收未知字段，需谨慎使用 |

## 时间类型要统一配置

推荐使用 `java.time` 类型，并注册 Java Time 模块：

```java
ObjectMapper mapper = JsonMapper.builder()
    .addModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build();
```

在 Spring Boot 中优先通过配置文件统一时区和格式，而不是在每个字段上散落不同的 `@JsonFormat`：

```yaml
spring:
  jackson:
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss
```

`LocalDateTime` 本身不带时区，跨系统传输时要明确它代表的是本地时间还是业务时间；需要表达绝对时间时优先考虑 `Instant` 或带时区的类型。

## 未知字段与兼容策略

消费外部接口时，第三方新增字段不应该轻易导致旧服务反序列化失败：

```java
ObjectMapper mapper = JsonMapper.builder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build();
```

但内部命令对象是否忽略未知字段，要看接口安全要求。对拼写错误、字段漂移敏感的核心接口，可以保留失败策略并通过版本化保证兼容。

## null、缺失和空值不是一回事

在更新接口中，以下三种状态可能含义不同：

- 字段缺失：不修改原值；
- 字段为 `null`：清空原值，或表示客户端没有提供有效值；
- 字段为空字符串 / 空集合：明确设置为空。

不要仅靠 Jackson 的全局配置解决业务语义。PATCH、更新命令和数据库实体之间，建议使用专门的请求对象和明确的更新策略。

## 统一响应和局部动态 JSON

固定结构优先使用 Java 类型：

```java
public record ApiResponse<T>(int code, String message, T data) {
}
```

只有确实存在动态字段时才使用 `JsonNode` 或 `Map<String, Object>`：

```java
JsonNode root = mapper.readTree(json);
String traceId = root.path("meta").path("traceId").asText(null);
```

`path()` 在字段不存在时返回缺失节点，不会像 `get()` 那样直接得到 `null`，适合读取可选字段；但最终仍要做业务校验。

## 安全注意事项

- 不要把不可信输入直接反序列化为任意类型；
- 不要为了兼容旧代码开启危险的默认类型推断或反序列化任意类；
- 序列化日志前先脱敏，尤其是密码、Token、Cookie 和身份证号；
- JSON 输入仍然需要 Bean Validation 和业务校验，反序列化成功不代表数据合法；
- 大 JSON 要限制请求体大小、嵌套深度和处理耗时，避免资源消耗攻击。

## Spring Boot 中的自定义配置

推荐通过 `Jackson2ObjectMapperBuilderCustomizer` 增量配置，而不是直接替换 Spring Boot 的 `ObjectMapper`：

```java
@Configuration
public class JacksonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .modules(new JavaTimeModule());
    }
}
```

直接定义一个新的 `ObjectMapper` Bean 可能覆盖框架默认模块，导致分页、参数校验或时间类型行为发生变化。改配置前先确认项目中是否已经有统一的 JSON 配置。

## 排查清单

| 现象 | 优先检查 |
|------|------|
| 日期格式不一致 | 时区、`JavaTimeModule`、Spring Boot Jackson 配置 |
| `List<T>` 变成 Map | 是否使用了 `TypeReference` |
| 字段没有序列化 | getter、访问策略、`@JsonIgnore`、命名策略 |
| 新字段导致消费方报错 | 未知字段策略和接口版本 |
| 日志泄露敏感信息 | `toString`、对象序列化和日志脱敏 |
| 某个接口行为和全局不一致 | 是否局部 new 了 `ObjectMapper` |
