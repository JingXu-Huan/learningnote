# 🎉🎉`MapStruct` —— DO映射到各种领域模型

简化 领域模型之间的映射。

## 快速开始💕💕

1.引入依赖：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.jingxu</groupId>
    <artifactId>MapStruct</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>MapStruct</name>
    <description>MapStruct</description>
    <url/>
    <properties>
        <java.version>21</java.version>
        <mapstruct.version>1.6.3</mapstruct.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

2.开始转换

演示在mapper中做如下操作：

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    //这里的Mappings表示DO和DTO之间有多个属性名不能直接对应
    @Mappings({
            @Mapping(source = "myFollows",target = "follows"),
            @Mapping(source = "myLikes", target = "likes",qualifiedByName = "myCover")
    })
    UserVO toVO(UserDO userDO);
    //这里的default方法是mp无法转换时 需要我们自己去实现
    //mp默认会根据你的方法签名寻找合适的转换方法
    //或是我们不希望通过mp的实现类做转换
    default List<String> likes(String[] myLikes) {
        List<String> list = new ArrayList<>();
        list.addAll(Arrays.asList(myLikes));
        return list;
    }
    //当有多个default方法的签名一致时，你需要指定让mp调用哪个
    @Named(value = "myCover")
    default List<String> cover(String[] myLikes) {
        List<String> list = new ArrayList<>(Arrays.asList(myLikes));
        return list;
    }
}
```

3.现在你可以自己写controller 来进行测试：

```Java
@RestController
public class Controller {
    @Autowired
    private UserMapper userMapper;
    @PostMapping("/test")
    public UserVO testMapper(@RequestBody UserDO userDO) {
        return userMapper.toVO(userDO);
    }
}
```

## 什么时候，`MapStruct` 不能为你自动映射呢😕？

| 源类型                | 目标类型       | 自动支持 | 说明                                                         |
| --------------------- | -------------- | -------- | ------------------------------------------------------------ |
| `String[]`            | `List<String>` | ❌        | `MapStruct` 不会自动把数组封装成集合，需自定义方法或 `@Named` |
| `String`              | `List<String>` | ❌        | 不知道怎么分割字符串                                         |
| `List<String>`        | `String`       | ❌        | 不知道用什么分隔符拼接                                       |
| `Date`                | `String`       | ❌        | 需要指定格式（如 `"yyyy-MM-dd"`）                            |
| `String`              | `Date`         | ❌        | 同上，需要格式化器                                           |
| `Object`              | 任意类型       | ❌        | 类型信息不明，无法推导                                       |
| `Map<String, Object>` | 自定义类       | ❌        | 无法猜测 key 与属性对应关系                                  |
| 多层嵌套对象          | 扁平对象       | ⚠️        | 必须显式指定路径（`@Mapping(source = "user.address.city", target = "city")`） |
| Builder 类            | 普通类         | ⚠️        | 若构造器或 builder 不标准，会失败，需要 builder 配置         |
| 泛型擦除类型          | 明确类型       | ❌        | `MapStruct` 无法通过反射还原泛型信息                         |

## 那什么时候可以呢🫡🫡

| 源类型                            | 目标类型                                | 自动支持 | 说明                            |
| --------------------------------- | --------------------------------------- | -------- | ------------------------------- |
| 基本类型（int、double、boolean…） | 对应包装类（Integer、Double、Boolean…） | ✅        | 自动装箱/拆箱                   |
| 枚举                              | 枚举                                    | ✅        | 同名常量自动匹配                |
| 枚举                              | String                                  | ✅        | 调用 `Enum.name()`              |
| String                            | 枚举                                    | ✅        | 调用 `Enum.valueOf()`           |
| List<A>                           | List<B>                                 | ✅        | 若存在 A→B 的映射               |
| Set<A>                            | Set<B>                                  | ✅        | 同上                            |
| 数组 A[]                          | A[]                                     | ✅        | 相同类型可直接复制              |
| 对象                              | 对象                                    | ✅        | 字段名和类型匹配时自动映射      |
| Map<K,V>                          | Map<K,V>                                | ✅        | 仅限简单映射（K、V 类型可对应） |

## 工程实践：让映射失败得更早

### 1. 对关键 DTO 开启严格检查

默认情况下，目标对象中没有被映射的属性可能只产生警告。对于核心接口，建议把遗漏字段直接变成编译错误：

```java
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface UserMapper {
    UserVO toVO(UserDO source);
}
```

也可以在 `MapperConfig` 中统一配置，避免每个 Mapper 重复编写：

```java
@MapperConfig(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface CentralMapperConfig {
}

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {
    UserVO toVO(UserDO source);
}
```

### 2. 更新已有对象时使用 `@MappingTarget`

`toVO` 适合创建新对象；更新已有对象时不要手动复制一遍字段，可以使用 `@MappingTarget`：

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    void update(@MappingTarget UserDO target, UserUpdateDTO source);
}
```

如果更新接口只允许修改请求中明确传入的字段，可以忽略 `null`：

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
void update(@MappingTarget UserDO target, UserUpdateDTO source);
```

注意：`IGNORE` 只是不覆盖目标对象已有值，不等于把 `null` 转成空字符串，也不等于忽略空集合。是否允许清空字段，要通过接口语义明确区分。

### 3. 把复杂转换拆成可复用方法

不要在 `expression = "java(...)"` 中堆积业务逻辑。可以把日期、枚举、金额和脱敏等转换放到独立类中：

```java
public class MappingHelper {
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

@Mapper(componentModel = "spring", uses = MappingHelper.class)
public interface UserMapper {
    @Mapping(source = "phone", target = "maskedPhone")
    UserVO toVO(UserDO source);
}
```

如果转换方法需要注入 Spring Service，可以让 `uses` 指向 Spring Bean，并优先使用构造器注入，避免生成类的依赖不清晰。

### 4. 处理枚举演进

枚举新增值时，映射代码可能仍然可以编译，但业务含义已经发生变化。对重要枚举可以显式设置未知值策略，或为每个枚举值写映射：

```java
@ValueMappings({
    @ValueMapping(source = "ACTIVE", target = "ENABLED"),
    @ValueMapping(source = "INACTIVE", target = "DISABLED"),
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = "UNKNOWN")
})
UserStatusVO toVO(UserStatus status);
```

### 5. MapStruct 与 Lombok 一起使用时

- `mapstruct-processor` 和 `lombok` 都要作为 annotation processor 配置；
- IDE 能编译不代表 Maven 一定能编译，优先执行 `mvn clean compile` 验证；
- 如果使用 Lombok 的 Builder、`@SuperBuilder` 或链式 setter，重点检查生成类是否被 MapStruct 识别；
- MapStruct 是编译期生成代码，问题应先查看 `target/generated-sources/annotations` 下的实现类，而不是猜运行时反射行为。

## 常见问题排查

| 现象 | 常见原因 | 排查方向 |
|------|------|------|
| 找不到 `UserMapper` Bean | 未配置 `componentModel = "spring"` 或未启用注解处理 | 检查生成类和 Spring 扫描范围 |
| 字段没有赋值 | 字段名、类型或 getter/setter 不匹配 | 查看生成的 `UserMapperImpl` |
| Lombok 字段识别不到 | annotation processor 配置或版本不一致 | 执行 `mvn clean compile`，检查 `pom.xml` |
| `null` 把原值覆盖了 | 使用了默认空值策略 | 更新方法增加 `NullValuePropertyMappingStrategy.IGNORE` |
| 映射了懒加载关联对象 | 映射过程中触发 ORM 访问 | 在事务边界内处理，或先构造查询 DTO |
| 编译时出现未映射属性 | 严格策略发现了新字段 | 显式 `@Mapping`，不要随意改成 `IGNORE` |

## 一句话总结

> MapStruct 的价值不只是少写 setter，更重要的是把对象转换逻辑提前到编译期检查；对核心接口开启严格映射，对复杂转换拆成可测试的方法。
