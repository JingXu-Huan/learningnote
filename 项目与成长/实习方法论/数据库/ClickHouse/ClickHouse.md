# ClickHouse 快速上手与 MySQL 迁移指南 😎😎😎

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[章节/01-1. 先说结论|1. 先说结论]]
- [[章节/02-2. ClickHouse 到底是什么|2. ClickHouse 到底是什么]]
- [[章节/03-3. ClickHouse 和 MySQL 的核心区别|3. ClickHouse 和 MySQL 的核心区别]]
- [[章节/04-4. 什么场景适合从 MySQL 迁到 ClickHouse|4. 什么场景适合从 MySQL 迁到 ClickHouse]]
- [[章节/05-5. 一个最常见的落地架构|5. 一个最常见的落地架构]]
- [[章节/06-6. Docker 一键启动 ClickHouse|6. Docker 一键启动 ClickHouse]]
- [[章节/07-7. ClickHouse 的几个核心概念|7. ClickHouse 的几个核心概念]]
- [[章节/08-8. MySQL 表结构迁到 ClickHouse 时，思路要变|8. MySQL 表结构迁到 ClickHouse 时，思路要变]]
- [[章节/09-9. 常见数据类型映射|9. 常见数据类型映射]]
- [[章节/10-10. SQL 写法上的变化|10. SQL 写法上的变化]]
- [[章节/11-11. 迁移时最容易踩的坑|11. 迁移时最容易踩的坑]]
- [[章节/12-12. 推荐的迁移路线|12. 推荐的迁移路线]]
- [[章节/13-13. 一组很实用的迁移对照|13. 一组很实用的迁移对照]]
- [[章节/14-14. SpringBoot 怎么接 ClickHouse|14. SpringBoot 怎么接 ClickHouse]]
- [[章节/15-15. 如果你现在项目里想“从 MySQL 改到 ClickHouse”，我建议你这样做|15. 如果你现在项目里想“从 MySQL 改到 ClickHouse”，我建议你这样做]]
- [[章节/16-16. 一个很重要的认知：不是“改数据库”，而是“改数仓思维”|16. 一个很重要的认知：不是“改数据库”，而是“改数仓思维”]]
- [[章节/17-17. 一句话总结|17. 一句话总结]]

## 相关笔记

- [[ClickHouse-Quickstart]] —— Docker 部署、建表、插入与聚合查询练习
- [[技术栈/数据库/MySQL/SQL知识]] —— SQL 基础
- [[技术栈/数据库/MySQL/MySQL索引/索引和索引下推]] —— 理解索引与查询优化
- [[技术栈/数据库/PostgreSQL/PostgreSQL知识库]] —— 另一种常见数据库对比参考
- [[项目与成长/项目选题/IoT-service]] —— 如果你做 IoT，这类场景很适合结合 ClickHouse

