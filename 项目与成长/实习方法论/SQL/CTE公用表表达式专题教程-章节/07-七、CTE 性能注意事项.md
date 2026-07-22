# 七、CTE 性能注意事项

## 7.1 MySQL 8.0 的 CTE 物化策略

MySQL 对 CTE 有两种执行策略：
- **MERGE**（合并）：将 CTE 内联到主查询中，类似视图展开
- **MATERIALIZATION**（物化）：先执行 CTE 生成临时表，后续引用临时表

```
一般规则：
- CTE 被引用 1 次 → 倾向 MERGE（和子查询一样快）
- CTE 被引用多次 → 倾向物化（避免重复执行）
```

## 7.2 项目中的性能优化技巧

1. **scoped_order 先圈范围**：在大表上只做一次索引扫描，后续 CTE 用 ID 关联小结果集
2. **RECURSIVE 有上限**：MySQL 默认递归深度限制 1000，日期序列不会超过 365，小时序列只有 24
3. **CTE 内用 JOIN 代替 IN 子查询**：`JOIN scoped_orders` 比 `WHERE id IN (SELECT ...)` 更高效
4. **DISTINCT 放在 scoped_orders 层**：避免后续 CTE 处理重复数据

## 7.3 什么时候不该用 CTE

- **简单查询**：只有一张表或简单 JOIN，直接写就好
- **相关子查询**：需要"对每一行执行子查询"的场景，CTE 无法替代
- **MySQL 5.7 及以下**：不支持 CTE，只能用子查询或临时表

---

