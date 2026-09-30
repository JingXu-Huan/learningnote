# Gin 快速上手（Spring Boot 开发者版）

> 面向已有 Spring Boot 基础的 Go 后端快速迁移手册，涵盖路由、参数绑定、Middleware、JWT、GORM 与工程实践。
>
> 来源：用户提供的《Gin快速上手（SpringBoot开发者版）.pdf》，共 10 页。转换时移除重复页眉、页脚和打印版封面，恢复跨页段落、表格、目录树和代码缩进。
>
> 代码按独立示例组织；除首个 `main.go` 外，需要按项目补齐 import、业务类型与辅助函数，不应把全部片段直接拼成一个源文件。

学习目标：以已有 Spring Boot 基础为前提，按 1～2 天的练习计划，使用 Gin 实现带参数校验、统一返回、错误处理、数据库访问和 JWT 鉴权的 REST API。

------

## 目录

- [1. Gin 是什么：先建立 Spring Boot 对照](#1-gin-是什么先建立-spring-boot-对照)
- [2. 环境与第一个接口](#2-环境与第一个接口)
- [3. 路由：没有 Controller，也一样分层](#3-路由没有-controller也一样分层)
- [4. 取参数：Path、Query、Body 一次讲透](#4-取参数pathquerybody-一次讲透)
- [5. 统一返回结构](#5-统一返回结构)
- [6. 推荐项目结构](#6-推荐项目结构)
- [7. Middleware：对应 Spring 的 Filter / Interceptor](#7-middleware对应-spring-的-filter-interceptor)
- [8. JWT 鉴权最小写法](#8-jwt-鉴权最小写法)
- [9. 数据库：Gin + GORM 快速组合](#9-数据库gin-gorm-快速组合)
- [10. 配置与环境变量](#10-配置与环境变量)
- [11. 完整的 Handler 模板](#11-完整的-handler-模板)
- [12. CORS、静态文件与优雅关闭](#12-cors静态文件与优雅关闭)
- [13. 从 Spring Boot 迁移时最容易踩的坑](#13-从-spring-boot-迁移时最容易踩的坑)
- [14. 建议学习顺序（面向后端实战）](#14-建议学习顺序面向后端实战)
- [15. 下一步练手：把 Spring Boot 的用户模块重写一遍](#15-下一步练手把-spring-boot-的用户模块重写一遍)
- [参考文档](#参考文档)
- [转换说明](#转换说明)

## 1. Gin 是什么：先建立 Spring Boot 对照

Gin 是 Go 生态中常用的 HTTP Web 框架，定位接近 Spring Boot 的 Spring MVC 部分：负责路由、参数绑定、校验、中间件和响应。

它不像 Spring Boot 一样内置 IoC 容器、ORM、配置体系和自动装配；Go 项目通常显式组合这些库。其特点是结构直观、性能好、部署简单，常见交付形式是编译后的可执行文件。

| Spring Boot | Gin / Go 常见写法 | 说明 |
| --- | --- | --- |
| `@RestController` | 路由处理函数 `func(c *gin.Context)` | Controller 是函数，不是注解类 |
| `@GetMapping("/users/{id}")` | `r.GET("/users/:id", handler)` | HTTP 方法直接挂在引擎上 |
| `@RequestParam` | `c.Query("key")` / `c.DefaultQuery(...)` | 查询参数 |
| `@PathVariable` | `c.Param("id")` | 路径参数 |
| `@RequestBody @Valid` | `c.ShouldBindJSON(&req)` + `binding:"..."` | 绑定与校验合并 |
| `ResponseEntity` | `c.JSON(status, data)` | 输出 JSON |
| `HandlerInterceptor` / Filter | Middleware | `r.Use(...)` 或路由组 `group.Use(...)` |
| `@ControllerAdvice` | 错误处理中间件 + 统一错误函数 | 没有强制官方范式 |
| `application.yml` | env + Viper / 直接配置结构体 | 按项目选择 |
| Spring Data JPA / MyBatis | GORM / `database/sql` / sqlx | ORM 不是 Gin 的一部分 |

## 2. 环境与第一个接口

先通过 `go version` 确认 Go 环境。原 PDF 使用“Go 1.22+”作为环境起点，但安装 Gin 时应满足所选版本的 Go 要求，不能据此认为 Go 1.22 支持最新版 Gin；版本要求以 [Gin 官方文档](https://gin-gonic.com/en/docs/) 和所选版本的 `go.mod` 为准。

在 PowerShell 中新建项目：

~~~powershell
mkdir gin-demo
cd gin-demo
go mod init github.com/your-name/gin-demo
go get github.com/gin-gonic/gin
~~~

创建 `main.go`：

~~~go
package main

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func main() {
	r := gin.Default() // 自带 Logger 和 Recovery 中间件
	r.GET("/ping", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"message": "pong",
		})
	})
	_ = r.Run(":8080") // 等价于监听 0.0.0.0:8080
}
~~~

启动服务：

~~~powershell
go run .
~~~

在另一个终端请求接口：

~~~powershell
curl.exe http://localhost:8080/ping
~~~

预期响应：

~~~json
{"message":"pong"}
~~~

开发时可用 air 热重载：

~~~powershell
go install github.com/air-verse/air@latest
air
~~~

## 3. 路由：没有 Controller，也一样分层

Gin 的路由由 `*gin.Engine`（通常命名为 `r`）管理。路由组对应 Spring 中的统一 `@RequestMapping("/api")`。

~~~go
func main() {
	r := gin.Default()

	api := r.Group("/api")
	{
		v1 := api.Group("/v1")
		{
			users := v1.Group("/users")
			{
				users.GET("", listUsers)
				users.GET("/:id", getUser)
				users.POST("", createUser)
				users.PUT("/:id", updateUser)
				users.DELETE("/:id", deleteUser)
			}
		}
	}

	_ = r.Run(":8080")
}
~~~

常用路由方法：`GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`Any`。生产代码通常不要用 `Any`，避免接口方法语义失控。

## 4. 取参数：Path、Query、Body 一次讲透

### 4.1 路径参数与查询参数

请求：

~~~text
GET /api/v1/users/12?page=2&pageSize=20
~~~

~~~go
func getUser(c *gin.Context) {
	id := c.Param("id")                    // "12"
	page := c.DefaultQuery("page", "1")    // "2"，没有时为 "1"
	pageSize, ok := c.GetQuery("pageSize") // "20", true

	c.JSON(http.StatusOK, gin.H{
		"id":       id,
		"page":     page,
		"pageSize": pageSize,
		"exists":   ok,
	})
}
~~~

`Param`、`Query` 取到的是字符串。涉及数字、日期时应转换并处理错误，不要把无效输入默默当作 0。

~~~go
id, err := strconv.ParseInt(c.Param("id"), 10, 64)
if err != nil || id <= 0 {
	c.JSON(http.StatusBadRequest, gin.H{"message": "id 必须是正整数"})
	return
}
~~~

### 4.2 JSON Body 绑定与校验

这相当于 Spring 的 DTO + `@RequestBody` + `@Valid`。

~~~go
type CreateUserReq struct {
	Name  string `json:"name" binding:"required,min=2,max=20"`
	Email string `json:"email" binding:"required,email"`
	Age   int    `json:"age" binding:"gte=1,lte=150"`
}

func createUser(c *gin.Context) {
	var req CreateUserReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"message": "请求参数不合法：" + err.Error()})
		return
	}

	// 调用 service：user, err := userService.Create(c.Request.Context(), req)
	c.JSON(http.StatusCreated, gin.H{"name": req.Name})
}
~~~

关键点：

- `json:"name"` 指定 JSON 字段名，类似 Jackson 的 `@JsonProperty`。
- `binding:"required,email"` 是 Gin 使用的 validator 校验标签。
- `ShouldBindJSON` 只返回错误，由调用方决定响应；`BindJSON` 会在失败时自动写 400，业务项目优先使用 `ShouldBindJSON`。
- 请求 Body 默认按流读取，不要在中间件里随意读取 `c.Request.Body`；需要重复绑定时，应显式使用缓存请求体的方式。

常见绑定函数：

| 数据来源 | 函数 |
| --- | --- |
| JSON 请求体 | `ShouldBindJSON(&req)` |
| Query 参数 | `ShouldBindQuery(&req)` |
| 表单 | `ShouldBind(&req)` |
| URI 参数 | `ShouldBindUri(&req)` |
| Header | `ShouldBindHeader(&req)` |

## 5. 统一返回结构

不要在每个接口手写不同格式的 `gin.H`。先确定前后端契约：

~~~go
package response

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type Result struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

func OK(c *gin.Context, data interface{}) {
	c.JSON(http.StatusOK, Result{Code: 0, Message: "success", Data: data})
}

func Fail(c *gin.Context, status int, message string) {
	c.JSON(status, Result{Code: status, Message: message})
}
~~~

响应示例：

~~~json
{
    "code": 0,
    "message": "success",
    "data": { "id": 1, "name": "景旭" }
}
~~~

实际项目里，`code` 建议使用稳定的业务码，例如 10001 表示参数错误、20001 表示未登录；HTTP Status 仍表达协议层语义。不要所有错误都返回 HTTP 200。以上 `Fail` 仅演示统一封装，业务码与 HTTP 状态码的分离需要按实际契约扩展。

## 6. 推荐项目结构

Go 不强调传统 Java 分层目录，但 HTTP、业务、数据访问仍然应该分开。小型 REST 项目可按下面组织：

~~~text
gin-demo/
├── cmd/server/main.go      # 程序入口、依赖装配
├── internal/
│   ├── config/             # 配置加载
│   ├── handler/            # HTTP 层：参数绑定、响应
│   ├── service/            # 业务编排、事务边界
│   ├── repository/         # 数据访问
│   ├── model/              # 数据库实体、DTO
│   ├── middleware/         # JWT、日志、CORS、异常恢复
│   └── response/           # 统一返回与错误码
├── pkg/                    # 可被外部复用的公共包（按需）
├── migrations/             # SQL 迁移文件
├── .env.example
├── go.mod
└── go.sum
~~~

`internal` 是 Go 的语言级约束：导入其下包的代码必须位于该 `internal` 目录的父目录树内，适合存放内部业务代码。

职责对应：

~~~text
HTTP 请求 → handler（Bind / 校验） → service（业务） → repository（DB）
                ↓
           response（统一输出）
~~~

## 7. Middleware：对应 Spring 的 Filter / Interceptor

中间件函数签名是 `gin.HandlerFunc`。在 `c.Next()` 前做前置逻辑，后面做后置逻辑。

~~~go
func RequestLogger() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next() // 继续执行后面的中间件或 handler
		log.Printf("%s %s %d %s",
			c.Request.Method,
			c.Request.URL.Path,
			c.Writer.Status(),
			time.Since(start),
		)
	}
}
~~~

注册范围：

~~~go
r.Use(RequestLogger()) // 全局

// cfg 为启动时加载的配置，JWT_SECRET 必须配置。
auth := r.Group("/api", JWTAuth([]byte(cfg.JWTSecret))) // 仅 /api 下路由
auth.GET("/profile", profile)
~~~

`gin.Default()` 已经注册 Logger 和 Recovery；后者会捕获当前请求处理链中的 panic，避免单次请求把整个服务进程带崩。生产上仍建议补充统一错误日志与告警。

## 8. JWT 鉴权最小写法

以 `github.com/golang-jwt/jwt/v5` 为例：

~~~powershell
go get github.com/golang-jwt/jwt/v5
~~~

中间件核心流程：读取 `Authorization: Bearer <token>` → 验签 → 提取用户信息 → 写入 Context → 放行。

~~~go
func JWTAuth(secret []byte) gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		tokenString, ok := strings.CutPrefix(header, "Bearer ")
		if !ok || tokenString == "" {
			response.Fail(c, http.StatusUnauthorized, "请先登录")
			c.Abort()
			return
		}

		token, err := jwt.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
			if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
				return nil, fmt.Errorf("非法签名算法")
			}
			return secret, nil
		})
		if err != nil || !token.Valid {
			response.Fail(c, http.StatusUnauthorized, "token 无效或已过期")
			c.Abort()
			return
		}

		claims := token.Claims.(jwt.MapClaims)
		c.Set("userId", claims["userId"])
		c.Next()
	}
}
~~~

在后续 handler 中读取当前用户：

~~~go
userID, exists := c.Get("userId")
if !exists {
	// 鉴权路由必须经过 JWTAuth；业务中仍应处理缺少用户信息的情况。
}
~~~

安全注意：密钥只来自环境变量或密钥管理服务；验证签名算法；Token 设置过期时间；敏感接口还要做权限校验，不能只验证“已登录”。

本例是原文的最小鉴权片段，签名算法限定为 HMAC 类。业务中的具体算法、过期时间要求及 `userId` 的存在性与类型，应按认证契约显式验证。

## 9. 数据库：Gin + GORM 快速组合

Gin 与 ORM 解耦。习惯 JPA 时，可先使用 GORM；需要像 MyBatis 一样精确控制 SQL 时，可使用 `database/sql` 或 sqlx。

~~~powershell
go get gorm.io/gorm gorm.io/driver/mysql
~~~

初始化：

~~~go
db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{})
if err != nil {
	return err
}
sqlDB, err := db.DB()
if err != nil {
	return err
}
sqlDB.SetMaxOpenConns(20)
sqlDB.SetMaxIdleConns(10)
sqlDB.SetConnMaxLifetime(time.Hour)
~~~

实体和 Repository：

~~~go
type User struct {
	ID        uint64    `gorm:"primaryKey" json:"id"`
	Name      string    `gorm:"size:50;not null" json:"name"`
	Email     string    `gorm:"size:100;uniqueIndex;not null" json:"email"`
	CreatedAt time.Time `json:"createdAt"`
}

type UserRepository struct {
	db *gorm.DB
}

func (r *UserRepository) FindByID(ctx context.Context, id uint64) (*User, error) {
	var user User
	if err := r.db.WithContext(ctx).First(&user, id).Error; err != nil {
		return nil, err
	}
	return &user, nil
}
~~~

关键习惯：

- 将 `c.Request.Context()` 传进 service / repository，使请求取消能够传递到数据库调用。
- `gorm.ErrRecordNotFound` 映射为 404，其他数据库错误记录日志后映射为 500。
- 生产环境用迁移工具管理表结构，不要仅依赖 `AutoMigrate` 自动修改生产库。
- 事务放在 service 层：`db.WithContext(ctx).Transaction(func(tx *gorm.DB) error { ... })`。

## 10. 配置与环境变量

Go 项目推荐把配置集中加载。最小场景可以直接读取环境变量：

~~~go
type Config struct {
	Port      string
	MySQLDSN  string
	JWTSecret string
}

func LoadConfig() Config {
	return Config{
		Port:      getEnv("APP_PORT", "8080"),
		MySQLDSN:  mustEnv("MYSQL_DSN"),
		JWTSecret: mustEnv("JWT_SECRET"),
	}
}
~~~

`getEnv` 和 `mustEnv` 是项目自定义辅助函数：前者用于读取环境变量并提供默认值，后者用于要求必填配置。

本地可配置 `.env`，不要提交真实密码和密钥；部署时由 Docker、K8s 或 CI/CD 注入环境变量。项目复杂后可引入 Viper，但不要为了一个配置文件过早增加依赖。

## 11. 完整的 Handler 模板

这段代码体现了 Gin 项目中一个接口的基本形态：

~~~go
func (h *UserHandler) GetByID(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil || id == 0 {
		response.Fail(c, http.StatusBadRequest, "id 必须是正整数")
		return
	}

	user, err := h.service.GetByID(c.Request.Context(), id)
	if errors.Is(err, service.ErrUserNotFound) {
		response.Fail(c, http.StatusNotFound, "用户不存在")
		return
	}
	if err != nil {
		slog.Error("查询用户失败", "err", err, "userID", id)
		response.Fail(c, http.StatusInternalServerError, "服务器繁忙")
		return
	}

	response.OK(c, user)
}
~~~

与 Spring Boot 的差异在于，没有全局异常机制强制自动翻译错误，因此要明确约定错误如何从 repository → service → handler 传递。业务预期错误，如不存在、重复、状态不允许，与系统错误，如数据库、网络、序列化错误，应区分处理。

## 12. CORS、静态文件与优雅关闭

### CORS

前后端分离时，使用官方生态中间件：

~~~powershell
go get github.com/gin-contrib/cors
~~~

~~~go
r.Use(cors.New(cors.Config{
	AllowOrigins:     []string{"http://localhost:5173"},
	AllowMethods:     []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
	AllowHeaders:     []string{"Origin", "Content-Type", "Authorization"},
	AllowCredentials: true,
	MaxAge:           12 * time.Hour,
}))
~~~

生产环境不要把带 Cookie / 凭证的跨域请求配置为 `AllowAllOrigins: true`。

### 优雅关闭

不要只写 `r.Run()`。服务收到 SIGTERM 时应停止接收新请求，并给正在执行的请求一个完成窗口：

~~~go
srv := &http.Server{Addr: ":8080", Handler: r}
go func() {
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}()

quit := make(chan os.Signal, 1)
signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
<-quit

ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
defer cancel()
_ = srv.Shutdown(ctx)
~~~

## 13. 从 Spring Boot 迁移时最容易踩的坑

1. **所有依赖都要显式传入。** Go 没有 Spring 的自动注入。建议在 main / wire 函数集中按 `newRepository → newService → newHandler` 的顺序装配，再注册路由。
2. **不要把全局可变状态当作单例 Bean 使用。** 只读配置和 DB 客户端可以共享；可变的请求状态必须放在局部变量或请求上下文中。
3. **错误必须及时处理。** Go 的 `if err != nil` 是控制流的一部分，不要写 `_ = err` 掩盖问题。
4. **指针与值有语义。** DTO 小且不可变时可用值；数据库实体、需要修改或避免大拷贝时常用指针。方法接收者保持一致。
5. **并发需要显式治理。** goroutine 访问共享 map、缓存对象时要考虑锁、channel 或并发安全容器；数据库并发访问要结合连接池管理。
6. **每个请求都应有 Context。** 下游调用，如 DB、Redis、HTTP，应尽量传递 `ctx` 并设置超时，避免请求断开后任务仍无止境执行。

## 14. 建议学习顺序（面向后端实战）

1. 跑通路由、Path / Query / JSON 参数绑定、统一响应。
2. 用 middleware 实现日志、Recovery、JWT 鉴权、角色权限。
3. 接 MySQL：先 GORM CRUD，再学习原生 SQL / sqlx 处理复杂查询。
4. 接 Redis、消息队列、配置管理与优雅关闭。
5. 加测试：使用 `net/http/httptest` 测试 handler，接口使用 table-driven tests，service / repository 做依赖隔离。
6. 用 Docker 打包并部署。Go 的常见交付物是二进制文件或容器镜像。

## 15. 下一步练手：把 Spring Boot 的用户模块重写一遍

可以实现一个“用户 + 登录 + 文章”小项目，包含：

- 用户注册、登录、JWT 刷新。
- 文章分页、详情、创建、修改、删除。
- admin 与普通用户的角色控制。
- MySQL 唯一索引冲突映射为友好的 409 响应。
- Redis 缓存文章详情，并在修改 / 删除时失效。
- Docker Compose 一键启动 API + MySQL + Redis。

通过这个项目，可以完整练习 Gin 的路由、绑定、Middleware、Context、GORM、错误处理和工程分层。之后再学习 Go 的 goroutine、channel、context、sync，可进一步衔接实际 NestJS / Go 后端开发。

相关笔记：

- [Go 语言从 0 到 1：渐进式学习路线](Go语言从0到1快速上手.md)
- [11：net/http 与第一个 REST API](11-net-http与第一个REST-API.md)
- [13：goroutine、channel 与并发治理](13-goroutine-channel与并发治理.md)
- [用 Go channel 模拟 Java 并发工具](Go-channel模拟Java并发工具.md)

## 参考文档

原 PDF 列出的参考文档名称，补充对应的官方链接：

- [Gin 官方文档](https://gin-gonic.com/en/docs/)
- [Gin GitHub 仓库](https://github.com/gin-gonic/gin)
- [Go 官方教程](https://go.dev/doc/tutorial/)
- [GORM 官方文档](https://gorm.io/docs/)

## 转换说明

- 保留原 PDF 的 15 个主体章节，以及绑定函数对照、项目目录和全部代码主题；合并跨页内容，移除重复封面、打印目录、页眉和页码。
- 恢复目录树连接符与依赖装配流程中的箭头，重新排版 Go、JSON、PowerShell 和纯文本代码块。
- 修正 Spring 路径变量为 `{id}`；为 `JWTAuth(secret []byte)` 的注册示例补齐密钥参数；为 `db.DB()` 补上错误处理。
- 对 Go 版本要求增加按 Gin 所选版本核对的说明，并明确辅助函数与业务类型属于项目代码。
- 原 PDF 第 12 节标题包含“静态文件”，正文实际只提供 CORS 和优雅关闭示例；这里保留原章节范围，不补写未提供的静态文件代码。

转换日期：2026-09-30。
