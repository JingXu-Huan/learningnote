# 五、CTE 在 INSERT 中的应用

CTE 不仅可以用在 SELECT，还可以用在 `INSERT ... SELECT` 和 `UPDATE` 中。

## INSERT INTO ... WITH ... SELECT

```sql
INSERT INTO home_stat_report_result (...)
WITH src AS (
    SELECT DATE(COALESCE(tka.callback_time, tka.update_time, tka.create_time)) AS stat_date,
           cs.supplier_info_id, cs.id AS customer_service_id, ...
    FROM tk_tc_pool_and_tk_tc_user_total tka
    JOIN tk_tc_pool ttk ON ttk.id = tka.tk_tc_pool_id
    JOIN change_ticket_info cti ON cti.partner_order_id = ttk.order_id
    LEFT JOIN customer_service cs ON ...
    LEFT JOIN supplier_info si ON ...
    WHERE COALESCE(tka.callback_time, tka.update_time, tka.create_time)
          BETWEEN #{startTime} AND #{endTime}
)
SELECT stat_date, -1, 2, object_type, object_id, ...
FROM (
    SELECT stat_date, 0 AS object_type, 0 AS object_id, ... FROM src
    UNION ALL
    SELECT stat_date, 1, supplier_info_id, ... FROM src WHERE supplier_info_id IS NOT NULL
    UNION ALL
    SELECT stat_date, 2, customer_service_id, ... FROM src WHERE customer_service_id IS NOT NULL
) x
GROUP BY stat_date, object_type, object_id, ...
ON DUPLICATE KEY UPDATE ...;
```

> **来源**：`HomeStatReportMapper.xml#insertChangeDaily`

---

