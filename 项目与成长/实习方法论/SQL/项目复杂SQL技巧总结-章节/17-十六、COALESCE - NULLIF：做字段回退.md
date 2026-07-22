# 十六、COALESCE / NULLIF：做字段回退

## 16.1 场景

项目里经常遇到字段可能是 `NULL`，也可能是空字符串，要统一回退。

## 16.2 演示表

```sql
supplier(id, company_name, contact_name)
```

## 16.3 示例 SQL

```sql
SELECT id,
       COALESCE(NULLIF(company_name, ''), contact_name, '未知供应商') AS display_name
FROM supplier;
```

## 16.4 学习重点

- `NULLIF(company_name, '')` 先把空串转成 `NULL`。
- `COALESCE` 再按顺序找第一个可用值。
- 这是项目里非常高频的写法。

---

