# MinIO 与 Apache Tika：文件上传不能只看后缀 📁

你的 `auth2Demo` 引入了 MinIO 和 Tika。两者解决的问题不同：

- MinIO：保存文件、生成下载地址、管理对象元数据；
- Apache Tika：根据文件内容识别媒体类型、提取文本和元数据。

文件上传链路中，扩展名、请求头 `Content-Type` 和文件真实内容可能互相矛盾，不能只相信其中一个。

## MinIO 客户端初始化

```java
@Configuration
public class MinioConfig {
    @Bean
    MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .build();
    }
}
```

密钥从配置中心或环境变量读取，不要写入 Git，也不要通过日志打印。生产环境还要区分上传、下载和管理权限，应用账号不应拥有整个对象存储的全部管理权限。

## 上传对象

```java
public void upload(String objectName, InputStream input, long size,
                   String contentType) throws Exception {
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket("user-files")
            .object(objectName)
            .stream(input, size, -1)
            .contentType(contentType)
            .build()
    );
}
```

关键点：

- 不要把大文件全部读入 `byte[]`；
- `objectName` 不要直接使用用户传入的文件名，建议使用 UUID 或业务 ID 生成；
- 目录前缀可以按租户、日期和业务类型组织，但不能把它当作真正的权限隔离；
- 记录文件大小、内容类型、哈希、上传人和业务关联 ID；
- 上传成功后再写数据库状态，失败时清理孤儿对象或进入补偿任务。

## 下载：返回短期预签名地址

```java
String url = minioClient.getPresignedObjectUrl(
    GetPresignedObjectUrlArgs.builder()
        .method(Method.GET)
        .bucket("user-files")
        .object(objectName)
        .expiry(10, TimeUnit.MINUTES)
        .build()
);
```

预签名 URL 应设置短过期时间，日志、异常和埋点中不要记录完整 URL。需要强权限控制时，先由业务服务校验用户权限，再生成地址。

## Tika 检测真实类型

```java
Tika tika = new Tika();
String mediaType = tika.detect(inputStream, originalFilename);

Set<String> allowed = Set.of(
    "image/jpeg", "image/png", "application/pdf"
);
if (!allowed.contains(mediaType)) {
    throw new IllegalArgumentException("不支持的文件类型");
}
```

Tika 的结果是“识别结果”，不是授权结果。最终仍要结合业务白名单、文件大小、图片尺寸、压缩炸弹风险和病毒扫描判断是否接收。

## 文件上传安全清单

1. 限制请求体大小和单文件大小。
2. 校验真实媒体类型，不只校验扩展名和 `Content-Type`。
3. 文件名只作为展示信息保存，存储名使用随机值。
4. 阻止路径穿越、双扩展名和可执行脚本上传。
5. 对图片、压缩包、Office 文件按业务要求做内容安全检查。
6. 下载时设置正确的 `Content-Disposition` 和 `X-Content-Type-Options`。
7. 不让对象存储桶默认公开，使用最小权限和短期预签名地址。
8. 数据库记录对象状态：`UPLOADING`、`AVAILABLE`、`DELETED`、`FAILED`。

## 大文件与断点续传

大文件上传建议使用分片：

```text
初始化上传任务
    ↓
客户端分片上传
    ↓
服务端记录已完成分片
    ↓
校验分片数量与哈希
    ↓
合并对象
    ↓
更新数据库状态
```

上传任务要有过期清理机制，避免用户中途关闭页面后遗留大量分片。合并操作也需要幂等，重复请求不能生成多个有效文件记录。

## 一句话总结

> MinIO 负责“放在哪里”，Tika 负责“文件到底是什么”；文件上传必须同时处理流式传输、权限、类型识别、命名、生命周期和清理。
