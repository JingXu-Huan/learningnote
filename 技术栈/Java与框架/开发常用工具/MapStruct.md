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
    //这里的mappinngs表示我们在DO和DTO之间有多个属性名不能对应
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

## 什么时候，`mpstruct`它不能为你自动封装呢😕？

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