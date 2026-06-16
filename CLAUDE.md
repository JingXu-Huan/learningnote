# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库性质

这是一个**个人学习笔记 / 知识库仓库**（非可构建运行的软件项目）。笔记以 Markdown 编写，主要面向后端 Java 技术栈，用于学习整理与面试复习。

- 远程仓库：`https://github.com/JingXu-Huan/learningnote.git`
- 默认分支：`master`
- 维护者背景：华北水利水电大学（NCWU）软件工程本科生，后端 / IoT 方向（见根目录 `👋 你好，我是 JingXu.md`）
- 配套实践项目：`Campus-Water-IQ`（智慧水务 IoT 平台，Spring Cloud Alibaba + InfluxDB + RocketMQ）

## 目录结构

按**技术主题**组织，每个一级目录对应一个主题领域：

| 目录 | 内容 |
| --- | --- |
| `Java/` | Java 语法糖、Lambda、Stream API、反射、集合 API、源码分析、项目优化方案 |
| `IO流/` | Java I/O 相关笔记 |
| `多线程/` | 线程池（`线程池七大核心参数.md`）等并发内容 |
| `MySQL/` | 索引、SQL、分页/动态/多表查询、备份、面试题 |
| `Mbatis/`、`MybatisPlus/` | MyBatis / MyBatis-Plus 笔记 |
| `Redis/`、`Redis/Redisson/` | Redis 数据结构、Redisson 分布式锁（`redisson_lock_guide.md`）、阻塞队列 |
| `SpringBoot/` | AOP、`@PostConstruct`、定时任务、事务、参数注解、其它注解 |
| `微服务/` | 分布式日志、服务间调用、消息队列、网关、配置中心 |
| `计算机网络/` | 系统化的网络知识总结（含目录锚点） |
| `面经/` | 真实面试记录（美团、九识智能 等） |
| `开发经验/` | 缓存使用、Redis 分布式锁、苍穹外卖项目复盘 |
| `开发常用工具/` | MapStruct 等 |
| `其它/` | ELK、日志级别、字段注入问题、文件上传、`Collection/` 学习路线 PDF |
| `项目选题/`、`实习方法论/` | 选题思路与实习方法论 |

仓库根的 `计算机设计大赛.pdf` 与 `景旭的编程笔记/` 内的 PDF（Java 学习路线）作为辅助材料保留。

## 笔记风格约定

写或改笔记时遵循以下约定，保持仓库整体一致性：

- **语言**：中文为主，专有名词（API 名、类名、框架名）保留英文。
- **格式**：GitHub Flavored Markdown。标题层级 `#` → `###`，分隔线使用 `------`。
- **代码块**：Java 用 `java`，SQL 用 `sql`，Flux / InfluxQL 用纯文本（参见根目录 `flux.md`）。
- **表情符号**：标题中常见 `😎😎😎`、`😘😘🎉`、`😢🎉` 等装饰性 emoji，新增笔记可沿用，但不要在严肃内容（如面经、公司名）里滥用。
- **空标题文件**：常见 `Untitled.md` 作为草稿占位，新建笔记应直接起一个语义化文件名。
- **目录式笔记**：长笔记（如 `计算机网络/计算机网络知识总结.md`）使用带锚点的目录（`- [标题](#锚点)`），新增时同步维护目录。
- **学习路线 / 攻略 PDF**：保留在 `其它/`，不要移动或删除（属于公开资料）。

## Git 工作流

- 单分支：`master`，直接提交到 `master`。
- 提交信息简短中文，如 `新增线程池和部分面经。`、`新增 MySQL 磁盘满了清理 Binlog 的文档`。
- 该仓库**没有**构建、测试、lint、format 等脚本——不要尝试 `npm test`、`mvn build` 之类。
- 修改笔记时如涉及大段重写，提交信息简要说明新增 / 删除 / 更新即可。

## 操作注意事项

- 仓库名带中文路径（`景旭的编程笔记`），在 Windows + Git Bash 下用引号包裹路径。
- 笔记中存在 Obsidian 风格的双向链接与 emoji 装饰是**有意为之**，不要当作"格式错误"清理。
- `面经/` 与 `开发经验/` 内的内容带有个人经历与公司信息，修改时注意保留原意，避免误删面试题细节。
- 新增主题时优先在现有目录下添加 `.md` 文件；只有当主题与现有目录都不匹配时才新建一级目录。
