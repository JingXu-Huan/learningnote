# 八、速查表：CTE 模式选择

| 场景 | 推荐模式 | 示例 |
|------|---------|------|
| 先筛范围，再多表聚合 | `scoped_order` + 多个 `*_scope` | TicketOrderMapper#getTodayTicketOrderStat |
| 多步数据加工 | 多层 CTE 流水线 | HomeStatReportMapper#insertIssueDaily |
| 多种数据源合并 | CTE 内 UNION ALL | OrderLogMapper#getTicketSuccessTrend |
| 先聚合再关联维度表 | `*_metrics` CTE | OrderLogMapper#getVSupplierCustomerOrderStatOrderMetrics |
| 两级聚合（订单→供应商） | `grouped_logs` → `*_metrics` | OrderLogMapper#getVSupplierCustomerOrderStatTicketMetrics |
| 动态分组（小时/周/月） | CTE + CASE WHEN 桶索引 | TicketStatChartMapper#selectIssueRows |
| 生成连续序列 | WITH RECURSIVE | HomeStatReportMapper#selectIssueTrendHourly |
| 检查日期覆盖/缺失 | WITH RECURSIVE + EXISTS | BusinessArchiveMapper#countMissingReportCoverage |
| INSERT + 统计 | INSERT INTO ... WITH ... SELECT | HomeStatReportMapper#insertChangeDaily |

---

