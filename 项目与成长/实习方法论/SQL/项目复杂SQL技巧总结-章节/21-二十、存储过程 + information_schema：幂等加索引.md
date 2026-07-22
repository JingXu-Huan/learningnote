# 二十、存储过程 + information_schema：幂等加索引

## 20.1 场景

项目做归档表初始化时，希望脚本重复执行也不报错。

## 20.2 示例 SQL

```sql
DROP PROCEDURE IF EXISTS add_idx;
DELIMITER $$
CREATE PROCEDURE add_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'order_detail_his'
          AND index_name = 'idx_ret_time'
    ) THEN
        ALTER TABLE order_detail_his
        ADD INDEX idx_ret_time(ret_feedback_time);
    END IF;
END$$
DELIMITER ;
```

## 20.3 学习重点

- 用 `information_schema.statistics` 判断索引是否存在。
- 适合部署脚本、初始化脚本、归档脚本。

---

