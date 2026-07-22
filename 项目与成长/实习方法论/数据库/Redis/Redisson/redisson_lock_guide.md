# Redisson 分布式锁详细文档

> 本文已按二级标题拆分为独立章节，按需进入对应笔记阅读。

## 章节目录

- [[redisson_lock_guide-章节/01-概述|概述]]
- [[redisson_lock_guide-章节/02-核心概念|核心概念]]
- [[redisson_lock_guide-章节/03-锁的类型|锁的类型]]
- [[redisson_lock_guide-章节/04-实现原理|实现原理]]
- [[redisson_lock_guide-章节/05-使用指南|使用指南]]
- [[redisson_lock_guide-章节/06-最佳实践|最佳实践]]
- [[redisson_lock_guide-章节/07-常见问题|常见问题]]
- [[redisson_lock_guide-章节/08-性能优化|性能优化]]
- [[redisson_lock_guide-章节/09-总结|总结]]

## 相关笔记

- [[项目与成长/开发经验/使用Redis构建分布式锁]] —— 手动实现分布式锁的基础版本（SETNX + UUID）
- [[../数据结构]] —— Redis 数据结构总览（String / Hash / List 等）
- [[阻塞队列]] —— 基于 Redisson RReliableQueue 的消息队列
- [[项目与成长/面经/如何解决缓存和数据库的数据不一致性]] —— 缓存与数据库一致性方案
- [[项目与成长/开发经验/关于使用缓存]] —— Redis 缓存模式与实战经验

