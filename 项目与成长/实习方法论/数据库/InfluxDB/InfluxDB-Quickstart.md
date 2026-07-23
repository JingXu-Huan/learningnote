# InfluxDB Quickstart：Docker 部署、写入与 Flux 查询 😎😎😎

这篇笔记带你从 Docker 启动 InfluxDB 2.x，到创建 bucket、写入一条时序数据，再用 Flux 查出最新值和时间窗口聚合结果。做完后，你就能直观看到 InfluxDB 的基本使用方式。

------

## 1. Docker 启动 InfluxDB 2.x

仓库里现成的启动命令如下：

```powershell
docker run --hostname=0fd99081dcb6 --env=DOCKER_INFLUXDB_INIT_USERNAME=jingxu --env=DOCKER_INFLUXDB_INIT_PASSWORD=jingxu202430904 --env=DOCKER_INFLUXDB_INIT_ORG=ncwu --env=DOCKER_INFLUXDB_INIT_BUCKET=water --env=DOCKER_INFLUXDB_INIT_MODE=setup --env=PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin --env=GOSU_VER=1.19 --env=INFLUXDB_VERSION=2.8.0 --env=INFLUXDB_PR=-2 --env=INFLUXDB_PV=2.8.0-2 --env=INFLUX_CLI_VERSION=2.7.5 --env=INFLUX_CONFIGS_PATH=/etc/influxdb2/influx-configs --env=INFLUXD_INIT_PORT=9999 --env=INFLUXD_INIT_PING_ATTEMPTS=600 --env=DOCKER_INFLUXDB_INIT_CLI_CONFIG_NAME=default --volume=/etc/influxdb2 --volume=/var/lib/influxdb2 --network=bridge -p 8086:8086 --restart=no --runtime=runc -d influxdb:2
```

上面几个环境变量的意思可以先粗略理解成：

| 环境变量 | 作用 |
|------|------|
| `DOCKER_INFLUXDB_INIT_USERNAME` | 初始用户名 |
| `DOCKER_INFLUXDB_INIT_PASSWORD` | 初始密码 |
| `DOCKER_INFLUXDB_INIT_ORG` | 组织名 |
| `DOCKER_INFLUXDB_INIT_BUCKET` | 默认 bucket |
| `DOCKER_INFLUXDB_INIT_MODE=setup` | 首次启动时自动初始化 |

确认容器已启动：

```powershell
docker ps
```

| 端口 | 用途 |
|---|---|
| `8086` | InfluxDB HTTP 接口，也是 Web UI 和客户端常用入口 |

如果你之前已经用相同配置创建过容器，直接启动即可：

```powershell
docker start influxdb
```

注意：Docker 容器创建后，后面再给 `docker start` 增加环境变量不会生效。若旧容器的初始化信息忘了，就用原来的配置连接，或者重新开一个新的练习容器。

------

## 2. 进入 Influx CLI

如果容器里已经初始化好了，可以尝试进入 CLI：

```powershell
docker exec -it influxdb influx setup
```

如果你已经有 token，也可以直接使用 Influx CLI 或 Web UI 创建查询配置。对于新手来说，更建议先通过 Web UI 完成 bucket 和 token 的确认，再开始写入和查询。

------

## 3. 确认组织和 bucket

在 Web UI 中确认：

- `org = ncwu`
- `bucket = water`

如果你是第一次接触 InfluxDB，先把它理解成“一个组织下面放多个 bucket，bucket 里存时序数据”。

------

## 4. 写入一条时序数据

下面用 line protocol 的形式举个最简单的例子：

```text
water_quality,deviceId=210101001,area=west chlorine=0.38,ph=7.2 1710000000000000000
```

这条数据表示：某个设备在某个时间点上报了余氯和酸碱度。

如果拆开看：

- `water_quality` 是 measurement
- `deviceId`、`area` 是 tag
- `chlorine`、`ph` 是 field
- 最后是纳秒级时间戳

你可以把它理解成“带标签和指标值的时间点记录”。

------

## 5. 用 Flux 查最新一条数据

下面是仓库里已有的查询思路，适合查询某个设备最近 3 分钟内的最新记录：

```text
from(bucket: "water")
  |> range(start: -3m)
  |> filter(fn: (r) => r["_measurement"] == "water_quality")
  |> filter(fn: (r) => r["_field"] == "chlorine")
  |> filter(fn: (r) => r["deviceId"] == "210101001")
  |> last()
```

这段查询的阅读顺序是：

1. 从 `water` bucket 取数据
2. 只看最近 3 分钟
3. 过滤出 `water_quality`
4. 只保留 `chlorine`
5. 只查某个设备
6. 取最后一条

------

## 6. 查一个时间范围内的平均值

```text
from(bucket: "water")
  |> range(start: -1h)
  |> filter(fn: (r) => r["_measurement"] == "water_quality")
  |> filter(fn: (r) => r["_field"] == "chlorine")
  |> filter(fn: (r) => r["deviceId"] == "210101001")
  |> aggregateWindow(every: 10m, fn: mean)
```

这个查询适合看一段时间内的趋势，不是只看最后一个点。

------

## 7. 常用查看命令

如果你用的是 Web UI，可以先找这些页面：

- Data Explorer：执行 Flux 查询
- Buckets：查看 bucket
- Load Data：手动写入数据
- Tokens：管理访问令牌

如果你用 CLI 或 API，常见关注点是：

- org
- bucket
- token
- query
- write

------

## 8. 下一步

- 看 [[InfluxDB]]，理解为什么时序数据要这么建模。
- 尝试把 `water_quality` 改成温湿度、设备在线心跳或接口耗时。
- 给查询加上 `aggregateWindow`，看看时间窗口聚合的效果。