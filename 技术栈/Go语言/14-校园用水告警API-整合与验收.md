# 14：结业项目——校园用水告警 API（整合与验收）

> 预计：2～4 天<br>
> 本章目标：把前面所有内容整合为一个能启动、能测试、能讲清楚的 Go 后端项目。

------

## 1. 最小功能范围

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | /healthz | 健康检查 |
| POST | /api/v1/alerts | 创建告警 |
| GET | /api/v1/alerts | 按状态分页查询 |
| GET | /api/v1/alerts/{id} | 查询单条告警 |
| PATCH | /api/v1/alerts/{id}/resolve | 处理告警 |
| GET | /api/v1/statistics/daily | 每日统计 |

## 2. 推荐目录

~~~text
water-alert-api/
├── cmd/api/main.go
├── internal/
│   ├── alert/
│   │   ├── model.go
│   │   ├── service.go
│   │   ├── repository.go
│   │   ├── mysql_repository.go
│   │   └── handler.go
│   └── platform/
│       ├── config/
│       ├── database/
│       └── httpx/
├── migrations/
├── go.mod
├── go.sum
└── README.md
~~~

不是每个项目都必须长成这个样子；它的目的只是让入口、业务、基础设施和迁移文件各自有明确位置。

### 从 alert-cli 迁移过来

结业项目建议新建模块，而不是直接把 <code>alert-cli</code> 改名后继续堆功能：

~~~powershell
Set-Location ..
New-Item -ItemType Directory -Name 'water-alert-api'
Set-Location .\water-alert-api
go mod init example.com/water-alert-api
~~~

把 CLI 项目中已经验证过的 Alert、状态规则、Store 测试思路迁过来，并把 import 路径改为新模块名。HTTP 服务一旦有 POST、PATCH、DELETE 等并发写操作，就不能直接复用前面的非线程安全内存 Store；要么用 <code>sync.RWMutex</code> 保护它，要么更早切换到 MySQL Repository。

## 3. 交付顺序

1. 用内存 Store 跑通只读接口；需要并发写时先加 <code>sync.RWMutex</code>，或直接切换 MySQL Repository。
2. 给 Service 写单元测试，给 Handler 写 <code>httptest</code>。
3. 加 MySQL、migration、参数化 SQL 与事务。
4. 加环境变量、请求日志、统一错误响应、健康检查。
5. 加有界 worker pool 完成“并发批量通知并等待结果”。
6. 最后再按真实需要接 Gin、Redis、Docker Compose、JWT 或消息队列。

## 4. 最终验收清单

- [ ] 新机器按 README 能启动。
- [ ] 数据库连不上时，服务不会错误地报告健康。
- [ ] 参数错误返回 400，记录不存在返回 404，状态冲突返回稳定错误码。
- [ ] 所有 SQL 传递 Context 并使用参数化查询。
- [ ] 关键业务有单元测试，HTTP 层有接口测试。
- [ ] 并发批量通知能限制并发、响应取消、等待退出。
- [ ] <code>go fmt ./...</code>、<code>go test ./...</code>、<code>go test -race ./...</code>、<code>go vet ./...</code>、<code>go build ./...</code> 均通过。

## 5. 能讲清楚比“能跑”更重要

完成项目后，应能简洁说明：

> 请求先进入 Handler，Handler 解析输入并传递 Context。Service 负责状态规则，Repository 用参数化 SQL 访问 MySQL。批量通知使用有界 worker pool，避免无限创建 goroutine；测试用 race detector 检查共享状态问题。

这就是从 Go 语法走到后端项目交付的第一轮闭环。

下一章：[附录-常用命令踩坑与面试表达.md](附录-常用命令踩坑与面试表达.md)
