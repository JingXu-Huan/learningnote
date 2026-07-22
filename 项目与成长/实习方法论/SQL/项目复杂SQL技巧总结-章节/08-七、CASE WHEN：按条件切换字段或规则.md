# 七、CASE WHEN：按条件切换字段或规则

## 7.1 场景

项目里同一张通知表，可能同时包含出票、改签、退票三种业务，展示字段取值规则不同。

## 7.2 演示表

```sql
notice(id, notice_type, issue_agent, change_agent, refund_agent)
```

## 7.3 示例 SQL

```sql
SELECT id,
       notice_type,
       CASE notice_type
           WHEN 'ISSUE' THEN issue_agent
           WHEN 'CHANGE' THEN change_agent
           WHEN 'REFUND' THEN refund_agent
       END AS agent_code
FROM notice;
```

## 7.4 学习重点

- `CASE WHEN` 适合做“同一列，不同场景取不同值”。
- 在项目里，常见于状态映射、字段回退、优先级排序。

---

