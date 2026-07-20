# Hutool 与 Apache Commons：工具库怎么选 🔧

工具库的价值是减少重复代码，但“一个 `xxxUtil` 解决所有问题”也容易带来依赖膨胀、语义不清和隐藏行为。选型时先看 JDK，再看 Spring 或已有基础设施，最后才引入第三方工具。

## 常见能力对比

| 需求 | JDK | Apache Commons | Hutool | 建议 |
|------|------|------|------|------|
| 字符串判空、截取 | `String`、`Objects` | Commons Lang | `StrUtil` | 项目已有哪套就统一哪套 |
| 集合判空、分割 | `Collection`、Stream | Commons Collections | `CollUtil` | 复杂集合结构要看可读性 |
| 日期时间 | `java.time` | Commons Lang/DateUtils | `DateUtil` | 新代码优先 `java.time` |
| Bean 属性复制 | 手动或 MapStruct | BeanUtils | `BeanUtil` | 结构稳定映射优先 MapStruct |
| 文件与 IO | `Files`、`Path` | Commons IO | `FileUtil`、`IoUtil` | 大文件要关注流关闭和内存 |
| HTTP | Java HttpClient、Spring | Commons HttpClient 生态 | `HttpUtil` | 生产服务优先统一客户端 |
| 数字计算 | `BigDecimal` | Commons Math | `NumberUtil` | 金额必须明确精度和舍入 |
| 编解码 | JDK Base64 | Commons Codec | `Base64` | 安全算法不要只看工具类名 |

## Maven 引入策略

开发阶段可以快速引入 `hutool-all`，但长期项目建议按模块引入，减少传递依赖：

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-core</artifactId>
    <version>${hutool.version}</version>
</dependency>
```

Apache Commons 也建议按需引入，例如 `commons-lang3`、`commons-io`、`commons-codec`，不要为了一个字符串方法引入整套依赖。

## 字符串处理：先确认 null 语义

```java
String value = StrUtil.trimToNull(input);
if (StrUtil.isBlank(value)) {
    throw new IllegalArgumentException("参数不能为空");
}
```

工具方法对 `null`、空字符串和空白字符串的处理可能不同。不要只根据方法名猜语义，尤其注意：

- `isEmpty` 通常只判断长度是否为 0；
- `isBlank` 通常还会判断空白字符；
- `trim` 可能把原始输入改成另一个业务值；
- 分割字符串时要明确连续分隔符、首尾空元素和空输入的行为。

## 日期时间：优先 `java.time`

```java
LocalDateTime start = LocalDateTime.now();
LocalDate end = LocalDate.parse("2026-07-10");
Duration duration = Duration.between(start, start.plusMinutes(5));
```

新代码尽量不要把 `DateUtil` 或旧 `Date` 类型扩散到领域模型中。工具库可以放在适配层处理历史数据，但时区和格式应由接口契约统一定义。

## Bean 复制：便利不等于安全

```java
// ❌ 结构不稳定、字段语义重要时不建议直接复制
BeanUtil.copyProperties(source, target);
```

反射复制可能带来：

- 同名字段被意外覆盖；
- `null` 是否覆盖旧值不明确；
- 嵌套对象只是浅复制；
- 类型转换失败在运行时才暴露；
- 敏感字段被无意复制到 VO。

对于 DO、DTO、VO 之间的固定转换，优先使用 [[MapStruct]]；对于动态表单或确实需要运行时属性复制的场景，才使用 Bean 工具，并明确允许复制的字段。

## 金额和小数计算

```java
BigDecimal price = new BigDecimal("19.90");
BigDecimal count = new BigDecimal("3");
BigDecimal total = price.multiply(count).setScale(2, RoundingMode.HALF_UP);
```

不要使用 `new BigDecimal(0.1)`，也不要把 `double` 作为金额的长期存储类型。无论使用 `NumberUtil` 还是原生 `BigDecimal`，都要明确：精度、舍入模式、币种和最终格式化时机。

## 文件和 HTTP：工具类不会自动解决资源问题

使用文件工具时重点关注：

- 大文件使用流式读写，不要一次性 `readBytes` 到内存；
- 路径来自用户输入时防止路径穿越；
- 上传文件校验大小、扩展名、MIME 和实际文件头；
- 临时文件在成功和异常路径都要清理。

使用 HTTP 工具时重点关注：

- 连接超时、读取超时、整体超时；
- 连接池和最大并发数；
- 状态码、响应体大小和错误响应处理；
- 重试是否幂等，是否会放大下游压力；
- Token、Cookie、Authorization 是否会进入日志。

## 什么时候不应该使用工具库

1. JDK 已经有可读性更好的实现，例如 `Path`、`Files`、`java.time`、`HttpClient`。
2. 逻辑本身承载业务规则，例如金额、权限、状态流转和库存扣减。
3. 工具方法会隐藏异常、吞掉 `null` 或把多个不同语义的状态合并成一个默认值。
4. 为了一个方法引入重量级依赖，且项目无法接受额外传递依赖。

## 统一工具类的建议

```java
public final class PhoneMaskUtil {
    private PhoneMaskUtil() {
    }

    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
```

好的工具类通常满足：无状态、线程安全、输入输出清晰、没有远程调用、异常策略明确、可以单元测试。若方法开始依赖配置、数据库或当前用户，就应该升级为有明确职责的组件。

## 一句话总结

> 先用 JDK，再用项目已有框架能力；固定结构映射用 MapStruct，动态小工具才用 Hutool 或 Commons，并始终明确 null、时区、精度、异常和资源边界。
