# 08 - FastAPI 后端开发 😎

> 本章以 **Python 3.10+、FastAPI、Pydantic v2** 为基线，建立从 HTTP 接口到可维护应用结构的完整认知。

[← 上一章：并发与异步编程](./07-并发与异步编程.md) ｜ [返回学习路线](./README.md) ｜ [下一章：FastAPI 项目实战 →](./09-FastAPI项目实战.md)

------

## 学习目标

完成本章后，你应该能够：

- 解释 WSGI、ASGI、FastAPI、Starlette、Pydantic 与 Uvicorn 的关系。
- 正确声明路径参数、查询参数、请求体、响应模型和 HTTP 状态码。
- 使用依赖注入、`APIRouter`、中间件和 `lifespan` 拆分应用。
- 根据依赖库类型选择 `async def` 或普通 `def`。
- 实现文件上传、后台任务和 WebSocket 等常见能力。
- 利用自动生成的 OpenAPI 文档调试和交付 API。

------

## 目录

- [1. FastAPI 处在后端技术栈的什么位置](#1-fastapi-处在后端技术栈的什么位置)
- [2. 环境准备与最小应用](#2-环境准备与最小应用)
- [3. 路径操作与请求处理流程](#3-路径操作与请求处理流程)
- [4. 路径参数与查询参数](#4-路径参数与查询参数)
- [5. 请求体与 Pydantic v2](#5-请求体与-pydantic-v2)
- [6. 响应模型与状态码](#6-响应模型与状态码)
- [7. 异常处理](#7-异常处理)
- [8. 依赖注入](#8-依赖注入)
- [9. APIRouter 与应用拆分](#9-apirouter-与应用拆分)
- [10. 中间件与 CORS](#10-中间件与-cors)
- [11. lifespan 生命周期管理](#11-lifespan-生命周期管理)
- [12. 后台任务](#12-后台任务)
- [13. 文件上传](#13-文件上传)
- [14. WebSocket](#14-websocket)
- [15. async def 还是 def](#15-async-def-还是-def)
- [16. OpenAPI 与接口文档](#16-openapi-与接口文档)
- [17. 常见误区与版本迁移](#17-常见误区与版本迁移)
- [18. 本章检查清单](#18-本章检查清单)
- [19. 官方资料](#19-官方资料)

------

## 1. FastAPI 处在后端技术栈的什么位置

### 1.1 从 HTTP 请求到业务代码

一次请求大致经过如下链路：

```text
浏览器 / App / 其他服务
        ↓ HTTP / WebSocket
反向代理（可选：Nginx、Traefik、云负载均衡）
        ↓
ASGI Server（Uvicorn）
        ↓
FastAPI / Starlette：路由、中间件、依赖、异常处理
        ↓
Pydantic：输入校验、序列化、JSON Schema
        ↓
Service / Repository：业务规则与数据访问
        ↓
数据库、缓存、消息队列、第三方 API
```

FastAPI 并不是直接监听 TCP 端口的服务器。通常由 **Uvicorn** 这样的 ASGI Server 接收网络请求，再调用 FastAPI 应用。

### 1.2 ASGI 是什么

WSGI 主要面向传统同步 HTTP 应用；ASGI 除了 HTTP，还能表达异步请求、WebSocket 与应用生命周期事件。可以把 ASGI 理解为“服务器和 Python Web 应用之间的调用协议”。

FastAPI 的几个关键组成如下：

| 组件 | 职责 |
| --- | --- |
| FastAPI | 路由、依赖注入、OpenAPI 集成与开发体验 |
| Starlette | ASGI、请求响应、中间件、WebSocket 等 Web 基础能力 |
| Pydantic | 基于类型注解的数据校验、转换与序列化 |
| Uvicorn | ASGI Server，监听端口并驱动应用 |

### 1.3 FastAPI 的主要特点

- 类型注解同时服务于编辑器、运行时校验和接口文档。
- 自动生成 OpenAPI、Swagger UI 和 ReDoc。
- 同时支持同步与异步路径操作函数。
- 依赖注入适合组织鉴权、数据库会话和公共参数。
- 底层基于 Starlette，可直接使用其成熟的 ASGI 能力。

------

## 2. 环境准备与最小应用

### 2.1 创建虚拟环境

以下命令适用于 PowerShell：

```powershell
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install "fastapi[standard]"
```

`fastapi[standard]` 会安装 FastAPI CLI、Uvicorn 及常用标准依赖。团队项目应使用 `pyproject.toml` 或锁文件固定版本，避免“我的机器可以运行”。

### 2.2 第一个接口

创建 `main.py`：

```python
from fastapi import FastAPI

app = FastAPI(title="Learning API", version="1.0.0")


@app.get("/")
async def read_root() -> dict[str, str]:
    return {"message": "Hello, FastAPI"}
```

开发模式启动：

```powershell
fastapi dev main.py
```

也可以直接使用 Uvicorn：

```powershell
uvicorn main:app --reload
```

其中 `main:app` 表示“导入 `main` 模块中的 `app` 对象”。`--reload` 只适合开发环境。

访问：

- API：`http://127.0.0.1:8000/`
- Swagger UI：`http://127.0.0.1:8000/docs`
- ReDoc：`http://127.0.0.1:8000/redoc`
- OpenAPI JSON：`http://127.0.0.1:8000/openapi.json`

### 2.3 路由匹配顺序

固定路径应写在动态路径之前，否则 `/users/me` 可能先被 `/users/{user_id}` 捕获：

```python
@app.get("/users/me")
async def read_current_user() -> dict[str, str]:
    return {"user": "current"}


@app.get("/users/{user_id}")
async def read_user(user_id: int) -> dict[str, int]:
    return {"user_id": user_id}
```

------

## 3. 路径操作与请求处理流程

`@app.get()`、`@app.post()` 等装饰器注册“路径操作”。常见 HTTP 方法语义如下：

| 方法 | 常见语义 | 是否通常幂等 |
| --- | --- | --- |
| `GET` | 查询资源 | 是 |
| `POST` | 创建资源或触发动作 | 否 |
| `PUT` | 整体替换资源 | 是 |
| `PATCH` | 部分更新资源 | 设计得当时可幂等 |
| `DELETE` | 删除资源 | 是 |

典型请求处理过程：

1. Uvicorn 把 ASGI 请求交给应用。
2. 中间件按注册关系处理请求。
3. 路由匹配 HTTP 方法和路径。
4. FastAPI 解析参数并执行依赖。
5. Pydantic 校验输入数据。
6. 路径操作函数执行业务逻辑。
7. `response_model` 校验并过滤输出。
8. 中间件处理响应并返回客户端。

HTTP API 的关键不只是“返回 JSON”，还要正确表达资源、状态码、错误结构和鉴权边界。

------

## 4. 路径参数与查询参数

现代 FastAPI 示例优先使用 `typing.Annotated`，让真实类型和框架元数据保持分离。

### 4.1 路径参数

```python
from typing import Annotated

from fastapi import FastAPI, Path

app = FastAPI()


@app.get("/tasks/{task_id}")
async def get_task(
    task_id: Annotated[int, Path(gt=0, description="任务 ID")],
) -> dict[str, int]:
    return {"task_id": task_id}
```

请求 `/tasks/abc` 或 `/tasks/0` 时，FastAPI 会返回结构化的 `422 Unprocessable Entity` 校验错误。

枚举可限制路径参数取值：

```python
from enum import Enum


class TaskState(str, Enum):
    TODO = "todo"
    DONE = "done"


@app.get("/tasks/state/{state}")
async def list_by_state(state: TaskState) -> dict[str, str]:
    return {"state": state.value}
```

### 4.2 查询参数

未出现在路径模板中的简单参数默认会被识别为查询参数：

```python
from typing import Annotated, Literal

from fastapi import Query


@app.get("/tasks")
async def list_tasks(
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(ge=1, le=100)] = 20,
    keyword: Annotated[str | None, Query(min_length=1, max_length=50)] = None,
    order: Literal["created_at", "title"] = "created_at",
) -> dict[str, object]:
    return {
        "page": page,
        "page_size": page_size,
        "keyword": keyword,
        "order": order,
    }
```

有默认值的是可选参数，没有默认值的参数是必填参数。`str | None` 只表达“值可以是空”，是否必填仍由有没有默认值决定。

### 4.3 Header 与 Cookie

```python
from typing import Annotated

from fastapi import Cookie, Header


@app.get("/client-context")
async def client_context(
    user_agent: Annotated[str | None, Header()] = None,
    session_id: Annotated[str | None, Cookie()] = None,
) -> dict[str, str | None]:
    return {"user_agent": user_agent, "session_id": session_id}
```

默认情况下，`user_agent` 会对应 HTTP Header `user-agent`。

------

## 5. 请求体与 Pydantic v2

### 5.1 声明请求模型

```python
from datetime import datetime

from pydantic import BaseModel, Field


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=1000)
    priority: int = Field(default=3, ge=1, le=5)
    due_at: datetime | None = None


@app.post("/tasks")
async def create_task(payload: TaskCreate) -> dict[str, object]:
    return payload.model_dump()
```

FastAPI 会读取 JSON 请求体、交给 Pydantic 校验并构造 `TaskCreate` 对象。Pydantic v2 将模型转换为字典使用 `model_dump()`，不要再写旧版的 `.dict()`。

### 5.2 字段校验器

```python
from pydantic import BaseModel, Field, field_validator


class UserRegister(BaseModel):
    username: str = Field(min_length=3, max_length=30)
    password: str = Field(min_length=12, max_length=128)

    @field_validator("username")
    @classmethod
    def normalize_username(cls, value: str) -> str:
        normalized = value.strip().lower()
        if not normalized.replace("_", "").isalnum():
            raise ValueError("用户名只能包含字母、数字和下划线")
        return normalized
```

跨字段校验可使用 `@model_validator`。校验器应该保持确定性，不要在其中发数据库请求；数据库唯一性等业务规则放到 service 层。

### 5.3 嵌套模型

```python
from pydantic import BaseModel, Field, HttpUrl


class Attachment(BaseModel):
    name: str
    url: HttpUrl


class TaskWithAttachments(BaseModel):
    title: str
    tags: set[str] = Field(default_factory=set)
    attachments: list[Attachment] = Field(default_factory=list)
```

集合字段使用 `Field(default_factory=list)` / `Field(default_factory=set)`，让每个模型实例获得独立容器，语义也更清楚：

```python
class TaskWithTags(BaseModel):
    title: str
    tags: list[str] = Field(default_factory=list)
```

### 5.4 从 ORM 对象生成响应

Pydantic v2 使用 `ConfigDict(from_attributes=True)`：

```python
from pydantic import BaseModel, ConfigDict


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    completed: bool
```

这替代了 Pydantic v1 的 `orm_mode = True` 和 `from_orm()`。

### 5.5 部分更新

PATCH 模型把字段都声明为可选，然后只提取客户端实际提交的字段：

```python
class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=100)
    completed: bool | None = None


@app.patch("/tasks/{task_id}")
async def update_task(task_id: int, payload: TaskUpdate) -> dict[str, object]:
    changes = payload.model_dump(exclude_unset=True)
    return {"task_id": task_id, "changes": changes}
```

`exclude_unset=True` 能区分“字段未传”和“字段明确传了 `null`”。是否允许 `null` 是另一个业务决定。

------

## 6. 响应模型与状态码

### 6.1 response_model 是输出边界

```python
from datetime import datetime, timezone

from fastapi import status
from pydantic import BaseModel, ConfigDict


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    completed: bool
    created_at: datetime


@app.post(
    "/tasks",
    response_model=TaskRead,
    status_code=status.HTTP_201_CREATED,
)
async def create_task(payload: TaskCreate) -> dict[str, object]:
    return {
        "id": 1,
        "title": payload.title,
        "completed": False,
        "created_at": datetime.now(timezone.utc),
        "internal_note": "不会出现在响应中",
    }
```

`response_model` 会：

- 校验返回值是否符合契约。
- 过滤模型中未声明的字段，降低敏感信息泄露风险。
- 生成 OpenAPI 响应 Schema。

也可直接使用返回类型声明：

```python
@app.get("/tasks/{task_id}")
async def get_task(task_id: int) -> TaskRead:
    ...
```

复杂项目中，显式 `response_model=` 常更容易表达 `exclude_none`、联合响应等选项。

### 6.2 常见状态码

| 状态码 | 典型场景 |
| --- | --- |
| `200 OK` | 查询、更新成功 |
| `201 Created` | 创建成功 |
| `204 No Content` | 删除成功且无响应体 |
| `400 Bad Request` | 请求语义不合法 |
| `401 Unauthorized` | 未认证或凭证无效 |
| `403 Forbidden` | 已认证但无权限 |
| `404 Not Found` | 资源不存在 |
| `409 Conflict` | 唯一键冲突、状态冲突 |
| `422 Unprocessable Entity` | 参数或请求体校验失败 |

删除接口可返回空响应：

```python
from fastapi import Response, status


@app.delete("/tasks/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task(task_id: int) -> Response:
    return Response(status_code=status.HTTP_204_NO_CONTENT)
```

不要给 `204` 响应附加 JSON 响应体。

### 6.3 自定义响应类型

```python
from fastapi.responses import PlainTextResponse


@app.get("/robots.txt", response_class=PlainTextResponse)
async def robots() -> str:
    return "User-agent: *\nDisallow: /admin"
```

FastAPI / Starlette 还提供 `JSONResponse`、`StreamingResponse`、`FileResponse` 和重定向响应。大文件应使用流式或对象存储下载，不要一次性读入内存。

------

## 7. 异常处理

### 7.1 HTTPException

```python
from fastapi import HTTPException, status


@app.get("/tasks/{task_id}", response_model=TaskRead)
async def get_task(task_id: int) -> TaskRead:
    task = None
    if task is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="任务不存在",
        )
    return task
```

`raise` 会立即终止当前处理流程。不要 `return HTTPException(...)`。

### 7.2 统一业务错误

先定义与 HTTP 无关的领域异常，再集中映射为响应：

```python
from fastapi import Request
from fastapi.responses import JSONResponse


class DomainError(Exception):
    def __init__(self, code: str, message: str, status_code: int = 400) -> None:
        self.code = code
        self.message = message
        self.status_code = status_code


@app.exception_handler(DomainError)
async def domain_error_handler(
    request: Request,
    exc: DomainError,
) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": {
                "code": exc.code,
                "message": exc.message,
                "path": request.url.path,
            }
        },
    )
```

这样 service 层不必知道 FastAPI 的 `HTTPException`，也便于单元测试。

### 7.3 请求校验错误

FastAPI 默认会返回详细的 `422` 错误。生产环境可以通过 `RequestValidationError` 处理器统一外层结构，但不要完全丢弃字段位置与错误类型，否则客户端难以定位问题。

意外的 `500` 错误应记录异常堆栈和请求追踪 ID，响应中不要暴露数据库连接串、密钥或内部堆栈。

------

## 8. 依赖注入

依赖注入不是“全局变量容器”，而是声明某个路径操作需要哪些前置能力。FastAPI 会构建依赖图、解析子依赖，并在一次请求内缓存相同依赖的结果。

### 8.1 公共查询参数

```python
from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Query


@dataclass
class Pagination:
    offset: int
    limit: int


def pagination_params(
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(ge=1, le=100)] = 20,
) -> Pagination:
    return Pagination(offset=(page - 1) * page_size, limit=page_size)


PaginationDep = Annotated[Pagination, Depends(pagination_params)]


@app.get("/tasks")
async def list_tasks(pagination: PaginationDep) -> dict[str, int]:
    return {"offset": pagination.offset, "limit": pagination.limit}
```

类型别名可以减少路由函数中的重复注解。

### 8.2 带清理逻辑的 yield 依赖

```python
from collections.abc import AsyncIterator


class Client:
    async def close(self) -> None:
        ...


async def get_client() -> AsyncIterator[Client]:
    client = Client()
    try:
        yield client
    finally:
        await client.close()
```

`yield` 前相当于资源获取，`finally` 中完成释放。数据库会话通常采用这种模式。

### 8.3 鉴权依赖与子依赖

```python
from typing import Annotated

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

bearer = HTTPBearer(auto_error=False)


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> str:
    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="缺少访问令牌",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return credentials.credentials


CurrentUser = Annotated[str, Depends(get_current_user)]
```

真实项目还要校验签名、过期时间、令牌类型和用户状态；完整实现见下一章。

### 8.4 依赖覆盖

测试时可替换真实依赖：

```python
app.dependency_overrides[get_client] = fake_client_dependency
```

测试结束后必须清理：

```python
app.dependency_overrides.clear()
```

------

## 9. APIRouter 与应用拆分

当应用不再只有几个接口时，用 `APIRouter` 按业务域拆分：

```text
app/
├── main.py
└── routers/
    ├── __init__.py
    ├── health.py
    └── tasks.py
```

`app/routers/tasks.py`：

```python
from fastapi import APIRouter

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.get("")
async def list_tasks() -> list[dict[str, object]]:
    return []


@router.get("/{task_id}")
async def get_task(task_id: int) -> dict[str, int]:
    return {"id": task_id}
```

`app/main.py`：

```python
from fastapi import FastAPI

from app.routers import health, tasks

app = FastAPI(title="Task API")
app.include_router(health.router)
app.include_router(tasks.router, prefix="/api/v1")
```

可在 `include_router()` 时统一添加版本前缀、依赖或响应定义。推荐按业务域组织，而不是把所有 GET 放一个文件、所有 POST 放另一个文件。

------

## 10. 中间件与 CORS

### 10.1 自定义中间件

```python
from time import perf_counter
from uuid import uuid4

from fastapi import Request


@app.middleware("http")
async def add_request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID", str(uuid4()))
    started = perf_counter()
    response = await call_next(request)
    elapsed = perf_counter() - started
    response.headers["X-Request-ID"] = request_id
    response.headers["X-Process-Time"] = f"{elapsed:.6f}"
    return response
```

中间件适合请求 ID、访问日志、耗时统计、安全响应头等横切逻辑。业务权限一般更适合依赖注入，因为依赖能访问路由参数并出现在 OpenAPI 中。

### 10.2 CORS

CORS 是浏览器的跨源访问策略，不是服务端鉴权。开发时前端 `http://localhost:5173` 调用后端 `http://localhost:8000` 就属于跨源。

```python
from fastapi.middleware.cors import CORSMiddleware

allowed_origins = [
    "http://localhost:5173",
    "https://app.example.com",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PATCH", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "X-Request-ID"],
)
```

当 `allow_credentials=True` 时，不要依赖通配符 `*`；明确列出可信来源、方法和请求头。CORS 配置应来自环境配置，并按开发、测试、生产环境区分。

### 10.3 中间件顺序

中间件形成嵌套调用栈。后添加的中间件通常位于外层，先处理请求、后处理响应。涉及异常、日志和追踪时要通过测试确认实际顺序，不要只凭直觉判断。

------

## 11. lifespan 生命周期管理

应用启动时可能需要验证数据库连接、创建共享 HTTP 客户端或加载模型；关闭时要释放连接池。现代 FastAPI 推荐使用 `lifespan`：

```python
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from httpx import AsyncClient


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    app.state.http_client = AsyncClient(timeout=5.0)
    try:
        yield
    finally:
        await app.state.http_client.aclose()


app = FastAPI(lifespan=lifespan)
```

- `yield` 之前：启动逻辑。
- `yield` 之后：关闭逻辑。
- 资源必须在异常路径下也能释放，所以使用 `try/finally` 或资源上下文管理器。

不要在新代码中使用 `@app.on_event("startup")` / `@app.on_event("shutdown")`。也不要在应用启动时自动执行生产数据库结构迁移；迁移应作为独立部署步骤，只执行一次。

------

## 12. 后台任务

`BackgroundTasks` 适合“响应可以先返回、任务很轻、即使进程重启丢失也可接受”的工作，例如写简单审计日志或发送非关键通知。

```python
from fastapi import BackgroundTasks, status


def write_audit_log(task_id: int, action: str) -> None:
    with open("audit.log", "a", encoding="utf-8") as file:
        file.write(f"task={task_id} action={action}\n")


@app.post("/tasks/{task_id}/complete", status_code=status.HTTP_202_ACCEPTED)
async def complete_task(
    task_id: int,
    background_tasks: BackgroundTasks,
) -> dict[str, str]:
    background_tasks.add_task(write_audit_log, task_id, "complete")
    return {"status": "accepted"}
```

需要重试、持久化、跨机器执行或耗时较长的任务，应使用 Celery、Dramatiq、RQ、Arq 或消息队列消费者。`BackgroundTasks` 运行在当前应用进程中，不是可靠任务队列。

传给后台任务的数据应是可独立使用的标量或 DTO，不要把即将关闭的请求级数据库会话传进去。

------

## 13. 文件上传

表单与文件上传使用 `multipart/form-data`。如依赖中未包含 multipart 支持，可安装：

```powershell
python -m pip install python-multipart
```

```python
from typing import Annotated

from fastapi import File, Form, HTTPException, UploadFile, status

MAX_UPLOAD_SIZE = 5 * 1024 * 1024
ALLOWED_TYPES = {"image/png", "image/jpeg"}


@app.post("/attachments", status_code=status.HTTP_201_CREATED)
async def upload_attachment(
    task_id: Annotated[int, Form(gt=0)],
    file: Annotated[UploadFile, File(description="PNG 或 JPEG，最大 5 MiB")],
) -> dict[str, object]:
    if file.content_type not in ALLOWED_TYPES:
        raise HTTPException(status_code=415, detail="不支持的文件类型")

    content = await file.read(MAX_UPLOAD_SIZE + 1)
    if len(content) > MAX_UPLOAD_SIZE:
        raise HTTPException(status_code=413, detail="文件过大")

    # 真实项目应生成服务端文件名，写入对象存储，并进行病毒扫描。
    return {
        "task_id": task_id,
        "original_name": file.filename,
        "size": len(content),
    }
```

安全注意事项：

- 不信任 `filename`，禁止直接拼接到服务器路径。
- 不只信任 `content_type`，重要场景要检测文件签名。
- 限制请求体和文件大小，避免耗尽内存或磁盘。
- 大文件分块读取并直接写入对象存储。
- 文件下载时设置安全的响应头和权限校验。

------

## 14. WebSocket

WebSocket 适合双向实时通信，例如在线状态、协作编辑和实时通知。

```python
from fastapi import WebSocket, WebSocketDisconnect


class ConnectionManager:
    def __init__(self) -> None:
        self.active: set[WebSocket] = set()

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        self.active.add(websocket)

    def disconnect(self, websocket: WebSocket) -> None:
        self.active.discard(websocket)

    async def broadcast(self, message: str) -> None:
        stale: list[WebSocket] = []
        for connection in self.active:
            try:
                await connection.send_text(message)
            except RuntimeError:
                stale.append(connection)
        for connection in stale:
            self.disconnect(connection)


manager = ConnectionManager()


@app.websocket("/ws/tasks")
async def task_updates(websocket: WebSocket) -> None:
    # 这里只用短期、一次性 WebSocket 票据演示；生产代码必须实际验证。
    token = websocket.query_params.get("token")
    if token is None:
        await websocket.close(code=1008)
        return

    await manager.connect(websocket)
    try:
        while True:
            message = await websocket.receive_text()
            await manager.broadcast(message)
    except WebSocketDisconnect:
        manager.disconnect(websocket)
```

这个内存版连接管理器只适合单进程演示。多 worker 或多实例部署时，各进程看不到其他进程的连接，应借助 Redis Pub/Sub、NATS、Kafka 等完成跨实例广播，并设计心跳、限流、断线重连和消息顺序策略。

查询字符串可能进入代理访问日志、浏览器历史和监控系统，因此不要把长期 Bearer Token 放进 WebSocket URL。生产环境可使用有效期极短且一次性的连接票据，或根据客户端条件使用安全 Cookie / WebSocket 子协议传递凭证；无论采用哪种方式，都必须在 `accept()` 前验证签名、有效期、用途和权限，并对日志脱敏。

------

## 15. async def 还是 def

核心原则不是“异步一定更快”，而是**不要阻塞事件循环**。

### 15.1 使用 async def 的场景

依赖库提供可等待 API 时使用 `async def`：

```python
@app.get("/remote-data")
async def remote_data() -> dict[str, object]:
    response = await async_http_client.get("https://example.com/api")
    return response.json()
```

典型异步库：

- `httpx.AsyncClient`
- SQLAlchemy `AsyncSession` + `asyncpg` / `aiosqlite`
- `redis.asyncio`
- 原生异步消息客户端

### 15.2 使用普通 def 的场景

只提供阻塞 API 的库可以放在普通 `def` 路径函数或普通 `def` 依赖中，FastAPI 会在线程池中调用它：

```python
@app.get("/legacy-report")
def legacy_report() -> dict[str, str]:
    result = blocking_sdk.generate_report()
    return {"result": result}
```

不要在 `async def` 中直接调用 `time.sleep()`、同步 `requests.get()`、同步数据库驱动或长时间文件 I/O。这会阻塞同一事件循环上的其他请求。

### 15.3 CPU 密集任务

图像处理、模型推理和大规模计算属于 CPU 密集任务。`asyncio` 不能让 CPU 计算自动并行，应考虑：

- 独立任务队列与 worker。
- 进程池。
- 专门的推理服务。
- 能释放 GIL 的底层实现。

### 15.4 数据库会话的并发边界

一个 `AsyncSession` 表示一个可变的事务状态，不能被多个并发 task 共享。每个请求 / 并发任务使用独立会话；不要把同一个会话放进 `asyncio.gather()` 的多个协程共同操作。

------

## 16. OpenAPI 与接口文档

FastAPI 根据路由、参数、模型和依赖自动生成 OpenAPI。

### 16.1 完善元数据

```python
from fastapi import FastAPI

tags_metadata = [
    {"name": "tasks", "description": "任务的增删改查"},
    {"name": "health", "description": "服务健康检查"},
]

app = FastAPI(
    title="Task API",
    summary="任务管理服务",
    description="供 Web 与移动端调用的任务管理 API。",
    version="1.0.0",
    openapi_tags=tags_metadata,
)
```

路径操作也应补充语义：

```python
@app.get(
    "/tasks/{task_id}",
    response_model=TaskRead,
    summary="查询单个任务",
    operation_id="getTask",
    responses={404: {"description": "任务不存在"}},
    tags=["tasks"],
)
async def get_task(task_id: int) -> TaskRead:
    ...
```

稳定的 `operation_id` 有利于生成客户端 SDK，但必须在整个 OpenAPI 文档中唯一。

### 16.2 文档不是完整安全边界

关闭 `/docs` 并不能替代鉴权。生产环境是否开放交互式文档取决于组织要求；无论开放与否，API 本身都必须执行认证、授权、输入校验和限流。

### 16.3 OpenAPI 作为契约

可以把 `/openapi.json` 用于：

- 前后端对齐字段和错误模型。
- 生成 TypeScript / Java / Python 客户端。
- 契约差异检查。
- API 网关导入与安全扫描。

接口变更时要考虑兼容性。删除字段、收紧校验、修改含义通常是破坏性变更；新增可选字段一般更容易保持兼容。

------

## 17. 常见误区与版本迁移

### 17.1 现代写法对照

| 旧教程常见写法 | 本仓库采用的现代写法 |
| --- | --- |
| `@app.on_event("startup")` | `FastAPI(lifespan=lifespan)` |
| Pydantic `.dict()` | `.model_dump()` |
| `class Config: orm_mode = True` | `model_config = ConfigDict(from_attributes=True)` |
| `Model.from_orm(obj)` | `Model.model_validate(obj)`，并启用 `from_attributes` |
| `Optional[str]` 但没有默认值就认为可选 | 同时明确 `str | None = None` |
| 把所有代码写进 `main.py` | `APIRouter` + service / repository 分层 |
| 在 `async def` 里调用同步阻塞库 | 使用异步客户端或交给线程 / 任务 worker |

### 17.2 业务逻辑堆在路由中

路由层应该处理 HTTP 适配：参数、依赖、状态码和响应模型。事务规则、权限规则和状态转换应进入 service；SQL 进入 repository。这样才能独立单元测试并在 CLI、消息消费者中复用业务逻辑。

### 17.3 返回 ORM 对象后触发隐式查询

异步 ORM 中，响应序列化时访问尚未加载的关系可能触发 `MissingGreenlet`。应在 repository 查询阶段明确使用 `selectinload()` 等加载策略，或先转换为纯 Pydantic DTO，不依赖序列化阶段的隐式 I/O。

### 17.4 把应用启动当迁移工具

多个 worker 同时启动时，如果每个都执行 `create_all()` 或 Alembic upgrade，可能出现竞态。生产迁移应作为独立、可审计、只执行一次的部署步骤。

------

## 18. 本章检查清单

- [ ] 能说明 Uvicorn、ASGI、Starlette、FastAPI 和 Pydantic 的职责。
- [ ] 能用 `Annotated` 声明并约束路径、查询、Header 和 Cookie 参数。
- [ ] 请求与响应使用不同的 Pydantic 模型，敏感字段不会出现在输出中。
- [ ] 使用 Pydantic v2 的 `model_dump()`、`ConfigDict(from_attributes=True)`。
- [ ] 正确区分 `400`、`401`、`403`、`404`、`409`、`422`。
- [ ] 公共能力通过依赖注入组织，资源依赖使用 `yield` 清理。
- [ ] 路由按业务域拆进 `APIRouter`。
- [ ] CORS 仅允许可信 origin，理解 CORS 不等于鉴权。
- [ ] 启停逻辑使用 `lifespan`，没有使用 `@app.on_event`。
- [ ] 不在 `async def` 中直接执行阻塞 I/O。
- [ ] 知道 `BackgroundTasks` 和可靠任务队列的边界。
- [ ] 文件上传限制大小、类型和文件名使用方式。
- [ ] 知道单进程 WebSocket 管理器不能直接扩展到多实例。
- [ ] OpenAPI 描述了成功响应和主要错误响应。

------

## 19. 官方资料

- [FastAPI 官方教程](https://fastapi.tiangolo.com/tutorial/)
- [FastAPI - Lifespan Events](https://fastapi.tiangolo.com/advanced/events/)
- [FastAPI - Dependencies](https://fastapi.tiangolo.com/tutorial/dependencies/)
- [FastAPI - Bigger Applications / APIRouter](https://fastapi.tiangolo.com/tutorial/bigger-applications/)
- [FastAPI - CORS](https://fastapi.tiangolo.com/tutorial/cors/)
- [FastAPI - Background Tasks](https://fastapi.tiangolo.com/tutorial/background-tasks/)
- [FastAPI - Request Files](https://fastapi.tiangolo.com/tutorial/request-files/)
- [FastAPI - WebSockets](https://fastapi.tiangolo.com/advanced/websockets/)
- [FastAPI - Async](https://fastapi.tiangolo.com/async/)
- [Pydantic v2 Models](https://docs.pydantic.dev/latest/concepts/models/)
- [Starlette 官方文档](https://www.starlette.io/)
- [Uvicorn 官方文档](https://www.uvicorn.org/)

------

[← 上一章：并发与异步编程](./07-并发与异步编程.md) ｜ [返回学习路线](./README.md) ｜ [下一章：FastAPI 项目实战 →](./09-FastAPI项目实战.md)
