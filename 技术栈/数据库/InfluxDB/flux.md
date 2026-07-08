查询过去3分钟内，最近一条数据：
```
from(bucket: "test08")

 |> range(start: -3m)

 |> filter(fn: (r) => r["_measurement"] == "water_quality")

 |> filter(fn: (r) => r["_field"] == "chlorine")

 |> filter(fn: (r) => r["deviceId"] == "210101001")

 |> last()
```

