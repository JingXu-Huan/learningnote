# Flux 片段速查 😎😎😎

这是一页轻量速查，完整教程请看：

- [[InfluxDB]]
- [[InfluxDB-Quickstart]]

------

## 查询过去 3 分钟内最近一条数据

```text
from(bucket: "test08")

 |> range(start: -3m)

 |> filter(fn: (r) => r["_measurement"] == "water_quality")

 |> filter(fn: (r) => r["_field"] == "chlorine")

 |> filter(fn: (r) => r["deviceId"] == "210101001")

 |> last()
```

