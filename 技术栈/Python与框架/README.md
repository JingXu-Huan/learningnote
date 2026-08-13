# Python 与 FastAPI 学习笔记 🐍

> 本目录是一条从 Python 语言基础到 FastAPI 后端项目落地的完整学习路线。示例以 Python 3.10+ 为兼容基线，优先采用当前主流写法；第一次学习时按编号顺序阅读，复习时可按主题跳转。

## 学习路线

| 阶段 | 章节 | 学完后能够做什么 |
| --- | --- | --- |
| 语言入门 | [01-Python基础](./01-Python基础.md) | 配置环境，读写基础语法，独立完成小程序 |
| 容器与算法 | [02-核心数据结构](./02-核心数据结构.md) | 正确选择容器，理解可变性、哈希与复杂度 |
| 抽象能力 | [03-函数与面向对象](./03-函数与面向对象.md) | 使用函数、生成器、类型注解和类组织代码 |
| 工程基础 | [04-模块异常与工程化](./04-模块异常与工程化.md) | 拆包、处理异常与文件，建立可维护项目 |
| 工具箱 | [05-常用内置函数与标准库](./05-常用内置函数与标准库.md) | 优先使用标准库解决常见任务 |
| 生态 | [06-常用第三方库](./06-常用第三方库.md) | 按场景选择 HTTP、校验、ORM、测试等库 |
| 并发模型 | [07-并发与异步编程](./07-并发与异步编程.md) | 区分线程、进程和协程，写出可靠异步代码 |
| Web API | [08-FastAPI后端开发](./08-FastAPI后端开发.md) | 掌握路由、依赖注入、校验、生命周期与安全基础 |
| 项目落地 | [09-FastAPI项目实战](./09-FastAPI项目实战.md) | 完成分层的任务管理 API、数据库迁移和 JWT 认证 |
| 上线保障 | [10-测试质量与部署](./10-测试质量与部署.md) | 测试、检查、容器化并规划生产部署 |
| 巩固复习 | [11-练习题与面试复习](./11-练习题与面试复习.md) | 用递进项目和高频问题检查知识盲点 |

------

## 推荐学习方式

### 第一遍：能运行

1. 自己输入示例，不要只复制。
2. 每章至少完成一个小练习，并主动制造一次错误观察 traceback。
3. 遇到陌生 API，先读函数签名和官方文档，再搜索二手答案。

### 第二遍：能解释

重点回答“为什么”：为什么字典键必须可哈希、为什么不要使用可变默认参数、为什么异步接口中不能直接执行阻塞 I/O、为什么请求模型和数据库模型需要分离。

### 第三遍：能落地

以第 09 章的任务管理 API 为主线，逐步加入用户权限、缓存、消息队列、可观测性和 CI。每次只加入一个变量，并用测试保护已有行为。

------

## 环境约定

在 Windows PowerShell 中可以这样准备环境：

```powershell
py -3 --version
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
```

macOS / Linux 使用：

```shell
python3 --version
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
```

建议始终写 `python -m pip`，这样可以明确 `pip` 属于当前解释器。项目依赖应放进虚拟环境，不要安装到系统 Python。

------

## 版本与资料边界

- 语言示例以 Python 3.10+ 为最低基线，使用 `X | None`、`match` 等现代语法。
- Web 部分采用 FastAPI、Pydantic v2 和 SQLAlchemy 2.x 风格；看到 `orm_mode`、`from_orm()`、`session.query()` 等旧教程时要先核对版本。
- “内置函数”是不需要 `import` 的函数；“标准库”随 Python 发布但通常需要 `import`；“第三方库”需要单独安装。
- 文档用于建立知识体系，精确参数和版本变化以官方文档为准。

常用官方入口：

- [Python 官方教程](https://docs.python.org/3/tutorial/)
- [Python 标准库](https://docs.python.org/3/library/)
- [Python Packaging User Guide](https://packaging.python.org/)
- [FastAPI 官方教程](https://fastapi.tiangolo.com/tutorial/)
- [Pydantic 官方文档](https://docs.pydantic.dev/latest/)
- [SQLAlchemy 2.x 官方文档](https://docs.sqlalchemy.org/en/20/)

------

## 全路线验收项目

最终项目是一套“任务管理 API”，至少应具备：

- 用户注册、登录和 JWT 身份认证；
- 任务的增删改查、分页、筛选和权限隔离；
- PostgreSQL（学习阶段可先用 SQLite）与 Alembic 迁移；
- Pydantic 请求/响应模型和统一错误响应；
- 单元测试、API 集成测试、日志和健康检查；
- 环境变量配置、Docker 镜像与生产部署清单。

达到这些目标后，再学习 Redis、Celery / Dramatiq、消息队列和微服务会更稳，因为这些组件解决的是规模问题，不是基础代码组织问题。

