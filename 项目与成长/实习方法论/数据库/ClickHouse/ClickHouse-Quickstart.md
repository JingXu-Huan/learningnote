# ClickHouse Quickstart：Docker 部署、建表与查询 😎😎😎

这篇笔记带你从 Docker 启动 ClickHouse，到创建数据库、建一张表、插入数据并执行聚合查询。做完后，你就能直观看到 ClickHouse 的 SQL 和表结构是什么样。

------

## 1. Docker 启动 ClickHouse

在 PowerShell 中执行：

```powershell
docker run -d `
  --name clickhouse `
  -p 8123:8123 `
  -p 9000:9000 `
  -v clickhouse_data:/var/lib/clickhouse `
  -v clickhouse_logs:/var/log/clickhouse-server `
  -e CLICKHOUSE_DB=learning_ck `
  -e CLICKHOUSE_USER=learning_user `
  -e CLICKHOUSE_PASSWORD='ChangeMe_2026!' `
  clickhouse/clickhouse-server:latest
```

上面三项环境变量会在首次启动时创建练习数据库和账号。`ChangeMe_2026!` 只是示例密码，自己练习时请换成一个你记得住的密码；DataGrip 需要填这个密码，不能留空。

确认容器已启动：

```powershell
docker ps
```

| 端口 | 用途 |
|---|---|
| `8123` | HTTP 接口，可用于浏览器、程序或 `curl` 请求 |
| `9000` | ClickHouse Native TCP 接口，命令行客户端默认使用 |

如果之前已经用**相同账号和密码配置**创建过这个容器，直接启动即可：

```powershell
docker start clickhouse
```

注意：Docker 容器创建后，后面再给 `docker start` 增加环境变量不会生效。若旧容器创建时没有设置或忘记了密码，请使用旧密码连接，或另起一个使用不同端口的练习容器，不要直接删除可能存有数据的旧容器。

------

## 2. 进入 ClickHouse 命令行

```powershell
docker exec -it clickhouse clickhouse-client --user learning_user --password 'ChangeMe_2026!' --database learning_ck
```

成功后会看到类似提示：

```text
ClickHouse client version xxx.
Connecting to localhost:9000 as user learning_user.
Connected to ClickHouse server version xxx.

:)
```

`:)` 就是 ClickHouse 的交互式 SQL 提示符。下面 SQL 直接在这里粘贴执行即可，每条 SQL 最后要加分号。

------

## 3. 确认练习数据库

```sql
SHOW DATABASES;
```

`learning_ck` 是启动容器时由 `CLICKHOUSE_DB` 创建的练习数据库；命令行已经通过 `--database learning_ck` 自动进入它。

如果你没有在 Docker 命令里配置 `CLICKHOUSE_DB`，可以手动执行：

```sql
CREATE DATABASE IF NOT EXISTS learning_ck;

USE learning_ck;
```

------

## 4. 创建第一张表

下面创建一张设备上报指标明细表：设备每上报一次温度和湿度，就插入一行数据。

```sql
CREATE TABLE IF NOT EXISTS device_metric
(
    id UInt64,
    device_id String,
    temperature Float32,
    humidity Float32,
    create_time DateTime
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(create_time)
ORDER BY (device_id, create_time);
```

| 部分 | 含义 |
|---|---|
| `UInt64`、`String`、`Float32`、`DateTime` | 无符号整数、字符串、单精度小数、时间 |
| `ENGINE = MergeTree` | 最常用的明细表引擎，入门时优先使用它 |
| `PARTITION BY toYYYYMM(create_time)` | 按月分区，便于管理和查询时间数据 |
| `ORDER BY (device_id, create_time)` | 数据按设备和时间组织，适合按设备查一段时间的数据 |

查看表和字段定义：

```sql
SHOW TABLES;

DESCRIBE TABLE device_metric;
```

------

## 5. 插入二维表数据

```sql
INSERT INTO device_metric
    (id, device_id, temperature, humidity, create_time)
VALUES
    (1, 'water-001', 25.6, 60.2, '2026-07-20 09:00:00'),
    (2, 'water-001', 26.1, 59.8, '2026-07-20 10:00:00'),
    (3, 'water-002', 24.3, 65.5, '2026-07-20 09:05:00'),
    (4, 'water-002', 24.8, 64.9, '2026-07-21 09:05:00');
```

查询明细：

```sql
SELECT *
FROM device_metric
ORDER BY id;
```

结果大致如下：

| id | device_id | temperature | humidity | create_time |
|---:|---|---:|---:|---|
| 1 | water-001 | 25.6 | 60.2 | 2026-07-20 09:00:00 |
| 2 | water-001 | 26.1 | 59.8 | 2026-07-20 10:00:00 |
| 3 | water-002 | 24.3 | 65.5 | 2026-07-20 09:05:00 |
| 4 | water-002 | 24.8 | 64.9 | 2026-07-21 09:05:00 |

这就是一张普通的二维表：每一行是一条设备上报记录，每一列是一个字段。ClickHouse 在磁盘中会按列组织这些字段，具体可看 [[ClickHouse]] 中“列式存储”的二维表对比。

------

## 6. 执行一次聚合分析

```sql
SELECT
    device_id,
    count() AS report_count,
    round(avg(temperature), 2) AS avg_temperature,
    max(temperature) AS max_temperature
FROM device_metric
WHERE create_time >= '2026-07-20 00:00:00'
GROUP BY device_id
ORDER BY device_id;
```

预期结果：

| device_id | report_count | avg_temperature | max_temperature |
|---|---:|---:|---:|
| water-001 | 2 | 25.85 | 26.1 |
| water-002 | 2 | 24.55 | 24.8 |

这里没有逐行把结果查出来，而是按设备统计了上报次数、平均温度和最高温度。这正是 ClickHouse 的主要使用场景：**存大量明细数据，快速完成范围筛选和聚合统计。**

------

## 7. 常用查看和退出命令

```sql
SHOW DATABASES;
SHOW TABLES;
DESCRIBE TABLE device_metric;
SELECT count() FROM device_metric;
EXIT;
```

如果要查看容器日志：

```powershell
docker logs clickhouse
```

------

## 8. 下一步

- 看 [[ClickHouse]]，理解为什么上面的聚合查询适合列式存储。
- 练习把 `device_metric` 改成订单、日志或埋点表。
- 尝试按 `toDate(create_time)` 分组，统计每天的上报次数。
