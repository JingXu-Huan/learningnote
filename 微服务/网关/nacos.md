# 配置网关😢

## 网关本身也是微服务。

这里以nacos为例，需要引入：

```xml
<!--网关-->
<dependency>
<groupId>org.springframework.cloud</groupId>
<artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<!--nacos discovery-->
<dependency>
<groupId>com.alibaba.cloud</groupId>
<artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<!--负载均衡-->
<dependency>
groupId>org.springframework.cloud</groupId>
<artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

## 配置路由

其实可以由配置中心进行简化。

```yml
server:
  port: 8080
spring:
  application:
    name: gateway
  cloud:
    nacos:
      server-addr: 192.168.150.101:8848
    gateway:
      routes:
        - id: item # 路由规则id，自定义，唯一
          uri: lb://item-service # 路由的目标服务，lb代表负载均衡，会从注册中心拉取服务列表
          predicates: # 路由断言，判断当前请求是否符合当前规则，符合则路由到目标服务
            - Path=/items/**,/search/** # 这里是以请求路径作为判断规则
        - id: cart
          uri: lb://cart-service
          predicates:
            - Path=/carts/**
        - id: user
          uri: lb://user-service
          predicates:
            - Path=/users/**,/addresses/**
        - id: trade
          uri: lb://trade-service
          predicates:
            - Path=/orders/**
        - id: pay
          uri: lb://pay-service
          predicates:
            - Path=/pay-orders/**

```

### 自定义断言

如果需要自定义断言，需要写一个类去继承：`AbstractRoutePredicateFactory`

举例：(白天才放行的断言)

```java
@Component
public class DayTimeRoutePredicateFactory extends AbstractRoutePredicateFactory<DayTimeRoutePredicateFactory.Config> {
    public DayTimeRoutePredicateFactory() {
        super(Config.class);
    }
    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        //这里需要一个断言式接口，返回一个布尔值
        //if(true) 就 放行
        return exchange -> {
            int hour = LocalTime.now().getHour();
            // 早上8点到晚上8点允许
            return hour >= 8 && hour < 20;
        };
    }
    public static class Config {
        // 可以在 yml 里配 startHour, endHour
    }
}
```

对应的配置文件只需这样写：

```yml
spring:
  cloud:
    gateway:
      routes:
        - id: item
          uri: lb://item-service
          predicates:
            - Path=/items/**,/search/**
            - name: DayTime
              args:
                startHour: 8
                endHour: 20
                zoneId: Asia/Shanghai

```

### 自定义过滤器(Filter)

```java
@Component  // 让 Spring 自动加载
public class LogGatewayFilterFactory extends AbstractGatewayFilterFactory<LogGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(LogGatewayFilterFactory.class);

    public LogGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        // 这里就是过滤器逻辑
        return (ServerWebExchange exchange, GatewayFilterChain chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            log.info("请求路径: {}", path);

            // 在请求头加一个标记，传递给后端服务
            exchange = exchange.mutate()
                //这里要求使用消费性接口
                    .request(r -> r.headers(h -> h.add("X-Gateway-Tag", "from-gateway")))
                    .build();

            return chain.filter(exchange)  // 放行给下一个过滤器
                    .then(Mono.fromRunnable(() -> {
                        log.info("响应完成，状态码: {}", exchange.getResponse().getStatusCode());
                    }));
        };
    }

    // 配置类，可以在 yml 中传参数
    public static class Config {
        // 这里可以加字段，比如是否开启日志
    }
}

```

### 大多时候我们只需要按照默认的即可

