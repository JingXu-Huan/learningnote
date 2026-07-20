# Actuator 与 Micrometer：让服务可观测 📈

你的 `Campus-Water-IQ`、`KafkaDemo` 和 `auth2Demo` 都引入了 Actuator 或 Micrometer 相关能力。它们的作用不是“多暴露几个接口”，而是把服务状态变成可监控、可告警的数据。

- Spring Boot Actuator：提供健康检查、信息、指标、环境等管理端点；
- Micrometer：用统一 API 记录 Counter、Gauge、Timer 等指标，并适配 Prometheus 等监控系统；
- Micrometer Tracing：为请求和消息传递 TraceId、SpanId 等链路上下文。

## 最小化暴露端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

Actuator 端点默认位于 `/actuator/{id}`，但生产环境不应把所有端点无差别暴露到公网。`env`、`configprops`、`beans`、`mappings` 等端点可能泄露配置、依赖和内部结构。

## 存活与就绪不是一回事

- Liveness：进程本身是否还能工作，失败通常需要重启；
- Readiness：当前实例是否准备好接收流量，数据库、消息队列或关键依赖不可用时可以暂时摘流量。

不要把所有外部依赖都塞进 Liveness，否则一个下游短暂故障可能导致所有实例不断重启。Kubernetes 场景通常更关注 Readiness 的准确性。

## Micrometer 记录业务指标

```java
@Component
@RequiredArgsConstructor
public class WaterMetrics {
    private final MeterRegistry registry;

    public void recordAlarm(String level) {
        Counter.builder("water_alarm_total")
            .tag("level", level)
            .description("水务告警数量")
            .register(registry)
            .increment();
    }
}
```

更高频的指标建议在初始化时注册，避免每次调用都创建或查找对象。业务指标命名要稳定，标签值要低基数。

## 标签基数是隐藏炸弹

```java
// ❌ 不要把 userId、orderId、设备完整序列号作为 tag
Counter.builder("request_total")
    .tag("userId", userId.toString())
    .register(registry);
```

高基数标签会产生大量时间序列，导致监控系统内存、存储和查询压力上升。标签更适合使用有限集合，如接口名、状态码、业务类型和告警等级。用户 ID、请求 ID、设备 ID 放日志或 Trace 中。

## Timer 比手动计算耗时更可靠

```java
Timer timer = Timer.builder("remote_call_duration")
    .tag("service", "device-service")
    .register(registry);

timer.record(() -> deviceClient.query(deviceId));
```

Timer 可以统计调用次数、总耗时和分布。对外部接口还应同时记录成功率、超时数、状态码和重试次数，单独看平均耗时容易漏掉长尾。

## 自定义健康检查

```java
@Component
public class DeviceServiceHealth implements HealthIndicator {
    @Override
    public Health health() {
        boolean reachable = checkDeviceService();
        return reachable
            ? Health.up().build()
            : Health.down().withDetail("reason", "unreachable").build();
    }
}
```

健康检查本身也不能无限等待。调用外部依赖时设置短超时、缓存最近结果或使用异步探测，避免 `/health` 请求反过来拖垮线程池。

## Actuator 安全配置

- 管理端口可以与业务端口分离；
- 端点必须经过 Spring Security 或网关鉴权；
- 只暴露必要端点，尤其谨慎开放 `shutdown`、`env`、`loggers`；
- 对健康检查可以允许基础匿名访问，但详细组件信息要受保护；
- 配置脱敏，确认密码、Token、数据库 URL 不会出现在响应中；
- 监控接口也要有访问日志和限流。

## 观测三件套

```text
Logs      发生了什么？
Metrics   发生了多少？趋势如何？
Traces    一次请求经过了哪些服务？卡在哪里？
```

排查一个接口变慢的问题时，先用 Metrics 判断范围，再用 Trace 找到具体依赖，最后结合带 TraceId 的日志查看错误上下文。不要只在业务代码里打大量日志来代替指标和链路追踪。

## 一句话总结

> Actuator 暴露服务状态，Micrometer 记录可聚合指标，Tracing 串起跨服务请求；三者都必须同时考虑信息泄露、标签基数、超时和访问权限。
