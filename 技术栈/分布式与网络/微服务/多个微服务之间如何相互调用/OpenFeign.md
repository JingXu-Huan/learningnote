# 使用`Feign` 远程调用其它微服务🎉🎉

## 新建微服务模块😕

新建微服务模块`api`，然后将需要远程调用的其它微服务引入此模块的依赖。

例如：

* 购物车模块需要远程调用订单模块:

  我们在购物车模块中引入`api`，在`api`模块中定义好 Feign的客户端；

  在购物车模块需要调用时，`@Autowried` 注入客户端；

  然后调用客户端(接口)的方法（就是使用feign帮助我们发送网络请求）。

## `api`模块接口的定义😎

```java
//这里写的是你要远程调用的微服务名称
@FeignClient("item-service")
public interface ItemClient {
    //微服务暴露的请求路径
    @GetMapping("/items")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Set<Long> ids);
    @PutMapping("/items/stock/deduct")
    void deductStock(@RequestBody List<OrderDetailDTO> items);
}
```

## (可选) `api`模块可能需要拦截器👌

* 为什么需要过滤器？

  我们的单体项目如何在不同服务之间传递共享的信息？

  * `TreadLocal`

* 将单体项目拆分为微服务之后，显然，`TreadLocal`不能在多个微服务之间传递共享的信息。

* 这时候，我们需要利用Feign中提供的一个拦截器接口：`RequestInterceptor`

  ```java
  public interface RequestInterceptor {
    /**
     * Called for every request. 
     * Add data using methods on the supplied {@link RequestTemplate}.
     */
    void apply(RequestTemplate template);
  }
  ```

  ```java
  //最后记得要去发起请求的启动类下配置
  //@EnableFeignClients(basePackages = "com.hmall.api.client", defaultConfiguration = DefaultFeignConfig.class)
  //否则DefaultFeignConfig不会生效
  @Bean
  public RequestInterceptor userInfoRequestInterceptor(){
      return new RequestInterceptor() {
          @Override
          public void apply(RequestTemplate template) {
              // 获取登录用户
              Long userId = UserContext.getUser();
              if(userId == null) {
                  // 如果为空则直接跳过
                  return;
              }
              // 如果不为空则放入请求头中，传递给下游微服务
              template.header("user-info", userId.toString());
          }
      };
  }
  ```

---

## 🔗 相关笔记

- [[../网关/nacos]] —— 网关路由与负载均衡（Feign 调用经过网关）
- [[../分布式日志/在你的微服务项目中引入分布式日志技术]] —— 跨服务调用链路的日志追踪
- [[../消息队列/kafka-quickstart]] —— 异步调用替代方案：Kafka 消息队列
- [[Dubbo]] —— Dubbo RPC 框架（另一种微服务调用方式）  