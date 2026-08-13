# 09 - FastAPI 项目实战：任务管理 API 😘🎉

> 本章从空目录搭建一个带用户认证、任务 CRUD、分页和数据库迁移的 API。基线为 **Python 3.10+、Pydantic v2、SQLAlchemy 2.x 异步 ORM、PostgreSQL、Alembic、PyJWT 与 pwdlib**。

[← 上一章：FastAPI 后端开发](./08-FastAPI后端开发.md) ｜ [返回学习路线](./README.md) ｜ [下一章：测试、质量与部署 →](./10-测试质量与部署.md)

------

## 学习目标

完成本章后，你应该能够：

- 按 router、service、repository 分层组织 FastAPI 项目。
- 使用 Pydantic Settings 从环境变量加载配置与密钥。
- 使用 SQLAlchemy 2.x `AsyncSession`、`select()` 和显式事务。
- 设计数据库模型与输入 / 输出 Schema，避免泄露密码哈希。
- 使用 Alembic 生成并执行可审计的数据库迁移。
- 使用 pwdlib 推荐算法存储密码，并签发、验证 JWT。
- 实现统一错误模型、所有者权限检查和分页响应。
- 理解一次请求从路由到数据库再回到响应的完整流程。

------

## 目录

- [1. 需求与技术选择](#1-需求与技术选择)
- [2. 项目目录](#2-项目目录)
- [3. 安装依赖与环境配置](#3-安装依赖与环境配置)
- [4. 配置模块](#4-配置模块)
- [5. 异步数据库会话](#5-异步数据库会话)
- [6. SQLAlchemy 2.x 数据库模型](#6-sqlalchemy-2x-数据库模型)
- [7. Pydantic v2 Schema](#7-pydantic-v2-schema)
- [8. 错误模型与异常](#8-错误模型与异常)
- [9. Repository 数据访问层](#9-repository-数据访问层)
- [10. 密码哈希与 JWT](#10-密码哈希与-jwt)
- [11. Service 业务层](#11-service-业务层)
- [12. 认证依赖](#12-认证依赖)
- [13. Router 接口层](#13-router-接口层)
- [14. 组装应用与 lifespan](#14-组装应用与-lifespan)
- [15. Alembic 数据库迁移](#15-alembic-数据库迁移)
- [16. 请求的完整流转过程](#16-请求的完整流转过程)
- [17. 启动与手工验证](#17-启动与手工验证)
- [18. 可以继续演进的方向](#18-可以继续演进的方向)
- [19. 本章检查清单](#19-本章检查清单)
- [20. 官方资料](#20-官方资料)

------

## 1. 需求与技术选择

### 1.1 功能范围

本项目实现以下能力：

- 用户注册。
- 使用用户名、密码换取 Bearer Token。
- 用户查询自己的资料。
- 创建、查询、分页、更新和删除自己的任务。
- 不允许用户读取或修改其他用户的任务。
- 统一输出业务错误结构。
- 提供存活与就绪检查。

接口约定：

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | 注册 | 否 |
| `POST` | `/api/v1/auth/token` | 获取 Token | 否 |
| `GET` | `/api/v1/auth/me` | 当前用户 | 是 |
| `POST` | `/api/v1/tasks` | 创建任务 | 是 |
| `GET` | `/api/v1/tasks` | 分页查询自己的任务 | 是 |
| `GET` | `/api/v1/tasks/{id}` | 查询自己的任务 | 是 |
| `PATCH` | `/api/v1/tasks/{id}` | 部分更新自己的任务 | 是 |
| `DELETE` | `/api/v1/tasks/{id}` | 删除自己的任务 | 是 |
| `GET` | `/health/live` | 进程存活检查 | 否 |
| `GET` | `/health/ready` | 数据库就绪检查 | 否 |

### 1.2 为什么选择这些库

| 库 | 用途 |
| --- | --- |
| FastAPI | HTTP API、依赖注入、OpenAPI |
| Pydantic / pydantic-settings | 数据校验与配置加载 |
| SQLAlchemy 2.x | ORM、事务与异步数据库访问 |
| asyncpg | PostgreSQL 异步驱动 |
| Alembic | 数据库版本迁移 |
| pwdlib + Argon2 | 密码哈希与验证 |
| PyJWT | JWT 编码与解码 |

这里刻意不使用旧的 `session.query()`、`orm_mode`、`from_orm()` 和 `@app.on_event()`。

------

## 2. 项目目录

最终目录如下：

```text
task-api/
├── .env                         # 本地密钥，不提交
├── .env.example                 # 可提交的变量模板
├── .gitignore
├── alembic.ini
├── pyproject.toml
├── alembic/
│   ├── env.py
│   ├── script.py.mako
│   └── versions/
└── app/
    ├── __init__.py
    ├── main.py
    ├── api/
    │   ├── __init__.py
    │   ├── dependencies.py
    │   └── routers/
    │       ├── __init__.py
    │       ├── auth.py
    │       ├── health.py
    │       └── tasks.py
    ├── core/
    │   ├── __init__.py
    │   ├── config.py
    │   ├── errors.py
    │   └── security.py
    ├── db/
    │   ├── __init__.py
    │   ├── base.py
    │   └── session.py
    ├── models/
    │   ├── __init__.py
    │   ├── enums.py
    │   ├── task.py
    │   └── user.py
    ├── repositories/
    │   ├── __init__.py
    │   ├── task.py
    │   └── user.py
    ├── schemas/
    │   ├── __init__.py
    │   ├── auth.py
    │   ├── common.py
    │   ├── task.py
    │   └── user.py
    └── services/
        ├── __init__.py
        ├── auth.py
        └── task.py
```

`__init__.py` 可以为空。分层职责：

```text
Router：HTTP 参数、状态码、响应模型
  ↓
Service：业务规则、授权、事务边界
  ↓
Repository：SQLAlchemy 查询与持久化
  ↓
Database
```

依赖方向保持单向。Repository 不抛 `HTTPException`，Service 不接触 `Request`，这样业务代码可以脱离 Web 框架测试。

------

## 3. 安装依赖与环境配置

### 3.1 pyproject.toml

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[project]
name = "task-api"
version = "0.1.0"
description = "FastAPI task management example"
requires-python = ">=3.10"
dependencies = [
    "fastapi[standard]",
    "pydantic-settings",
    "sqlalchemy[asyncio]>=2.0,<3.0",
    "asyncpg",
    "alembic",
    "pwdlib[argon2]",
    "PyJWT",
    "email-validator",
]

[project.optional-dependencies]
dev = [
    "pytest",
    "pytest-asyncio",
    "pytest-cov",
    "httpx",
    "aiosqlite",
    "asgi-lifespan",
    "ruff",
    "mypy",
]

[tool.hatch.build.targets.wheel]
packages = ["app"]

[tool.ruff]
target-version = "py310"
line-length = 100

[tool.ruff.lint]
select = ["E", "F", "I", "B", "UP", "ASYNC"]

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]
```

创建并安装环境：

```powershell
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
```

### 3.2 .env.example

```dotenv
APP_NAME=Task API
ENVIRONMENT=local
DEBUG=true
DATABASE_URL=postgresql+asyncpg://task_user:change_me@127.0.0.1:5432/task_db
JWT_SECRET=replace-with-at-least-32-random-characters
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
CORS_ORIGINS=["http://localhost:5173"]
```

`.gitignore` 至少包含：

```gitignore
.venv/
.env
__pycache__/
*.py[cod]
.pytest_cache/
.mypy_cache/
.ruff_cache/
```

生成本地 JWT 密钥：

```powershell
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

把输出写入本机 `.env` 的 `JWT_SECRET`。不要把真实密钥放进源码、镜像、Git 历史或文档示例。

------

## 4. 配置模块

`app/core/config.py`：

```python
from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_name: str = "Task API"
    environment: Literal["local", "test", "staging", "production"] = "local"
    debug: bool = False

    database_url: str
    jwt_secret: SecretStr = Field(min_length=32)
    jwt_algorithm: Literal["HS256", "HS384", "HS512"] = "HS256"
    access_token_expire_minutes: int = Field(default=30, ge=1, le=1440)
    cors_origins: list[str] = Field(default_factory=list)


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
```

要点：

- `SecretStr` 的普通字符串表示不会直接显示真实值。
- 配置校验失败应让进程启动失败，不要带着错误配置继续运行。
- `@lru_cache` 让进程内复用同一个不可频繁变化的配置对象。
- 生产环境通常由容器平台或密钥管理服务注入环境变量，不依赖镜像中的 `.env`。

如果测试要在同一进程切换环境变量，需要执行 `get_settings.cache_clear()`，并避免其他模块在导入时提前固化旧配置。

------

## 5. 异步数据库会话

### 5.1 声明式基类

`app/db/base.py`：

```python
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass
```

### 5.2 Engine、会话工厂与请求依赖

`app/db/session.py`：

```python
from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import settings

engine = create_async_engine(
    settings.database_url,
    echo=settings.debug,
    pool_pre_ping=True,
)

AsyncSessionFactory = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False,
)


async def get_db_session() -> AsyncIterator[AsyncSession]:
    async with AsyncSessionFactory() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
```

关键边界：

- `AsyncSessionFactory` 是可复用的工厂；`AsyncSession` 实例是请求级状态。
- 同一个 `AsyncSession` 不能被多个并发 task 共同使用。
- service 明确 `commit()`；异常时依赖负责兜底 `rollback()`，上下文结束后会话关闭。
- `expire_on_commit=False` 允许提交后读取已加载属性，但仍应避免异步场景中的隐式 lazy loading。

------

## 6. SQLAlchemy 2.x 数据库模型

### 6.1 枚举

`app/models/enums.py`：

```python
from enum import Enum


class TaskStatus(str, Enum):
    TODO = "todo"
    IN_PROGRESS = "in_progress"
    DONE = "done"
```

### 6.2 用户模型

`app/models/user.py`：

```python
from datetime import datetime

from sqlalchemy import Boolean, DateTime, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
```

数据库只保存密码哈希，永远不保存明文密码或“可解密密码”。

### 6.3 任务模型

`app/models/task.py`：

```python
from datetime import datetime

from sqlalchemy import DateTime, Enum as SAEnum, ForeignKey, Index, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base
from app.models.enums import TaskStatus


class Task(Base):
    __tablename__ = "tasks"
    __table_args__ = (
        Index("ix_tasks_owner_created", "owner_id", "created_at"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    owner_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"),
        index=True,
    )
    title: Mapped[str] = mapped_column(String(100))
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[TaskStatus] = mapped_column(
        SAEnum(
            TaskStatus,
            name="task_status",
            native_enum=False,
            values_callable=lambda enum_type: [item.value for item in enum_type],
        ),
        default=TaskStatus.TODO,
        server_default=TaskStatus.TODO.value,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
    )
```

`owner_id` 同时参与查询和授权：任务查询始终带 `owner_id == current_user.id`，避免先查出别人的任务再做判断。

### 6.4 汇总模型导入

`app/models/__init__.py`：

```python
from app.models.task import Task
from app.models.user import User

__all__ = ["Task", "User"]
```

Alembic 必须导入模型，才能让它们注册到 `Base.metadata`。

------

## 7. Pydantic v2 Schema

数据库模型描述“如何存”，Pydantic Schema 描述“API 如何收发”。两者不要直接混为一个类。

### 7.1 用户 Schema

`app/schemas/user.py`：

```python
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class UserCreate(BaseModel):
    username: str = Field(min_length=3, max_length=30)
    email: EmailStr
    password: str = Field(min_length=12, max_length=128)

    @field_validator("username")
    @classmethod
    def normalize_username(cls, value: str) -> str:
        normalized = value.strip().lower()
        if not normalized.replace("_", "").isalnum():
            raise ValueError("用户名只能包含字母、数字和下划线")
        return normalized

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: EmailStr) -> str:
        return str(value).lower()


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    email: EmailStr
    is_active: bool
    created_at: datetime
```

`UserRead` 没有 `password` 和 `password_hash`，所以输出边界不会暴露凭证。

### 7.2 认证 Schema

`app/schemas/auth.py`：

```python
from pydantic import BaseModel


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
```

登录请求使用 FastAPI 的 `OAuth2PasswordRequestForm`，客户端以表单字段 `username`、`password` 提交。

### 7.3 任务与分页 Schema

`app/schemas/task.py`：

```python
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.enums import TaskStatus


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=2000)

    @field_validator("title")
    @classmethod
    def normalize_title(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("任务标题不能为空白")
        return normalized


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=2000)
    status: TaskStatus | None = None

    @field_validator("title")
    @classmethod
    def normalize_title(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        if not normalized:
            raise ValueError("任务标题不能为空白")
        return normalized


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    owner_id: int
    title: str
    description: str | None
    status: TaskStatus
    created_at: datetime
    updated_at: datetime


class TaskPage(BaseModel):
    items: list[TaskRead]
    total: int
    page: int
    page_size: int
```

PATCH 中使用 `model_dump(exclude_unset=True)` 区分“没传字段”和“明确传 null”。本项目允许 `description=null` 清空描述，但不允许 `title=null` 或 `status=null`，service 会再次执行这一业务约束。

### 7.4 错误 Schema

`app/schemas/common.py`：

```python
from pydantic import BaseModel


class ErrorDetail(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorDetail
```

------

## 8. 错误模型与异常

`app/core/errors.py`：

```python
class AppError(Exception):
    status_code = 400
    code = "bad_request"

    def __init__(self, message: str) -> None:
        self.message = message
        super().__init__(message)


class AuthenticationError(AppError):
    status_code = 401
    code = "authentication_failed"


class PermissionDeniedError(AppError):
    status_code = 403
    code = "permission_denied"


class NotFoundError(AppError):
    status_code = 404
    code = "not_found"


class ConflictError(AppError):
    status_code = 409
    code = "conflict"


class BusinessValidationError(AppError):
    status_code = 422
    code = "business_validation_failed"
```

这些异常只表达业务含义。到 `main.py` 再统一转换为 HTTP 响应。Pydantic 参数校验错误仍由 FastAPI 处理，其结构包含字段位置和校验类型。

------

## 9. Repository 数据访问层

Repository 只负责构造查询和操作 ORM 对象，不负责 HTTP，也不决定整个业务事务何时提交。

### 9.1 用户 Repository

`app/repositories/user.py`：

```python
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user import User


class UserRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get_by_id(self, user_id: int) -> User | None:
        return await self.session.get(User, user_id)

    async def get_by_username(self, username: str) -> User | None:
        stmt = select(User).where(User.username == username)
        return await self.session.scalar(stmt)

    async def get_by_email(self, email: str) -> User | None:
        stmt = select(User).where(User.email == email)
        return await self.session.scalar(stmt)

    async def add(self, user: User) -> User:
        self.session.add(user)
        await self.session.flush()
        return user
```

### 9.2 任务 Repository

`app/repositories/task.py`：

```python
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.task import Task


class TaskRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def add(self, task: Task) -> Task:
        self.session.add(task)
        await self.session.flush()
        return task

    async def get_owned(self, task_id: int, owner_id: int) -> Task | None:
        stmt = select(Task).where(
            Task.id == task_id,
            Task.owner_id == owner_id,
        )
        return await self.session.scalar(stmt)

    async def list_owned(
        self,
        owner_id: int,
        *,
        offset: int,
        limit: int,
    ) -> tuple[list[Task], int]:
        filters = (Task.owner_id == owner_id,)

        count_stmt = select(func.count()).select_from(Task).where(*filters)
        total = await self.session.scalar(count_stmt) or 0

        items_stmt = (
            select(Task)
            .where(*filters)
            .order_by(Task.created_at.desc(), Task.id.desc())
            .offset(offset)
            .limit(limit)
        )
        items = list((await self.session.scalars(items_stmt)).all())
        return items, total

    async def delete(self, task: Task) -> None:
        await self.session.delete(task)
        await self.session.flush()
```

这里使用 `select()`、`session.scalar()` 和 `session.scalars()`，不使用 SQLAlchemy 1.x 风格的 `session.query()`。

生产级分页在数据量非常大时可改用游标分页；传统 `OFFSET` 越靠后通常越慢，而且并发插入时可能产生重复或遗漏。

------

## 10. 密码哈希与 JWT

`app/core/security.py`：

```python
from datetime import datetime, timedelta, timezone

import jwt
from pwdlib import PasswordHash

from app.core.config import settings

password_hash = PasswordHash.recommended()
# 对不存在的用户也做一次验证，减少明显的用户名枚举时序差异。
DUMMY_HASH = password_hash.hash("not-a-real-password")


def hash_password(password: str) -> str:
    return password_hash.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return password_hash.verify(plain_password, hashed_password)


def create_access_token(subject: str) -> str:
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(minutes=settings.access_token_expire_minutes)
    payload = {
        "sub": subject,
        "type": "access",
        "iat": now,
        "exp": expires_at,
    }
    return jwt.encode(
        payload,
        settings.jwt_secret.get_secret_value(),
        algorithm=settings.jwt_algorithm,
    )


def decode_access_token(token: str) -> str:
    payload = jwt.decode(
        token,
        settings.jwt_secret.get_secret_value(),
        algorithms=[settings.jwt_algorithm],
        options={"require": ["sub", "exp", "iat", "type"]},
    )
    if payload.get("type") != "access":
        raise jwt.InvalidTokenError("unexpected token type")

    subject = payload.get("sub")
    if not isinstance(subject, str) or not subject:
        raise jwt.InvalidTokenError("invalid subject")
    return subject
```

安全要点：

- 使用 `PasswordHash.recommended()`，不要手写盐、迭代或“加密密码”。
- JWT 只是签名令牌，不会自动加密 payload，禁止放密码、密钥等敏感数据。
- 解码时固定允许的算法列表，不能相信令牌头自己声明的算法。
- `sub` 使用稳定的用户 ID，而不是可能变化的用户名。
- 真实系统还应设计刷新令牌轮换、撤销策略、密钥轮换和登录限流。
- HTTPS 是必须的，否则 Bearer Token 可能在传输中泄露。

------

## 11. Service 业务层

### 11.1 认证 Service

`app/services/auth.py`：

```python
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import AuthenticationError, ConflictError
from app.core.security import DUMMY_HASH, hash_password, verify_password
from app.models.user import User
from app.repositories.user import UserRepository
from app.schemas.user import UserCreate


class AuthService:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session
        self.users = UserRepository(session)

    async def register(self, payload: UserCreate) -> User:
        if await self.users.get_by_username(payload.username):
            raise ConflictError("用户名已存在")
        if await self.users.get_by_email(str(payload.email)):
            raise ConflictError("邮箱已存在")

        user = User(
            username=payload.username,
            email=str(payload.email),
            password_hash=hash_password(payload.password),
        )
        try:
            await self.users.add(user)
            await self.session.commit()
        except IntegrityError as exc:
            await self.session.rollback()
            # 预检查不能消除并发竞态，最终仍以数据库唯一约束为准。
            raise ConflictError("用户名或邮箱已存在") from exc

        await self.session.refresh(user)
        return user

    async def authenticate(self, username: str, password: str) -> User:
        normalized = username.strip().lower()
        user = await self.users.get_by_username(normalized)

        hash_to_check = user.password_hash if user else DUMMY_HASH
        password_ok = verify_password(password, hash_to_check)
        if user is None or not password_ok:
            raise AuthenticationError("用户名或密码错误")
        if not user.is_active:
            raise AuthenticationError("用户已停用")
        return user
```

错误信息不区分“用户名不存在”和“密码错误”，降低账号枚举风险。

### 11.2 任务 Service

`app/services/task.py`：

```python
from typing import Protocol

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import BusinessValidationError, NotFoundError
from app.models.task import Task
from app.repositories.task import TaskRepository
from app.schemas.task import TaskCreate, TaskUpdate


class TaskRepositoryPort(Protocol):
    async def add(self, task: Task) -> Task: ...

    async def get_owned(self, task_id: int, owner_id: int) -> Task | None: ...

    async def list_owned(
        self,
        owner_id: int,
        *,
        offset: int,
        limit: int,
    ) -> tuple[list[Task], int]: ...

    async def delete(self, task: Task) -> None: ...


class TaskService:
    def __init__(
        self,
        session: AsyncSession,
        repository: TaskRepositoryPort | None = None,
    ) -> None:
        self.session = session
        self.tasks: TaskRepositoryPort = repository or TaskRepository(session)

    async def create(self, owner_id: int, payload: TaskCreate) -> Task:
        task = Task(owner_id=owner_id, **payload.model_dump())
        await self.tasks.add(task)
        await self.session.commit()
        await self.session.refresh(task)
        return task

    async def get(self, task_id: int, owner_id: int) -> Task:
        task = await self.tasks.get_owned(task_id, owner_id)
        if task is None:
            # 对无权访问和不存在都返回 404，避免泄露其他用户资源是否存在。
            raise NotFoundError("任务不存在")
        return task

    async def list(
        self,
        owner_id: int,
        *,
        page: int,
        page_size: int,
    ) -> tuple[list[Task], int]:
        offset = (page - 1) * page_size
        return await self.tasks.list_owned(
            owner_id,
            offset=offset,
            limit=page_size,
        )

    async def update(
        self,
        task_id: int,
        owner_id: int,
        payload: TaskUpdate,
    ) -> Task:
        task = await self.get(task_id, owner_id)
        changes = payload.model_dump(exclude_unset=True)

        if changes.get("title", ...) is None:
            raise BusinessValidationError("title 不能为 null")
        if changes.get("status", ...) is None:
            raise BusinessValidationError("status 不能为 null")

        for field, value in changes.items():
            setattr(task, field, value)

        await self.session.commit()
        await self.session.refresh(task)
        return task

    async def delete(self, task_id: int, owner_id: int) -> None:
        task = await self.get(task_id, owner_id)
        await self.tasks.delete(task)
        await self.session.commit()
```

事务边界放在 Service：如果未来“创建任务”还要写审计记录，两次 repository 操作可以在同一个事务中一起提交或回滚。Service 依赖小型 `Protocol`，真实 Repository 与测试 Fake 只要满足同一能力契约即可，无需共享父类。

------

## 12. 认证依赖

`app/api/dependencies.py`：

```python
from typing import Annotated

import jwt
from fastapi import Depends
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import AuthenticationError
from app.core.security import decode_access_token
from app.db.session import get_db_session
from app.models.user import User
from app.repositories.user import UserRepository

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/token")

DbSession = Annotated[AsyncSession, Depends(get_db_session)]
TokenDep = Annotated[str, Depends(oauth2_scheme)]


async def get_current_user(token: TokenDep, session: DbSession) -> User:
    try:
        subject = decode_access_token(token)
        user_id = int(subject)
    except (jwt.InvalidTokenError, ValueError) as exc:
        raise AuthenticationError("访问令牌无效或已过期") from exc

    user = await UserRepository(session).get_by_id(user_id)
    if user is None or not user.is_active:
        raise AuthenticationError("用户不存在或已停用")
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
```

OpenAPI 会识别 `OAuth2PasswordBearer`，Swagger UI 右上角会出现 Authorize 按钮。

------

## 13. Router 接口层

### 13.1 认证路由

`app/api/routers/auth.py`：

```python
from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.security import OAuth2PasswordRequestForm

from app.api.dependencies import CurrentUser, DbSession
from app.core.security import create_access_token
from app.schemas.auth import Token
from app.schemas.common import ErrorResponse
from app.schemas.user import UserCreate, UserRead
from app.services.auth import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post(
    "/register",
    response_model=UserRead,
    status_code=status.HTTP_201_CREATED,
    responses={409: {"model": ErrorResponse}},
)
async def register(payload: UserCreate, session: DbSession) -> UserRead:
    user = await AuthService(session).register(payload)
    return UserRead.model_validate(user)


@router.post(
    "/token",
    response_model=Token,
    responses={401: {"model": ErrorResponse}},
)
async def login(
    form: Annotated[OAuth2PasswordRequestForm, Depends()],
    session: DbSession,
) -> Token:
    user = await AuthService(session).authenticate(form.username, form.password)
    token = create_access_token(str(user.id))
    return Token(access_token=token)


@router.get("/me", response_model=UserRead)
async def read_me(current_user: CurrentUser) -> UserRead:
    return UserRead.model_validate(current_user)
```

### 13.2 任务路由

`app/api/routers/tasks.py`：

```python
from typing import Annotated

from fastapi import APIRouter, Path, Query, Response, status

from app.api.dependencies import CurrentUser, DbSession
from app.schemas.common import ErrorResponse
from app.schemas.task import TaskCreate, TaskPage, TaskRead, TaskUpdate
from app.services.task import TaskService

router = APIRouter(prefix="/tasks", tags=["tasks"])
TaskId = Annotated[int, Path(gt=0)]


@router.post(
    "",
    response_model=TaskRead,
    status_code=status.HTTP_201_CREATED,
)
async def create_task(
    payload: TaskCreate,
    current_user: CurrentUser,
    session: DbSession,
) -> TaskRead:
    task = await TaskService(session).create(current_user.id, payload)
    return TaskRead.model_validate(task)


@router.get("", response_model=TaskPage)
async def list_tasks(
    current_user: CurrentUser,
    session: DbSession,
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(ge=1, le=100)] = 20,
) -> TaskPage:
    items, total = await TaskService(session).list(
        current_user.id,
        page=page,
        page_size=page_size,
    )
    return TaskPage(
        items=[TaskRead.model_validate(item) for item in items],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.get(
    "/{task_id}",
    response_model=TaskRead,
    responses={404: {"model": ErrorResponse}},
)
async def get_task(
    task_id: TaskId,
    current_user: CurrentUser,
    session: DbSession,
) -> TaskRead:
    task = await TaskService(session).get(task_id, current_user.id)
    return TaskRead.model_validate(task)


@router.patch(
    "/{task_id}",
    response_model=TaskRead,
    responses={404: {"model": ErrorResponse}},
)
async def update_task(
    task_id: TaskId,
    payload: TaskUpdate,
    current_user: CurrentUser,
    session: DbSession,
) -> TaskRead:
    task = await TaskService(session).update(
        task_id,
        current_user.id,
        payload,
    )
    return TaskRead.model_validate(task)


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task(
    task_id: TaskId,
    current_user: CurrentUser,
    session: DbSession,
) -> Response:
    await TaskService(session).delete(task_id, current_user.id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
```

### 13.3 健康检查路由

`app/api/routers/health.py`：

```python
from fastapi import APIRouter
from sqlalchemy import text

from app.api.dependencies import DbSession

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/live")
async def liveness() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/ready")
async def readiness(session: DbSession) -> dict[str, str]:
    await session.execute(text("SELECT 1"))
    return {"status": "ready"}
```

`live` 只判断进程是否能响应；`ready` 检查当前实例是否具备接收业务流量的关键条件。生产环境需要给就绪检查设置短超时，并避免它制造高负载。

------

## 14. 组装应用与 lifespan

`app/main.py`：

```python
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.api.routers import auth, health, tasks
from app.core.config import settings
from app.core.errors import AppError, AuthenticationError
from app.db.session import engine


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # 启动时尽早暴露数据库配置 / 连接问题，但不在这里执行迁移。
    async with engine.connect() as connection:
        await connection.execute(text("SELECT 1"))
    try:
        yield
    finally:
        await engine.dispose()


def create_app() -> FastAPI:
    application = FastAPI(
        title=settings.app_name,
        version="1.0.0",
        debug=settings.debug,
        lifespan=lifespan,
    )

    application.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "X-Request-ID"],
    )

    @application.exception_handler(AppError)
    async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
        headers = (
            {"WWW-Authenticate": "Bearer"}
            if isinstance(exc, AuthenticationError)
            else None
        )
        return JSONResponse(
            status_code=exc.status_code,
            headers=headers,
            content={
                "error": {
                    "code": exc.code,
                    "message": exc.message,
                }
            },
        )

    application.include_router(health.router)
    application.include_router(auth.router, prefix="/api/v1")
    application.include_router(tasks.router, prefix="/api/v1")
    return application


app = create_app()
```

`lifespan` 负责共享资源的启动与释放。不要使用旧的 `@app.on_event("startup")`。数据库迁移也不要放在 `lifespan`：多 worker 同时启动会造成迁移竞态。

------

## 15. Alembic 数据库迁移

### 15.1 初始化

在项目根目录执行：

```powershell
alembic init -t async alembic
```

不要把真实数据库密码硬编码在 `alembic.ini`。让 `env.py` 从同一套 Settings 读取连接地址。

### 15.2 异步 env.py

用下面的核心内容替换生成的 `alembic/env.py`：

```python
from logging.config import fileConfig

from alembic import context
from sqlalchemy import pool
from sqlalchemy.ext.asyncio import async_engine_from_config

from app.core.config import settings
from app.db.base import Base
from app import models  # noqa: F401，确保所有模型进入 Base.metadata

config = context.config
config.set_main_option(
    "sqlalchemy.url",
    settings.database_url.replace("%", "%%"),
)

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def do_run_migrations(connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=target_metadata,
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


async def run_async_migrations() -> None:
    connectable = async_engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()


def run_migrations_online() -> None:
    import asyncio

    asyncio.run(run_async_migrations())


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
```

### 15.3 生成、审查和执行迁移

```powershell
alembic revision --autogenerate -m "创建用户与任务表"
alembic upgrade head
alembic current
```

自动生成只是草稿，执行前必须检查 `alembic/versions/*.py`：

- 是否意外删除表或字段。
- 字段类型、长度、默认值和空值约束是否正确。
- 索引、唯一约束和外键是否完整。
- 大表迁移是否可能长时间锁表。
- `downgrade()` 是否符合团队回滚策略。

查看待执行 SQL：

```powershell
alembic upgrade head --sql
```

异步驱动的 offline SQL 支持受迁移内容影响，生产执行方式应在预发布环境演练。

------

## 16. 请求的完整流转过程

以 `PATCH /api/v1/tasks/42` 为例：

```text
1. Uvicorn 接收 HTTP 请求
2. CORS / 日志等中间件处理请求
3. FastAPI 匹配 tasks.update_task 路由
4. Pydantic 校验 task_id、JSON 请求体
5. get_db_session 创建本次请求的 AsyncSession
6. oauth2_scheme 读取 Authorization: Bearer ...
7. get_current_user 验证 JWT 并查询用户
8. TaskService.get 以 task_id + owner_id 查询任务
9. TaskService 执行业务校验并修改 ORM 对象
10. Service 提交事务，刷新对象
11. TaskRead 从对象属性序列化并过滤输出
12. 中间件处理响应，Uvicorn 返回客户端
13. 请求依赖退出，会话关闭
```

这种结构的价值不只是“文件多”：

- Router 可以专注 HTTP 契约。
- Service 可以不用启动服务器直接单元测试。
- Repository 便于替换查询和优化索引。
- 事务边界清楚，不会在多个层里随意 `commit()`。
- 鉴权依赖可被所有受保护路由复用。

------

## 17. 启动与手工验证

### 17.1 前置条件

1. PostgreSQL 已创建 `task_db` 数据库和最小权限账号。
2. `.env` 中数据库 URL 可用。
3. `.env` 中 JWT 密钥是随机值且至少 32 字符。
4. 已执行 `alembic upgrade head`。

开发启动：

```powershell
uvicorn app.main:app --reload
```

或：

```powershell
fastapi dev app/main.py
```

### 17.2 检查健康状态

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8000/health/live'
Invoke-RestMethod -Uri 'http://127.0.0.1:8000/health/ready'
```

### 17.3 注册

```powershell
$registerBody = @{
    username = 'jingxu'
    email = 'jingxu@example.com'
    password = 'ChangeThis-Long-Password-123!'
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri 'http://127.0.0.1:8000/api/v1/auth/register' `
    -ContentType 'application/json' `
    -Body $registerBody
```

### 17.4 登录并保存 Token

OAuth2 Password Flow 使用表单，而不是 JSON：

```powershell
$tokenResponse = Invoke-RestMethod `
    -Method Post `
    -Uri 'http://127.0.0.1:8000/api/v1/auth/token' `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
        username = 'jingxu'
        password = 'ChangeThis-Long-Password-123!'
    }

$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
```

### 17.5 创建、分页和更新任务

```powershell
$task = Invoke-RestMethod `
    -Method Post `
    -Uri 'http://127.0.0.1:8000/api/v1/tasks' `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{
        title = '学习 FastAPI'
        description = '完成项目实战章节'
    } | ConvertTo-Json)

Invoke-RestMethod `
    -Method Get `
    -Uri 'http://127.0.0.1:8000/api/v1/tasks?page=1&page_size=20' `
    -Headers $headers

Invoke-RestMethod `
    -Method Patch `
    -Uri "http://127.0.0.1:8000/api/v1/tasks/$($task.id)" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{ status = 'done' } | ConvertTo-Json)
```

也可以打开 `/docs`，点击 Authorize 登录后逐个调试接口。验证时至少检查：

- 未带 Token 返回 `401`。
- 错误 Token 返回统一认证错误。
- 不存在的任务返回 `404`。
- `page_size=101` 返回参数校验错误。
- 响应中永远没有 `password_hash`。
- 用户 A 无法访问用户 B 的任务。

------

## 18. 可以继续演进的方向

### 18.1 搜索和分页

- 为状态、标题等过滤条件设计组合索引。
- 将深分页改为基于 `(created_at, id)` 的游标分页。
- 为分页响应增加 `next_cursor`，而不是暴露内部 SQL。

### 18.2 认证与授权

- 短期 Access Token + 可轮换 Refresh Token。
- Refresh Token 只存哈希，支持撤销与设备管理。
- 登录失败限流、账号锁定和安全审计。
- RBAC / ABAC 规则，但资源所有者校验仍不能省略。

### 18.3 可靠事件

如果创建任务后必须可靠地发送事件，不要简单地“提交数据库后发消息”。可使用 Transactional Outbox：业务数据和 outbox 记录在同一事务提交，再由独立 worker 投递消息。

### 18.4 可观测性

- 结构化 JSON 日志与请求 ID。
- 指标：吞吐、延迟、错误率、连接池状态。
- OpenTelemetry Trace 跨越 API、数据库和消息系统。
- 错误聚合与告警。

测试、容器与生产部署将在下一章展开。

------

## 19. 本章检查清单

- [ ] 配置通过环境变量注入，JWT 密钥不在源码中。
- [ ] 密码使用 pwdlib 推荐算法哈希，没有自制加密方案。
- [ ] 数据库模型使用 `Mapped` / `mapped_column()`。
- [ ] 查询使用 `select()`，没有 `session.query()`。
- [ ] 每个请求拥有独立 `AsyncSession`，没有跨并发 task 共享。
- [ ] Pydantic 响应模型启用 `ConfigDict(from_attributes=True)`。
- [ ] Router、Service、Repository 的职责和依赖方向清楚。
- [ ] Service 负责事务边界，异常路径会回滚。
- [ ] 所有任务查询都包含当前用户所有权条件。
- [ ] JWT 校验固定算法，检查 `exp`、`sub` 和令牌类型。
- [ ] 分页有最大 `page_size`，响应包含 `total`。
- [ ] 统一业务错误不会泄露内部堆栈和数据库信息。
- [ ] 启停逻辑使用 `lifespan`，迁移不在应用启动时自动执行。
- [ ] Alembic 自动迁移经过人工审查后才执行。
- [ ] 能通过 Swagger UI 或 PowerShell 完成注册、登录和任务 CRUD。

------

## 20. 官方资料

- [FastAPI - Bigger Applications](https://fastapi.tiangolo.com/tutorial/bigger-applications/)
- [FastAPI - OAuth2 with Password and Bearer](https://fastapi.tiangolo.com/tutorial/security/oauth2-jwt/)
- [FastAPI - Dependencies with yield](https://fastapi.tiangolo.com/tutorial/dependencies/dependencies-with-yield/)
- [FastAPI - Lifespan Events](https://fastapi.tiangolo.com/advanced/events/)
- [Pydantic Settings](https://docs.pydantic.dev/latest/concepts/pydantic_settings/)
- [Pydantic Models / from_attributes](https://docs.pydantic.dev/latest/concepts/models/)
- [SQLAlchemy 2.0 ORM Quick Start](https://docs.sqlalchemy.org/en/20/orm/quickstart.html)
- [SQLAlchemy 2.0 AsyncIO](https://docs.sqlalchemy.org/en/20/orm/extensions/asyncio.html)
- [SQLAlchemy Session Basics](https://docs.sqlalchemy.org/en/20/orm/session_basics.html)
- [Alembic Tutorial](https://alembic.sqlalchemy.org/en/latest/tutorial.html)
- [Alembic Autogenerate](https://alembic.sqlalchemy.org/en/latest/autogenerate.html)
- [pwdlib 官方文档](https://frankie567.github.io/pwdlib/)
- [PyJWT Usage](https://pyjwt.readthedocs.io/en/stable/usage.html)

------

[← 上一章：FastAPI 后端开发](./08-FastAPI后端开发.md) ｜ [返回学习路线](./README.md) ｜ [下一章：测试、质量与部署 →](./10-测试质量与部署.md)
