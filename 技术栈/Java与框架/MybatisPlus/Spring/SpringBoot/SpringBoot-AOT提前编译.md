# Spring Boot AOT 提前编译 😎😎😎

## 目录

- [一、AOT 是什么](#一aot-是什么)
- [二、Spring Boot 为什么需要 AOT](#二spring-boot-为什么需要-aot)
- [三、AOT 在构建期做了什么](#三aot-在构建期做了什么)
- [四、AOT 与 GraalVM Native Image 的关系](#四aot-与-graalvm-native-image-的关系)
- [五、如何使用](#五如何使用)
- [六、限制与注意事项](#六限制与注意事项)
- [七、适用场景](#七适用场景)

------

## 一、AOT 是什么

**AOT（Ahead-of-Time）**，即“提前处理 / 提前编译”。它的核心思想是：把一部分原本需要在应用启动时完成的分析和准备工作，提前到**构建阶段**完成，并将结果生成为可直接执行的代码或元数据。

传统 Spring Boot 应用启动时，会扫描 classpath、解析注解、推断自动配置、创建 Bean 定义，并大量使用反射和动态代理。AOT 会在构建时预先分析这些信息，生成对应的初始化代码；应用启动时直接使用这些结果，从而减少运行时工作量。

> AOT 不是把所有 Java 代码都编译成机器码；在 JVM 模式下，它主要是提前生成 Spring 的初始化代码。若配合 GraalVM Native Image，才会进一步生成本地可执行文件。

------

## 二、Spring Boot 为什么需要 AOT

Spring 的灵活性来自运行时机制，例如组件扫描、条件装配、反射、动态代理。但这些机制会增加冷启动时间和启动时内存开销。

使用 AOT 后，Spring Boot 可以：

1. **减少启动时间**：跳过或减少组件扫描、条件判断等运行时工作。
2. **降低启动阶段的资源消耗**：更少的反射和类加载意味着更少的 CPU、内存占用。
3. **为原生镜像提供支持**：GraalVM 的封闭世界假设要求构建时知道需要访问哪些类、资源和反射成员；AOT 会生成这类提示信息。
4. **提升冷启动场景体验**：对 Serverless、容器弹性扩缩容、短生命周期任务尤其有价值。

------

## 三、AOT 在构建期做了什么

可以将其理解为：把 Spring 容器的一部分“启动说明书”预先写好。

```text
传统模式：启动时扫描、推断、反射、创建 Bean

AOT 模式：构建时分析 Spring 应用
                 ↓
          生成 Bean 初始化代码、代理代码、运行时提示
                 ↓
          启动时直接使用生成结果
```

典型产物包括：

- Bean 定义和 Bean 初始化相关的源代码；
- 自动配置、条件注解处理后的结果；
- 代理相关代码；
- 反射、资源、序列化、动态代理等运行时提示（Runtime Hints）。

这样，运行时不必再“猜测”如何装配应用，而是按构建期已经确定的方案完成初始化。

------

## 四、AOT 与 GraalVM Native Image 的关系

二者经常同时出现，但不是同一个概念：

| 对比项 | Spring AOT | GraalVM Native Image |
| --- | --- | --- |
| 目标 | 提前处理 Spring 应用初始化 | 将应用编译为本地机器码可执行文件 |
| 是否仍运行在 JVM 上 | 可以 | 不可以，直接运行原生二进制文件 |
| 是否必须搭配使用 | 否 | Spring Boot 原生镜像通常需要 AOT 支持 |
| 主要收益 | 更快的 JVM 启动 | 更快启动、更低内存占用 |
| 主要代价 | 构建期约束增加 | 构建更慢、兼容性和调试成本更高 |

因此有两种常见模式：

- **AOT + JVM**：仍然打成 JAR，在 JVM 上运行，但使用 AOT 生成的初始化代码加快启动。
- **AOT + Native Image**：先完成 Spring AOT 处理，再由 GraalVM 构建原生镜像，最终产物是不依赖 JVM 启动的可执行程序。

------

## 五、如何使用

### 1. Maven 构建 AOT 产物

Spring Boot 官方的 `native` profile 会触发 AOT 处理：

```bash
mvn -Pnative package
```

若构建的是带 AOT 生成代码的 JAR，可在 JVM 模式下启用：

```bash
java -Dspring.aot.enabled=true -jar target/your-application.jar
```

### 2. 构建 GraalVM 原生镜像

项目需要使用 Spring Boot 对原生镜像的支持，并准备 GraalVM 环境。常见 Maven 命令为：

```bash
mvn -Pnative native:compile
```

构建完成后会得到平台相关的本地可执行文件。具体插件配置和命令会随 Spring Boot、GraalVM 版本而变化，应以当前项目版本的官方文档为准。

### 3. 自定义反射提示

如果业务代码或第三方库在运行时通过反射访问类型，AOT 分析可能无法自动发现，需要显式注册提示：

```java
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(UserRuntimeHints.class)
class AotConfiguration {
}

class UserRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(User.class);
    }
}
```

`RuntimeHints` 用来告诉 AOT / Native Image：哪些类需要反射、哪些资源需要打包、哪些 JDK 动态代理需要保留。

------

## 六、限制与注意事项

AOT 的本质是“构建期确定更多信息”，因此会牺牲一部分运行时动态性：

1. **classpath 应在构建期确定**：运行时临时添加依赖或插件的模式不适合 AOT。
2. **Bean 定义不能任意变化**：依赖运行时动态注册 Bean 的设计需要调整或补充提示。
3. **条件配置有约束**：会影响 Bean 是否创建的配置，在构建期与运行期必须保持一致；例如部分 `@Profile`、`@ConditionalOnProperty` 使用方式需要特别验证。
4. **反射和动态代理需显式处理**：框架无法静态推断到的行为，要通过 `RuntimeHints` 或兼容库支持补齐。
5. **构建时间变长**：把启动期工作前移，构建过程自然会更重，原生镜像尤其明显。

开发阶段通常仍使用普通 JVM 模式以获得更快的编译和热重载体验；在发布、冷启动敏感的环境中再评估是否启用 AOT 或 Native Image。

------

## 七、适用场景

适合优先评估 AOT 的场景：

- Serverless 函数、按请求或按任务启动的应用；
- Kubernetes 中频繁扩缩容的微服务；
- CLI 工具、批处理任务等短生命周期程序；
- 对启动时延和内存占用有明确指标的服务。

不必为了使用新技术而强行启用 AOT。对于长期运行、启动次数很少且动态扩展需求较多的传统后台服务，普通 JVM 部署可能已经足够简单稳定。

------

## 八、一句话总结

**Spring Boot AOT 就是把 Spring 容器在启动时需要做的一部分分析和初始化工作提前到构建期完成，以更严格的构建期约束换取更快启动；它既可用于 JVM 应用加速，也是 Spring Boot 构建 GraalVM Native Image 的重要基础。**

## 参考资料

- [Spring Boot 官方文档：Ahead-of-Time Processing With the JVM](https://docs.spring.io/spring-boot/reference/packaging/aot.html)
