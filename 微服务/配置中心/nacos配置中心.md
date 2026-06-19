# 使用nacos配置中心实现配置的热更新😍😘

## 为什么使用配置中心？🫡

- 网关路由在配置文件中写死了，如果变更必须重启微服务。

- 某些业务配置在配置文件中写死了，每次修改都要重启服务。

- 每个微服务都有很多重复的配置，维护成本高。

当然，我们的微服务不能想重启就重启吧...

## 添加对应的依赖😊

```xml
  <!--nacos配置管理-->
  <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
  </dependency>
  <!--读取bootstrap文件-->
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-bootstrap</artifactId>
  </dependency>
```

## 实现配置共享👌

- **在`Nacos`中添加共享配置** 

  * 把配置文件中重复的配置项抽取出来，在nacos中添加共享配置。

    例如：共享的`jdbc`配置。这些配置项在各个微服务之间是差不多的

    不同的部分，我们在`application.yml`重新配置就好了，通过引用的方式获取配置信息。

    ```yaml
    spring:
      datasource:
        url: jdbc:mysql://${hm.db.host}:${hm.db.port:3306}/${hm.db.database}?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai
        driver-class-name: com.mysql.cj.jdbc.Driver
        username: ${hm.db.user:root}
        password: ${hm.db.pw:202430904jingXu}
      cloud:
        nacos:
          discovery:
            server-addr: 101.42.157.163:8848
    mybatis-plus:
      configuration:
        default-enum-type-handler: com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler
      global-config:
        db-config:
          update-strategy: not_null
          id-type: auto
    ```

- **微服务拉取配置**
  
  接下来，我们要在微服务拉取共享配置。
  
  将拉取到的共享配置与本地的`application.yaml`配置合并，完成项目上下文的初始化。
  
  不过，需要注意的是，读取`Nacos`配置是`SpringCloud`上下文（`ApplicationContext`）初始化时处理的，发生在项目的引导阶段。然后才会初始化`SpringBoot`上下文，去读取`application.yaml`。
  
  也就是说引导阶段，`application.yaml`文件尚未读取，根本不知道`nacos` 地址，该如何去加载`nacos`中的配置文件呢？
  
  `SpringCloud`在初始化上下文的时候会先读取一个名为`bootstrap.yaml`(或者`bootstrap.properties`)的文件，如果我们将`nacos`地址配置到`bootstrap.yaml`中，那么在项目引导阶段就可以读取`nacos`中的配置了。
  
  ```yaml
  spring:
    application:
      name: cart-service # 服务名称
    profiles:
      active: dev
    cloud:
      nacos:
        server-addr: 192.168.150.101 # nacos地址
        config:
          file-extension: yaml # 文件后缀名
          shared-configs: # 共享配置
            - dataId: shared-jdbc.yaml # 共享mybatis配置
            - dataId: shared-log.yaml # 共享日志配置
            - dataId: shared-swagger.yaml # 共享日志配置
  ```
  
  ## 实现配置热更新💕🤩
  
  ### 添加动态配置到Nacos
  
  ```yml
  hm:
      cart:
          maxItems: 5
  ```

---

## 🔗 相关笔记

- [[../网关/nacos]] —— Nacos 网关（服务发现与路由）
- [[../../多线程/线程池七大核心参数]] —— 线程池参数通过 Nacos 动态配置
- [[../多个微服务之间如何相互调用/OpenFeign]] —— 微服务远程调用（依赖 Nacos 服务发现）
  
  

