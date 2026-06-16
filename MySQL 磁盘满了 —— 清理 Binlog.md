# MySQL 磁盘满了 —— 清理 Binlog

## 1. 查看当前正在写的 Binlog

```sql
SHOW MASTER STATUS;
-- 示例：当前写入 binlog.000038
```

## 2. 查看所有 Binlog 列表

```sql
SHOW BINARY LOGS;
```

## 3. 删除历史 Binlog

保留当前正在写的 `binlog.000038`，删除 `binlog.000001 ~ binlog.000037`：

```sql
PURGE BINARY LOGS TO 'binlog.000038';
```

## 4. 查看 Binlog 开启状态

```sql
SHOW VARIABLES LIKE 'log_bin';
```

> 单机 MySQL 无需主从同步，可以考虑直接关闭 Binlog。

## 5. 设置 Binlog 自动过期时间

只保留最近一天的 Binlog（重启后永久生效）：

```sql
SET PERSIST binlog_expire_logs_seconds = 86400;
```