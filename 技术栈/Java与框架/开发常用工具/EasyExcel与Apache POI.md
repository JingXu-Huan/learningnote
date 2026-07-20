# EasyExcel 与 Apache POI：Excel 导入导出 📊

你的 `auth2Demo` 使用了 EasyExcel，`sky` 使用了 Apache POI。两者都能处理 Excel，但实际开发中更重要的是先判断文件规模、格式复杂度和交互方式。

## 怎么选

| 场景 | 推荐 | 原因 |
|------|------|------|
| 大量数据导入导出 | EasyExcel | 更适合按行读取，降低内存压力 |
| 简单模板导出 | EasyExcel | 注解和监听器上手快 |
| 复杂样式、公式、合并单元格 | Apache POI | API 控制粒度更细 |
| 修改已有复杂模板 | Apache POI | 对工作簿、Sheet、Cell 控制更完整 |
| 小文件临时处理 | JDK + 任一工具 | 优先保证代码可读性 |

## EasyExcel 导入：监听器不要堆积数据

```java
public class UserDataListener
    extends AnalysisEventListener<UserRow> {

    private final List<UserRow> buffer = new ArrayList<>();

    @Override
    public void invoke(UserRow row, AnalysisContext context) {
        validate(row, context.readRowHolder().getRowIndex());
        buffer.add(row);

        if (buffer.size() >= 500) {
            saveBatch(buffer);
            buffer.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!buffer.isEmpty()) {
            saveBatch(buffer);
            buffer.clear();
        }
    }
}
```

关键点：

- 按批次写数据库，不要把全部行保存到一个 List；
- 校验错误要记录行号、字段和原因；
- 批量写入失败时要明确重试、回滚或生成错误报告；
- 大文件导入应放到异步任务中，接口只返回任务编号；
- 文件上传后先校验大小、类型和权限，再交给解析器。

## 导出：避免一次性拼接超大结果集

导出大数据时，先分页或游标读取数据库，再逐批写入 Excel。不要把数据库查询结果、导出行和 HTTP 响应全部一次性放进内存。

建议流程：

```text
校验导出条件
    ↓
创建导出任务
    ↓
分页 / 游标读取数据
    ↓
逐批写入临时文件
    ↓
上传对象存储或生成短期下载地址
    ↓
通知用户下载
```

如果使用 `Campus-Water-IQ` 或 `auth2Demo` 这类微服务项目，导出文件可以交给对象存储，避免业务服务长期保留大文件。

## POI 的内存陷阱

```java
// XSSFWorkbook 会将工作簿结构加载到内存
try (InputStream in = file.getInputStream();
     Workbook workbook = WorkbookFactory.create(in)) {
    Sheet sheet = workbook.getSheetAt(0);
    // 处理小文件
}
```

对大文件要谨慎使用 `XSSFWorkbook`。导出时可以考虑 SXSSF 的流式写法，读取时采用事件模型或分批处理，并限制上传大小和处理时间。

## 数据校验与安全

- 表头不能只按列下标信任，建议校验表头名称和版本；
- 日期、金额、枚举、手机号等字段按业务规则校验；
- 错误行应返回可定位的信息，而不是只返回“导入失败”；
- 防止 CSV/Excel 公式注入：以 `=`, `+`, `-`, `@` 开头的用户输入导出时要按业务进行转义；
- 文件名和路径不能直接拼接用户输入，防止路径穿越；
- 下载地址应设置过期时间和访问权限，日志中不要打印完整签名 URL。

## 事务边界

Excel 导入通常包含“解析、校验、批量写库、错误记录”多个阶段。不要简单地用一个超长事务包住整个文件：

- 小文件可以在一个事务内完成；
- 大文件按批次提交，并记录成功批次和失败行；
- 如果业务要求全部成功，使用导入临时表 + 校验通过后一次性切换；
- 导入接口必须考虑重复提交和幂等键。

## 一句话总结

> EasyExcel 更适合大数据量的按行处理，POI 更适合复杂模板和细粒度控制；无论用哪个库，都要先解决内存、校验、事务、文件安全和异步化问题。
