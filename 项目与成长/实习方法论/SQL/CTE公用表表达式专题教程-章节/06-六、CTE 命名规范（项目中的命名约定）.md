# 六、CTE 命名规范（项目中的命名约定）

| CTE 名称 | 含义 | 出现文件 |
|-----------|------|---------|
| `scoped_order` / `scoped_orders` | 圈出的订单 ID 范围 | TicketOrderMapper, TicketStatChartMapper, OrderLogMapper |
| `base_logs` | 基础日志（含状态判定） | HomeStatReportMapper |
| `ti_scope` / `ticket_scope` | 票数聚合子查询 | 多个文件 |
| `pi_scope` / `passenger_scope` | 金额聚合子查询 | HomeStatReportMapper, TicketOrderMapper |
| `src` | 汇总后的数据源 | HomeStatReportMapper |
| `filtered_logs` | 筛选后的日志（含时间回退） | OrderLogMapper |
| `order_metrics` | 订单维度的指标聚合 | OrderLogMapper |
| `grouped_logs` | 按供应商/客服+订单分组的日志 | OrderLogMapper |
| `ticket_metrics` | 票数维度的指标聚合 | OrderLogMapper |
| `hours` / `days` / `dates` | 时间序列（RECURSIVE） | HomeStatReportMapper, BusinessArchiveMapper |

**命名规律**：
- `*_scope`：表示"在某个范围内的数据"
- `*_metrics`：表示"已聚合的指标"
- `src`：最终查询前的数据源
- 序列类用直观的名词：`hours`、`days`、`dates`

---

