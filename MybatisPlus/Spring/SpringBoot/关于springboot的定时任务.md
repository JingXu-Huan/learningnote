# `SpringBoot`如何做定时任务？

## 使用注解：

```java
@Scheduled(cron = "0 * * * * ?")
```

此注解含义：

| 位置 | 字段                | 含义           | 当前值 | 解释                              |
| ---- | ------------------- | -------------- | ------ | --------------------------------- |
| 1    | 秒（Seconds）       | 0–59           | `0`    | 每分钟的第 0 秒触发               |
| 2    | 分（Minutes）       | 0–59           | `*`    | 每分钟都触发                      |
| 3    | 时（Hours）         | 0–23           | `*`    | 每小时都触发                      |
| 4    | 日（Day of month）  | 1–31           | `*`    | 每天都触发                        |
| 5    | 月（Month）         | 1–12           | `*`    | 每月都触发                        |
| 6    | 星期（Day of week） | 0–7 或 SUN–SAT | `?`    | 不指定（与第 4 个字段互斥时常用） |

## 在启动类启用：

```java
@SpringBootApplication
@EnableScheduling // 启用定时任务
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```
