# GORM 完整教程：用 MyBatis 的经验学习 Go 数据访问

> 面向读者：了解 Java、MyBatis 和 SQL，准备使用 Go、Gin、GORM 与 PostgreSQL。
>
> 示例范围：GORM v2 系列，导入路径为 `gorm.io/gorm`；正文以传统 API 为主，第 21 章单独介绍从 v1.30.0 起提供的泛型 API。GORM v2 的 Go 模块版本仍可能是 `v1.x.x`，不要因此安装旧包 `github.com/jinzhu/gorm`。
>
> 数据库：PostgreSQL 17，选择固定主版本以明确 Docker 数据卷路径，并非要求安装最新版本。官方文档核对日期：2026-10-11。

## 阅读路线

第一次学习，按 1—8 章建立基础，再看 19 章运行完整项目。之后按需阅读关联、事务和进阶章节。

正文中的短代码是**局部示例**：默认已经有 `db *gorm.DB`、`ctx context.Context` 和本章模型；需要按代码补充导入、检查错误。标注“替换模型”的示例不应直接与前面的同名结构体合并。第 19 章提供完整 `main.go`，可独立复制。

## 目录

- [1. GORM 与 MyBatis 的对应关系](#ch01)
- [2. PostgreSQL 与 Go 环境准备](#ch02)
- [3. 数据库连接、连接池与生命周期](#ch03)
- [4. 模型、字段映射、NULL 与默认值](#ch04)
- [5. Migration：自动迁移和版本化迁移](#ch05)
- [6. Create：新增、批量新增与 Upsert](#ch06)
- [7. Query：单条、列表、条件与投影](#ch07)
- [8. Update：零值、字段选择与 Save](#ch08)
- [9. Delete：软删除、恢复与物理删除](#ch09)
- [10. 动态 SQL、Scopes、分页与查询复用](#ch10)
- [11. 关联模型：一对一、一对多与多对多](#ch11)
- [12. Preload、Join、结果映射与 N+1](#ch12)
- [13. 原生 SQL、聚合、子查询与批处理](#ch13)
- [14. 事务、隔离级别与保存点](#ch14)
- [15. 并发更新、悲观锁与乐观锁](#ch15)
- [16. Hooks、Context 与错误处理](#ch16)
- [17. PostgreSQL 类型、JSONB 与约束](#ch17)
- [18. 日志、SQL 安全与性能](#ch18)
- [19. 完整实战：Gin + GORM + PostgreSQL](#ch19)
- [20. Repository、Service 与项目组织](#ch20)
- [21. 泛型 API：与传统 API 的区别](#ch21)
- [22. 从 MyBatis 迁移时的常见误区](#ch22)
- [23. GORM 方法与数据库英文术语速查](#ch23)
- [24. 练习、验证方式与排错](#ch24)
- [25. 官方参考资料](#ch25)

<a id="ch01"></a>
## 1. GORM 与 MyBatis 的对应关系

### 1.1 工作方式

MyBatis 的常见流程是：在 XML 或注解里写 SQL，由 Mapper 方法传参，再把查询结果映射成 Java 对象。GORM 的常见流程是：声明 Go 结构体，调用链式方法，由框架生成 SQL，再把结果写入结构体。复杂 SQL 仍可以直接编写。

两者都不能替代数据库约束、索引设计和业务事务。GORM 也不会在结构体字段发生变化时自动把变化同步到数据库，仍需要调用写入方法。

```java
// MyBatis：Mapper 的 SQL 由 XML 或注解提供。
User user = userMapper.selectById(1L);
```

```go
// GORM 传统 API：查询结果写到 user，错误从 Error 获取。
var user User
err := db.WithContext(ctx).First(&user, 1).Error
```

### 1.2 概念对照

| 关注点 | MyBatis / Java 常见方式 | GORM / Go 常见方式 |
| --- | --- | --- |
| 持久化对象 | POJO / Entity | 导出字段组成的 struct |
| 表、列映射 | SQL、`resultMap`、驼峰映射配置 | 命名约定、`gorm` 标签、`TableName()` |
| 数据访问入口 | Mapper 接口、`SqlSession` | `*gorm.DB`、自定义 Repository |
| 实际连接池 | DataSource，例如 HikariCP | 底层 `*sql.DB` |
| 插入 | `<insert>`、Mapper 方法 | `Create` |
| 查询 | `<select>`、`selectOne`、`selectList` | `First`、`Take`、`Find`、`Scan` |
| 更新 | `<update>`、动态 `<set>` | `Update`、`Updates` |
| 删除 | `<delete>` | `Delete`，是否软删取决于模型 |
| 动态 SQL | `<if>`、`<where>`、`<foreach>` | Go `if`、`Where`、`IN ?`、`Scopes` |
| 参数绑定 | `#{value}` | `?` 参数，或命名参数 |
| 文本替换 | `${value}` | 拼接 SQL / 动态标识符，需白名单 |
| 关联结果 | `<association>`、`<collection>` | 模型关联、`Preload`，或 Join + DTO |
| 事务 | `SqlSession` 或 Spring 事务 | `Transaction` 或 `Begin/Commit/Rollback` |
| DDL 管理 | 通常由 Flyway / Liquibase 等承担 | `AutoMigrate` / `Migrator`，或独立迁移工具 |
| 自定义类型 | `TypeHandler` | `sql.Scanner`、`driver.Valuer` |
| 分页 | 手写 SQL / 分页插件 | `Limit`、`Offset`，总数单独 `Count` |
| 结果缓存 | MyBatis 有会话级缓存及可配置二级缓存 | GORM 核心不提供等价的自动结果缓存 |

**边界要分清：**`@Transactional` 来自 Spring；`BaseMapper`、`QueryWrapper`、`IPage` 和 `@TableLogic` 常见于 MyBatis-Plus。它们不是 MyBatis 核心自带的统一 API。

`*gorm.DB` 是数据库操作句柄：根句柄通常使用连接池，事务句柄绑定事务。它不是“一条永久连接”，也不是 Java Mapper 接口的一对一翻译。

参考：[GORM 总览](https://gorm.io/docs/)、[MyBatis Mapper XML](https://mybatis.org/mybatis-3/sqlmap-xml.html)、[MyBatis 配置](https://mybatis.org/mybatis-3/configuration.html)。

<a id="ch02"></a>
## 2. PostgreSQL 与 Go 环境准备

### 2.1 启动本地 PostgreSQL

下面是 Bash / WSL / Git Bash 命令。示例账号密码仅用于本地学习。

```bash
docker run -d \
  --name gorm-pg \
  --restart unless-stopped \
  -e POSTGRES_USER=app \
  -e POSTGRES_PASSWORD=app123 \
  -e POSTGRES_DB=gorm_demo \
  -p 127.0.0.1:5432:5432 \
  -v gorm_pg_data:/var/lib/postgresql/data \
  postgres:17
```

PowerShell 可复制这一行：

```powershell
docker run -d --name gorm-pg --restart unless-stopped -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app123 -e POSTGRES_DB=gorm_demo -p 127.0.0.1:5432:5432 -v gorm_pg_data:/var/lib/postgresql/data postgres:17
```

检查是否就绪：

```bash
docker exec gorm-pg pg_isready -U app -d gorm_demo
docker exec -it gorm-pg psql -U app -d gorm_demo
```

在 psql 中可用 `\dt` 查看表，`\d users` 查看表结构，`\q` 退出。

如果容器已经存在，用 `docker start gorm-pg`。若本机 5432 已被占用，映射为 `5433:5432`，并将 Go 的 DSN 端口改为 5433。

注意：初始化环境变量只对空数据目录起作用。修改 `POSTGRES_PASSWORD` 后重启已有数据卷，不会自动修改数据库内的密码。PostgreSQL 18+ 官方镜像的数据卷规则有变化，不能直接照搬本章 17 的挂载路径。

### 2.2 初始化 Go 项目

安装可满足所选依赖要求的 Go 工具链，再执行：

```bash
mkdir gorm-demo
cd gorm-demo
go mod init example.com/gorm-demo
go get gorm.io/gorm
go get gorm.io/driver/postgres
go get github.com/gin-gonic/gin
```

`go get` 解析的具体依赖版本以生成的 `go.mod` 和 `go.sum` 为准。提交这两个文件，以便团队复现；已有项目应明确依赖升级范围，而不是每次启动都执行 `go get -u`。

数据库连接字符串：

```text
host=127.0.0.1 user=app password=app123 dbname=gorm_demo port=5432 sslmode=disable TimeZone=Asia/Shanghai
```

本地容器可使用 `sslmode=disable`；远程环境按数据库服务要求配置 TLS。Go 程序若也运行在容器中，`127.0.0.1` 指向 Go 容器本身，应使用同一 Docker 网络中的 PostgreSQL 服务名。

参考：[PostgreSQL 官方镜像](https://hub.docker.com/_/postgres)、[GORM 数据库连接](https://gorm.io/docs/connecting_to_the_database.html)。

<a id="ch03"></a>
## 3. 数据库连接、连接池与生命周期

### 3.1 建立连接

```go
import (
    "context"
    "fmt"
    "time"

    "gorm.io/driver/postgres"
    "gorm.io/gorm"
)

func OpenDB(dsn string) (*gorm.DB, error) {
    db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{
        TranslateError: true,
    })
    if err != nil {
        return nil, fmt.Errorf("open database: %w", err)
    }

    sqlDB, err := db.DB()
    if err != nil {
        return nil, fmt.Errorf("get connection pool: %w", err)
    }
    sqlDB.SetMaxOpenConns(20)
    sqlDB.SetMaxIdleConns(5)
    sqlDB.SetConnMaxLifetime(30 * time.Minute)
    sqlDB.SetConnMaxIdleTime(5 * time.Minute)

    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    if err := sqlDB.PingContext(ctx); err != nil {
        _ = sqlDB.Close()
        return nil, fmt.Errorf("ping database: %w", err)
    }
    return db, nil
}
```

`gorm.Open` 初始化 GORM 与驱动；`db.DB()` 取到底层连接池。应用启动时创建根句柄并复用，请求结束时不要关闭整个连接池。

### 3.2 与 Java 配置的对应

| Java 中的设置 | Go 设置 | 作用 |
| --- | --- | --- |
| maximumPoolSize | `SetMaxOpenConns` | 最大打开连接数 |
| minimumIdle 类似关注点 | `SetMaxIdleConns` | 最大空闲数；不等价于最低保留数 |
| maxLifetime | `SetConnMaxLifetime` | 单条连接可复用的最长时间 |
| idleTimeout 类似关注点 | `SetConnMaxIdleTime` | 空闲连接可保留时间 |
| 应用销毁时关闭 DataSource | `sqlDB.Close()` | 停止使用整个池 |

池大小应根据数据库连接上限、实例数和负载调整。例如每个实例 20 条连接，10 个实例就可能占用 200 条，而不是总共 20 条。

根 `db` 可以共享；不要跨请求共享带有可变查询条件的链式句柄，也不要把一个事务句柄交给无关请求。

<a id="ch04"></a>
## 4. 模型、字段映射、NULL 与默认值

### 4.1 基础模型

后续基础章节使用以下模型：

```go
import (
    "time"
    "gorm.io/gorm"
)

type User struct {
    ID        uint64         `gorm:"primaryKey" json:"id"`
    Name      string         `gorm:"size:100;not null" json:"name"`
    Email     string         `gorm:"size:255;not null;uniqueIndex" json:"email"`
    Age       int            `gorm:"not null;check:users_age_nonnegative,age >= 0" json:"age"`
    Active    bool           `gorm:"not null" json:"active"`
    CreatedAt time.Time      `json:"created_at"`
    UpdatedAt time.Time      `json:"updated_at"`
    DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
}
```

约定映射：`User` → `users`，`CreatedAt` → `created_at`，`ID` → 主键。字段必须导出，例如 `name string` 不会像 `Name string` 一样参与正常映射。

`gorm` 标签控制数据库映射；`json` 标签控制 JSON。设置 `json:"user_name"` 不会把数据库列自动改成 `user_name`。

### 4.2 自定义表名与列名

```go
func (User) TableName() string {
    return "app_users"
}

// 单独展示标签语法，替换模型中的对应字段即可。
type LegacyUser struct {
    ID       int64  `gorm:"column:user_id;primaryKey"`
    UserName string `gorm:"column:user_name"`
}
```

若给 `User` 增加上述 `TableName()`，后文的原生 SQL 也要改成 `app_users`。本教程其余示例仍使用默认表名 `users`。

MyBatis 中列映射常放在 `resultMap` 或 SQL 别名里；GORM 常放在结构体标签里。查询 DTO 也可以用 `gorm:"column:..."` 对齐结果列。

### 4.3 常用标签

| 标签 | 含义 |
| --- | --- |
| `primaryKey` | 主键 |
| `autoIncrement` | 按方言使用自增机制 |
| `column:name` | 指定列名 |
| `type:jsonb` | 指定 PostgreSQL 类型 |
| `size:100` | 字符字段长度等映射信息 |
| `not null` | 非空约束 |
| `default:18` | 默认值配置 |
| `index` / `uniqueIndex` | 普通 / 唯一索引 |
| `check:age >= 0` | 检查约束 |
| `foreignKey:UserID` | 指定当前关联使用的外键字段 |
| `references:ID` | 指定关联引用的字段 |
| `many2many:user_roles` | 指定多对多中间表 |
| `gorm:"-"` | 忽略字段 |
| `gorm:"->"` | 只读字段 |
| `gorm:"<-:create"` | 允许创建写入，禁止更新写入 |

### 4.4 NULL 与 Go 零值

```go
type Customer struct {
    ID       uint64
    Nickname *string // nil 表示数据库 NULL
    Birthday *time.Time
}
```

Go 的 `string` 零值为 `""`，`int` 为 `0`，`bool` 为 `false`。它们本身不能表达“没有值”。数据库 nullable 字段可用指针或 `sql.NullString` / `sql.NullTime`。输出 JSON 时通常转成响应 DTO，避免直接暴露 `sql.NullString` 的内部结构。

**请求里的三种状态也要区分：**没传字段、传了零值、传了 `null`。普通 `*string` 能区分非空字符串和 nil，但标准 JSON 反序列化时“没传”和“传 null”通常都成为 nil；需要清空 nullable 列时，应使用记录是否出现的自定义可选类型，或显式检查 JSON 字段。

### 4.5 默认值的坑

```go
// 替代示例，不是基础 User 模型的一部分。
type Feature struct {
    ID      uint64
    Enabled *bool `gorm:"default:true"`
}

disabled := false
f := Feature{Enabled: &disabled}
err := db.Create(&f).Error // 显式保存 false
```

普通 `bool` 配合 `default:true` 时，创建阶段的零值 `false` 可能被默认值规则替代。需要区分“未设置”和“明确为 false”时用指针。基础 `User.Active` 不配置 `default:true`，由业务代码明确赋值。

参考：[模型声明](https://gorm.io/docs/models.html)、[创建与默认值](https://gorm.io/docs/create.html)。

<a id="ch05"></a>
## 5. Migration：自动迁移和版本化迁移

### 5.1 AutoMigrate

```go
if err := db.AutoMigrate(&User{}); err != nil {
    return err
}
```

Migration 指 schema 的演进，例如创建表、添加列和建立索引。`AutoMigrate` 可以补建表、列、索引、约束，并在支持的情况下调整某些列属性；它不会为了匹配结构体而自动删除已经不用的列。

把 `Name` 改成 `FullName`，不能假定 GORM 会理解为“重命名列并保留数据”。应写显式迁移。

### 5.2 Migrator

```go
exists := db.Migrator().HasTable(&User{})
_ = exists

// 以下只是操作示例，应按已确认的 schema 状态执行。
err := db.Migrator().RenameColumn(&User{}, "name", "full_name")
```

常见方法还有 `HasColumn`、`AddColumn`、`CreateIndex`、`HasIndex`。`DropColumn` 和 `DropTable` 会删除数据，不能把它们放进普通应用启动流程。

### 5.3 为什么生产使用版本化迁移

项目可以采用：

```text
migrations/
  000001_create_users.up.sql
  000001_create_users.down.sql
  000002_add_users_status.up.sql
  000002_add_users_status.down.sql
```

例如新增字段：

```sql
ALTER TABLE users ADD COLUMN status varchar(20) NOT NULL DEFAULT 'normal';
```

版本化工具负责记录执行历史，发布流程负责审查、锁表影响、兼容性和回滚策略。GORM 不会因为调用了 `AutoMigrate` 就提供完整的迁移版本管理。

MyBatis 通常只负责运行 SQL，建表和 schema 升级由独立工具处理。使用 GORM 后，同样可以继续用版本化 SQL 管理生产数据库。

参考：[Migration](https://gorm.io/docs/migration.html)。

<a id="ch06"></a>
## 6. Create：新增、批量新增与 Upsert

### 6.1 单条新增与主键回填

```go
user := User{Name: "张三", Email: "zhang@example.com", Age: 20, Active: true}
result := db.WithContext(ctx).Create(&user)
if result.Error != nil {
    return result.Error
}
fmt.Println(user.ID, result.RowsAffected)
```

必须传地址以接收生成的主键和时间字段。PostgreSQL 驱动可使用 `RETURNING` 取得生成值；不要照搬某些 JDBC 场景中的 `LastInsertId`。

MyBatis 对应示例：

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
  INSERT INTO users(name, email, age, active)
  VALUES(#{name}, #{email}, #{age}, #{active})
</insert>
```

具体主键回填方式还与 JDBC 驱动和 SQL 写法有关。GORM 把这些方言细节封装在驱动里。

### 6.2 批量新增

```go
users := []User{
    {Name: "李四", Email: "li@example.com", Age: 21, Active: true},
    {Name: "王五", Email: "wang@example.com", Age: 22, Active: false},
}
err := db.WithContext(ctx).CreateInBatches(&users, 100).Error
```

相当于多行 `INSERT`。MyBatis 的 `<foreach>` 拼多行 values 与 JDBC 批执行不是同一件事；GORM 的 `CreateInBatches` 也要按实际 SQL 和事务边界理解，而不是只看“批量”这个名称。

### 6.3 选择插入字段

```go
err := db.Select("Name", "Email", "Age", "Active").Create(&user).Error
```

`Select` / `Omit` 可以限制参与写入的字段。被省略的非空列仍必须满足数据库默认值或其他约束。

### 6.4 Upsert：冲突时更新

```go
import "gorm.io/gorm/clause"

user := User{Name: "张三更新", Email: "zhang@example.com", Age: 25, Active: true}
err := db.WithContext(ctx).Clauses(clause.OnConflict{
    Columns:   []clause.Column{{Name: "email"}},
    DoUpdates: clause.AssignmentColumns([]string{"name", "age", "updated_at"}),
}).Create(&user).Error
```

大致对应 PostgreSQL：

```sql
INSERT INTO users(name, email, age, active, created_at, updated_at)
VALUES (...)
ON CONFLICT (email)
DO UPDATE SET name = EXCLUDED.name,
              age = EXCLUDED.age,
              updated_at = EXCLUDED.updated_at;
```

冲突目标必须有匹配的唯一约束 / 唯一索引。仅在 Go 中先查“是否存在”无法避免并发重复插入；最终由数据库约束兜底。软删除后的 email 是否可重复，见第 9 章。

### 6.5 FirstOrCreate

传统 API 有 `FirstOrCreate`，可以查找后创建，但它不能单独代替数据库唯一约束与原子的冲突处理。处理并发幂等写入时，优先明确唯一键和 `ON CONFLICT` 策略。

参考：[Create](https://gorm.io/docs/create.html)。

<a id="ch07"></a>
## 7. Query：单条、列表、条件与投影

### 7.1 First、Take、Last、Find

| 方法 | 使用目标 | 默认行为 | 没查到 |
| --- | --- | --- | --- |
| `First(&user)` | 一条记录 | 按主键升序取第一条 | `ErrRecordNotFound` |
| `Take(&user)` | 一条记录 | 不额外指定默认顺序 | `ErrRecordNotFound` |
| `Last(&user)` | 一条记录 | 按主键降序取第一条 | `ErrRecordNotFound` |
| `Find(&users)` | 列表 | 没有自动 LIMIT | 通常成功，列表为空 |

```go
var user User
err := db.WithContext(ctx).First(&user, 1).Error

var users []User
err = db.WithContext(ctx).Where("age >= ?", 18).Find(&users).Error
```

`First` 不等价于 MyBatis `selectOne`：它限制最多一条，因此不会因匹配多条自动抛出 `TooManyResultsException`。需要唯一结果时，应由唯一约束保证。

不要用 `Find(&user)` 模拟 `First`：语义容易误解，还可能缺少 `LIMIT 1`。也不要复用已有非零主键的目标结构体查询另一个用户，旧主键可能进入额外条件；创建新的目标变量最清晰。

### 7.2 SQL 条件

```go
var users []User
err := db.WithContext(ctx).
    Where("age >= ? AND active = ?", 18, true).
    Where("name ILIKE ?", "%张%").
    Order("id DESC").
    Limit(20).
    Find(&users).Error
```

`?` 绑定的是值；PostgreSQL 方言会把它转换成所需的参数形式。`ILIKE` 是 PostgreSQL 的不区分大小写模式匹配。参数绑定不会把 `%`、`_` 变成普通字符，搜索要求字面匹配时需单独处理 LIKE 转义。

### 7.3 结构体条件会忽略零值

```go
// false 和 0 默认不会成为筛选条件！
db.Where(&User{Age: 0, Active: false}).Find(&users)

// 明确查询 0 与 false。
err := db.Where(map[string]any{"age": 0, "active": false}).Find(&users).Error
```

与 MyBatis `<if test="age != null">` 不同，Go 普通 `int` 的 `0` 并不表示 null。需要零值条件时使用显式 SQL、map，或显式选择结构体条件字段。

### 7.4 IN、NOT、OR 与分组

```go
err := db.Where("id IN ?", []uint64{1, 2, 3}).Find(&users).Error
err = db.Not("active = ?", true).Find(&users).Error

err = db.Where("active = ?", true).
    Where(db.Where("age < ?", 18).Or("age > ?", 60)).
    Find(&users).Error
```

最后一条对应 `active = true AND (age < 18 OR age > 60)`。不要忽略 SQL 的 AND / OR 优先级。

对于空 ID 列表，先确定业务语义：通常返回空结果；不要为了“没有筛选值”而意外移除整个 IN 条件。

### 7.5 查询特定列与 DTO

```go
type UserSummary struct {
    ID   uint64 `json:"id"`
    Name string `json:"name"`
}

var summaries []UserSummary
err := db.Model(&User{}).Select("id", "name").Scan(&summaries).Error

var emails []string
err = db.Model(&User{}).Where("active = ?", true).Pluck("email", &emails).Error
```

`Scan` 是把查询结果映射到目标结构体，`Pluck` 适合读取一列。类似 MyBatis 的 `resultType` / `resultMap`，但字段名和 SQL 别名需要对齐。

### 7.6 Model 与 Table

```go
db.Model(&User{}).Where("age >= ?", 18).Find(&users)
db.Table("users").Select("id", "name").Scan(&summaries)
```

`Model` 提供模型信息，例如表名、关联和软删除规则；`Table` 指定表表达式。不能仅凭 `Table("users")` 就认为框架完整了解 User 的软删除等行为；模型未知的 DTO、原生 SQL 和手写 Join 条件需要显式处理 `deleted_at`。

参考：[Query](https://gorm.io/docs/query.html)、[Advanced Query](https://gorm.io/docs/advanced_query.html)。

<a id="ch08"></a>
## 8. Update：零值、字段选择与 Save

### 8.1 单字段更新

```go
result := db.WithContext(ctx).Model(&User{}).
    Where("id = ?", 1).
    Update("active", false)
if result.Error != nil {
    return result.Error
}
if result.RowsAffected == 0 {
    return gorm.ErrRecordNotFound
}
```

单字段 `Update` 可以更新零值，通常触发更新 Hooks 并维护 `UpdatedAt`。

### 8.2 Updates(struct) 与 Updates(map)

```go
// 默认只更新非零字段；Age=0、Active=false 被忽略。
err := db.Model(&User{}).Where("id = ?", 1).
    Updates(User{Name: "新名字", Age: 0, Active: false}).Error

// map 可明确写入 0、false、空字符串及 nil。
err = db.Model(&User{}).Where("id = ?", 1).
    Updates(map[string]any{"name": "新名字", "age": 0, "active": false}).Error

// 显式选择 struct 字段，也能写入对应零值。
err = db.Model(&User{}).Where("id = ?", 1).
    Select("Age", "Active").
    Updates(User{Age: 0, Active: false}).Error
```

MyBatis 的动态 `<set>` 常按“字段是否为 null”判断；GORM 结构体更新默认按“字段是否为 Go 零值”判断。这是迁移时最常见的区别之一。

### 8.3 PATCH 请求使用指针 DTO

```go
type UpdateUserRequest struct {
    Name   *string `json:"name"`
    Age    *int    `json:"age"`
    Active *bool   `json:"active"`
}

updates := map[string]any{}
if req.Name != nil { updates["name"] = *req.Name }
if req.Age != nil { updates["age"] = *req.Age }
if req.Active != nil { updates["active"] = *req.Active }
```

通过 map 白名单避免请求直接修改主键、角色、创建时间等字段。`{"active":false}` 应更新为 false；`{}` 应按 API 契约拒绝或作为无操作。nullable 列的 `null` 清空语义另行设计。

### 8.4 Save 为什么不适合随意使用

传统 API 的 `Save(&user)` 具有“保存全部字段”的语义：没有主键时创建，有主键时先更新全部字段，更新没有匹配行时可能回退为创建。

这意味着：部分加载的结构体可能覆盖未加载列；“修改不存在的用户”可能变成“创建用户”。普通 PATCH 推荐 `Where(...).Updates(...)`；不要把 `Save` 当成 MyBatis 的 update 方法。也不要把 `Model(...).Save(...)` 作为常规写法，其行为没有可靠保证。

### 8.5 数据库表达式与跳过 Hooks

```go
err := db.Model(&User{}).Where("id = ?", 1).
    Update("age", gorm.Expr("age + ?", 1)).Error

// 跳过更新 Hooks 与自动 UpdatedAt 维护。
err = db.Model(&User{}).Where("id = ?", 1).
    UpdateColumn("active", false).Error
```

`UpdateColumn` 不是更安全的普通 `Update` 替代品，应在理解生命周期差异后使用。

### 8.6 条件与全表保护

无条件批量更新通常返回 `gorm.ErrMissingWhereClause`，但显式 `Where("1=1")` 和原生 SQL 能绕开这种保护。业务代码仍必须明确条件，不能把框架保护当作授权控制。

参考：[Update](https://gorm.io/docs/update.html)。

<a id="ch09"></a>
## 9. Delete：软删除、恢复与物理删除

### 9.1 模型决定删除方式

基础 `User` 包含 `gorm.DeletedAt`：

```go
result := db.WithContext(ctx).Where("id = ?", 1).Delete(&User{})
```

其语义近似：

```sql
UPDATE users SET deleted_at = now()
WHERE id = 1 AND deleted_at IS NULL;
```

普通模型查询自动排除软删除记录。仅定义名叫 `DeletedAt` 的普通 `time.Time` 字段，不等于已经配置了同样的软删除机制。

MyBatis 核心不会自动补逻辑删除条件，通常需要自行写 SQL，或使用额外框架功能。原生 SQL 也需要手写软删除过滤条件。

### 9.2 查询删除记录、恢复和永久删除

```go
var users []User
err := db.Unscoped().Find(&users).Error

// 恢复软删除记录。这里假设没有与现存数据冲突的约束。
err = db.Unscoped().Model(&User{}).Where("id = ?", 1).
    Update("deleted_at", nil).Error

// 永久删除，不能靠恢复 deleted_at 撤销。
err = db.Unscoped().Where("id = ?", 1).Delete(&User{}).Error
```

无条件删除也有全表保护，仍要明确授权与 WHERE。

### 9.3 软删除与唯一索引

基础模型的 `uniqueIndex` 覆盖 email 的所有记录，因此软删除后 email 仍被占用。这是一种明确的业务选择。

若业务要求“只有未删除用户的 email 唯一”，PostgreSQL 可以用部分唯一索引：

```sql
-- 由独立迁移创建；替换原本覆盖所有行的 email 唯一索引。
CREATE UNIQUE INDEX ux_users_live_email
ON users(email)
WHERE deleted_at IS NULL;
```

此时模型与迁移要保持一致，不能又自动生成原来的全量唯一索引。Upsert 冲突目标也需要匹配该索引的谓词。不要简单加 `(email, deleted_at)` 唯一索引并假定 NULL 会像普通值一样相互冲突；PostgreSQL 默认的 NULL 唯一性语义不是这样。

### 9.4 软删除不等于级联物理删除

数据库外键的 `ON DELETE CASCADE` 在真正的 DELETE 时起作用；软删除是 UPDATE，不会因此自动删除关联订单。关联清理、保留历史和恢复策略需要单独设计。

参考：[Delete](https://gorm.io/docs/delete.html)、[PostgreSQL 约束](https://www.postgresql.org/docs/current/ddl-constraints.html)。

<a id="ch10"></a>
## 10. 动态 SQL、Scopes、分页与查询复用

### 10.1 MyBatis 动态 SQL 对照

```xml
<select id="list" resultType="User">
  SELECT * FROM users
  <where>
    deleted_at IS NULL
    <if test="name != null and name != ''">
      AND name LIKE #{pattern}
    </if>
    <if test="active != null">
      AND active = #{active}
    </if>
  </where>
  ORDER BY id DESC
</select>
```

`pattern` 由调用方构建，例如 `%张%`。对应 GORM：

```go
q := db.WithContext(ctx).Model(&User{})
if name != "" {
    q = q.Where("name LIKE ?", "%"+name+"%")
}
if active != nil {
    q = q.Where("active = ?", *active)
}
var users []User
err := q.Order("id DESC").Find(&users).Error
```

不需要 XML `<where>` 来处理首个 AND，条件由构造器合并。调用链应接收返回句柄，业务筛选状态用 Go 类型清楚表达。

### 10.2 Scopes 复用条件

```go
func ActiveOnly(db *gorm.DB) *gorm.DB {
    return db.Where("active = ?", true)
}

func Page(page, size int) func(*gorm.DB) *gorm.DB {
    if page < 1 { page = 1 }
    if size < 1 { size = 20 }
    if size > 100 { size = 100 }
    return func(db *gorm.DB) *gorm.DB {
        return db.Offset((page - 1) * size).Limit(size)
    }
}

err := db.Model(&User{}).Scopes(ActiveOnly, Page(1, 20)).Find(&users).Error
```

Scope 类似“可复用查询片段”，例如 MyBatis `<sql>` / `<include>` 的某些用途，但它是 Go 函数，不是 XML 文本模板。输入仍需验证，例如 page 应有合理上限。

### 10.3 分页：总数与列表独立构造

```go
func ListUsers(ctx context.Context, db *gorm.DB, page, size int) ([]User, int64, error) {
    if page < 1 || page > 100000 || size < 1 || size > 100 {
        return nil, 0, fmt.Errorf("invalid pagination")
    }
    build := func() *gorm.DB {
        return db.WithContext(ctx).Model(&User{}).Where("active = ?", true)
    }

    var total int64
    if err := build().Count(&total).Error; err != nil {
        return nil, 0, err
    }
    users := make([]User, 0)
    err := build().Order("id DESC").
        Offset((page - 1) * size).Limit(size).Find(&users).Error
    return users, total, err
}
```

`Count` 应统计相同筛选范围，不应误带分页 LIMIT / OFFSET。上面用构造函数每次生成独立链，避免查询状态污染。两条 SQL 在并发写入时可能看到不同快照；要求严格一致时，需要合适的事务与隔离级别。

### 10.4 游标分页

大偏移量分页可能越来越慢。可以改用最后一条记录的 id：

```go
q := db.WithContext(ctx).Model(&User{})
if lastID > 0 {
    q = q.Where("id < ?", lastID)
}
err := q.Order("id DESC").Limit(20).Find(&users).Error
```

排序列不是唯一键时，需要唯一键作为第二排序项并构造对应复合游标。游标分页通常不提供“任意跳转到第 N 页”的同等体验。

### 10.5 查询句柄复用

```go
// 条件链直接重复分支使用，可能累积条件。
base := db.Where("active = ?", true)
base.Where("age > ?", 18).Find(&adults)
base.Where("age < ?", 18).Find(&children)
```

上面可能让第二次查询同时带入 `age > 18` 和 `age < 18`。推荐独立构造，或使用新会话：

```go
base := db.Model(&User{}).Where("active = ?", true).Session(&gorm.Session{})
err1 := base.Where("age >= ?", 18).Find(&adults).Error
err2 := base.Where("age < ?", 18).Find(&children).Error
```

根 `db` 共享与条件链共享是两件事。不要把附带某个请求筛选条件的 `base` 存到全局。

参考：[MyBatis Dynamic SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)、[Scopes](https://gorm.io/docs/scopes.html)、[Method Chaining](https://gorm.io/docs/method_chaining.html)。

<a id="ch11"></a>
## 11. 关联模型：一对一、一对多与多对多

本章使用替代模型，演示用户、公司、档案、订单和角色。不要把它与前面的 `User` 重复声明在一个 package 里。

```go
type Company struct {
    ID   uint64 `gorm:"primaryKey"`
    Name string
}

type Profile struct {
    ID     uint64 `gorm:"primaryKey"`
    UserID uint64 `gorm:"not null;uniqueIndex"`
    Bio    string
}

type Order struct {
    ID         uint64 `gorm:"primaryKey"`
    UserID     uint64 `gorm:"not null;index"`
    TotalCents int64  `gorm:"not null"`
    Status     string
}

type Role struct {
    ID   uint64 `gorm:"primaryKey"`
    Name string `gorm:"uniqueIndex"`
}

type User struct {
    ID        uint64 `gorm:"primaryKey"`
    Name      string
    CompanyID *uint64
    Company   *Company `gorm:"foreignKey:CompanyID;references:ID"`
    Profile   *Profile `gorm:"foreignKey:UserID;references:ID"`
    Orders    []Order  `gorm:"foreignKey:UserID;references:ID"`
    Roles     []Role   `gorm:"many2many:user_roles;"`
}

err := db.AutoMigrate(&Company{}, &User{}, &Profile{}, &Order{}, &Role{})
```

| 关系 | 示例 | 外键在哪 | MyBatis 结果映射类比 |
| --- | --- | --- | --- |
| belongs to | User 属于 Company | `users.company_id` | 单对象 `association` |
| has one | User 有一个 Profile | `profiles.user_id` | 单对象 `association` |
| has many | User 有多个 Order | `orders.user_id` | 列表 `collection` |
| many to many | User 有多个 Role | `user_roles` 中间表 | 列表映射 + 中间表 SQL |

`foreignKey` 和 `references` 填的是 **Go 字段名**，而 SQL 条件通常写数据库列名。一对一的真实唯一性还要由数据库唯一约束保证，仅把字段类型写成 `Profile` 不足以禁止多个 profile 关联同一用户。

### 11.1 创建时的关联写入

```go
user := User{
    Name:    "赵六",
    Profile: &Profile{Bio: "Go 开发者"},
    Orders:  []Order{{TotalCents: 1000, Status: "new"}},
}
err := db.WithContext(ctx).Create(&user).Error
```

GORM 可保存关联并处理外键。若只想创建主记录，应明确省略：

```go
err := db.Omit(clause.Associations).Create(&user).Error
```

自动关联保存不是“任意对象图全量同步”。已经存在的关联对象、关联属性修改和删除策略应逐项明确；必要时使用显式写入或 `FullSaveAssociations`，并审查实际 SQL。

### 11.2 Association Mode

在关联操作前加载或明确指定用户主键：

```go
var user User
if err := db.First(&user, userID).Error; err != nil {
    return err
}
var role Role
if err := db.First(&role, roleID).Error; err != nil {
    return err
}

err := db.WithContext(ctx).Model(&user).Association("Roles").Append(&role)
err = db.WithContext(ctx).Model(&user).Association("Roles").Delete(&role)
```

对于多对多，上面的关联 `Delete` 通常移除中间表关系，不会删除 `roles` 中的角色记录。`Clear` / `Replace` 的重点也是关联关系，不能按普通 `db.Delete(&Role{})` 理解。

对于 has many，解除关系可能涉及外键置 NULL；如果外键不允许 NULL，应采用明确的删除或重新归属策略。

参考：[Belongs To](https://gorm.io/docs/belongs_to.html)、[Has One](https://gorm.io/docs/has_one.html)、[Has Many](https://gorm.io/docs/has_many.html)、[Many To Many](https://gorm.io/docs/many_to_many.html)、[Associations](https://gorm.io/docs/associations.html)。

<a id="ch12"></a>
## 12. Preload、Join、结果映射与 N+1

### 12.1 Preload：先查主表，再批量查关联

沿用第 11 章模型：

```go
var users []User
err := db.WithContext(ctx).
    Preload("Company").
    Preload("Profile").
    Preload("Orders", "status = ?", "paid").
    Find(&users).Error
```

对于订单，SQL 形态近似：

```sql
SELECT * FROM users;
SELECT * FROM orders WHERE user_id IN (...) AND status = 'paid';
```

`Preload("Orders")` 里的 `Orders` 是模型字段名，不是表名。预加载通常不是一个 Join。单个一对多关联常用一次主查询加一次批量关联查询，具体条数随关联、嵌套和中间表而变化。

Preload 的条件筛选关联列表，**不会因此把没有 paid 订单的用户移除**。需要“只查询有 paid 订单的用户”，应使用 EXISTS 子查询或明确的 Join 条件。

### 12.2 关联 Join

```go
err := db.WithContext(ctx).Joins("Company").Find(&users).Error
```

关联 Join Preload 适合 has one / belongs to 等单对象关系。不要假定它能把普通一对多 Join 的多行结果自动、完整地去重并装进切片。

MyBatis 的 `resultMap` 可以通过 `<id>`、`<association>` 和 `<collection>` 组织 Join 行；GORM 的扫描机制与其并非一对一等价。

### 12.3 手写 Join + DTO

```go
type UserOrderRow struct {
    UserID     uint64 `gorm:"column:user_id"`
    UserName   string `gorm:"column:user_name"`
    OrderID    uint64 `gorm:"column:order_id"`
    TotalCents int64  `gorm:"column:total_cents"`
}

var rows []UserOrderRow
err := db.Table("users AS u").
    Select("u.id AS user_id, u.name AS user_name, o.id AS order_id, o.total_cents").
    Joins("JOIN orders AS o ON o.user_id = u.id").
    Where("o.status = ?", "paid").Scan(&rows).Error
```

这会得到扁平行，一个用户有多个订单就有多行。需要树形结果时，在 Go 中按 `UserID` 分组，或用 `Preload`。这里第 11 章模型没有软删除；若换成有软删除的真实模型，应显式加入主表和关联表的过滤条件。

Join 后直接 `Count` 可能统计订单行而不是用户数，用户总数可以采用 `COUNT(DISTINCT u.id)` 或 EXISTS。Join 行上的 LIMIT 也不等于“限制 N 个完整用户及全部订单”。

### 12.4 避免 N+1

```go
// 容易出现 1 + N 次查询。
db.Find(&users)
for i := range users {
    db.Model(&users[i]).Association("Orders").Find(&users[i].Orders)
}
```

改用分页主查询 + `Preload("Orders")`，或先收集主键批量查询。GORM 不会因为你读取 `user.Orders` 字段就自动像代理对象那样延迟加载数据库。

参考：[Preloading](https://gorm.io/docs/preload.html)、[MyBatis Result Maps](https://mybatis.org/mybatis-3/sqlmap-xml.html)。

<a id="ch13"></a>
## 13. 原生 SQL、聚合、子查询与批处理

### 13.1 Raw 查询、Exec 写入

回到第 4 章基础模型：

```go
var users []User
err := db.WithContext(ctx).Raw(
    "SELECT * FROM users WHERE age >= ? AND deleted_at IS NULL ORDER BY id DESC LIMIT ?",
    18, 20,
).Scan(&users).Error

result := db.WithContext(ctx).Exec(
    "UPDATE users SET active = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL",
    false, 1,
)
```

`Raw` + `Scan` 适合手写查询；`Exec` 适合无需扫描结果的写操作。带 `RETURNING` 且要读取结果时，可以使用 `Raw(...).Scan(...)`。

原生 SQL 不会自动补 User 的软删除条件，也不会像模型 `Update` 一样替你实现所有 Hooks / 时间维护。若查询需要这些规则，自己写完整 SQL。

### 13.2 命名参数

```go
import "database/sql"

err := db.Raw(
    "SELECT * FROM users WHERE name = @name AND age >= @age AND deleted_at IS NULL",
    sql.Named("name", "张三"), sql.Named("age", 18),
).Scan(&users).Error
```

这类似 MyBatis 方法参数配合 `@Param` 和 `#{name}`，但机制与语法不同。

### 13.3 聚合

```go
type AgeCount struct {
    Age   int
    Total int64
}

var stats []AgeCount
err := db.Model(&User{}).
    Select("age, COUNT(*) AS total").
    Group("age").Having("COUNT(*) >= ?", 2).
    Scan(&stats).Error
```

模型已知时可以应用 User 的软删除规则。结果别名 `total` 与 DTO 的 `Total` 对齐。`COUNT` 结果用 `int64`，不要假定它永远适合小整数。

### 13.4 子查询

```go
sub := db.Model(&User{}).Select("AVG(age)")
err := db.Model(&User{}).Where("age > (?)", sub).Find(&users).Error
```

拥有订单的用户也可以使用固定 SQL 的 EXISTS：

```go
err := db.Model(&User{}).Where(
    "EXISTS (SELECT 1 FROM orders o WHERE o.user_id = users.id AND o.status = ?)",
    "paid",
).Find(&users).Error
```

本例需要已创建的 orders 表。若订单也有软删除，子查询需加订单自己的过滤条件。

### 13.5 大数据分批读取

```go
batch := make([]User, 0, 500)
err := db.WithContext(ctx).Where("active = ?", true).
    FindInBatches(&batch, 500, func(tx *gorm.DB, batchNo int) error {
        for _, user := range batch {
            // 处理本批，例如写入导出流。
            _ = user
        }
        return nil
    }).Error
```

避免把百万条记录一次 `Find` 到内存。`FindInBatches` 不是自动包住所有批次的全局事务；长时间导出时仍需考虑并发修改和快照要求。

参考：[SQL Builder](https://gorm.io/docs/sql_builder.html)、[Advanced Query](https://gorm.io/docs/advanced_query.html)。

<a id="ch14"></a>
## 14. 事务、隔离级别与保存点

### 14.1 默认写事务不是业务事务

GORM 默认会为常见模型写操作使用事务机制。但连续两次 `db.Update(...)` 不因此组成一个原子业务事务。多个写操作要么都成功、要么都撤销，需要显式 `Transaction`。

### 14.2 Transaction 回调

下面是独立 Account 示例，余额单位为分：

```go
type Account struct {
    ID           uint64 `gorm:"primaryKey"`
    BalanceCents int64  `gorm:"not null;check:accounts_balance_nonnegative,balance_cents >= 0"`
}

func Transfer(ctx context.Context, db *gorm.DB, fromID, toID uint64, cents int64) error {
    if cents <= 0 || fromID == 0 || toID == 0 || fromID == toID {
        return fmt.Errorf("invalid transfer")
    }
    return db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
        debit := tx.Model(&Account{}).
            Where("id = ? AND balance_cents >= ?", fromID, cents).
            Update("balance_cents", gorm.Expr("balance_cents - ?", cents))
        if debit.Error != nil { return debit.Error }
        if debit.RowsAffected != 1 {
            return fmt.Errorf("source missing or balance insufficient")
        }

        credit := tx.Model(&Account{}).Where("id = ?", toID).
            Update("balance_cents", gorm.Expr("balance_cents + ?", cents))
        if credit.Error != nil { return credit.Error }
        if credit.RowsAffected != 1 {
            return fmt.Errorf("destination missing")
        }
        return nil
    })
}
```

返回 nil 才提交，返回 error 则回滚；提交失败也通过 `Transaction` 的返回错误报告。**事务里所有数据库操作必须使用 `tx`**，误用根 `db` 可能在事务之外执行。

转账是事务边界的教学例子，不是完整资金系统。双向转账可能形成死锁，真实系统应设计统一的加锁顺序、幂等键和有限重试，并保证重试不会重复外部副作用。

### 14.3 与 Spring 事务的类比

```java
@Transactional
public void transfer(...) {
    accountMapper.debit(...);
    accountMapper.credit(...);
}
```

Spring 通常通过事务管理器与当前线程绑定的资源协调 Mapper；GORM 通过回调里的 `tx` 显式传递事务资源。不能只给 Go 方法起名 `Transactional`，或使用普通根句柄，就期待相同的自动传播。

### 14.4 手动事务

```go
func InsertUser(ctx context.Context, db *gorm.DB, user *User) error {
    tx := db.WithContext(ctx).Begin()
    if tx.Error != nil { return tx.Error }
    defer tx.Rollback() // 已提交后不会再撤销；panic 路径也释放事务

    if err := tx.Create(user).Error; err != nil {
        return err
    }
    return tx.Commit().Error
}
```

普通业务优先回调写法，减少遗漏 Begin / Commit 错误与回滚路径。

### 14.5 隔离级别

```go
err := db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
    // Count 和列表在同一 Repeatable Read 事务中读取一致快照。
    return nil
}, &sql.TxOptions{Isolation: sql.LevelRepeatableRead, ReadOnly: true})
```

PostgreSQL 默认 Read Committed 通常为每条语句取得快照，而不是整个事务固定一个快照。Repeatable Read / Serializable 提供不同保证，也可能带来重试需求。隔离级别是数据库语义，不由 ORM 名称决定。

### 14.6 保存点与嵌套事务

```go
err := db.Transaction(func(tx *gorm.DB) error {
    if err := tx.SavePoint("before_optional_step").Error; err != nil {
        return err
    }
    if err := optionalWrite(tx); err != nil {
        if rollbackErr := tx.RollbackTo("before_optional_step").Error; rollbackErr != nil {
            return rollbackErr
        }
        // 业务确认此步骤可忽略后继续。
    }
    return nil
})
```

`optionalWrite` 是业务自定义函数。GORM 的嵌套 `Transaction` 可通过保存点实现局部回滚；不要直接等同于 Spring 所有传播级别，尤其不是“自动新建一个独立提交的事务”。

PostgreSQL 事务里的语句失败后，事务通常进入失败状态；要继续使用，必须回滚事务或回滚到适当保存点，不能仅忽略 Go error。

参考：[GORM Transactions](https://gorm.io/docs/transactions.html)、[MyBatis-Spring Transactions](https://mybatis.org/spring/transactions.html)、[PostgreSQL 隔离级别](https://www.postgresql.org/docs/current/transaction-iso.html)。

<a id="ch15"></a>
## 15. 并发更新、悲观锁与乐观锁

### 15.1 尽量使用原子条件更新

先 SELECT 库存，再在 Go 中减 1 后保存，会产生并发覆盖。改用带条件的 SQL 更新：

```go
type Product struct {
    ID      uint64 `gorm:"primaryKey"`
    Stock   int64
    Version int64 `gorm:"not null;default:1"`
}

result := db.Model(&Product{}).
    Where("id = ? AND stock >= ?", productID, quantity).
    Update("stock", gorm.Expr("stock - ?", quantity))
```

执行前校验 `quantity > 0`，检查 `.Error` 与 `RowsAffected`。`RowsAffected == 0` 可能表示库存不足或记录不存在，需要按业务返回结果。

### 15.2 悲观锁 FOR UPDATE

```go
err := db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
    var product Product
    if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
        First(&product, productID).Error; err != nil {
        return err
    }
    if quantity <= 0 || product.Stock < quantity {
        return fmt.Errorf("invalid quantity or insufficient stock")
    }
    return tx.Model(&product).Update("stock", product.Stock-quantity).Error
})
```

锁必须置于覆盖“读取 + 更新”的同一事务中。若只在单独自动提交的 SELECT 上加锁，语句完成后锁就释放，无法保护后续独立更新。

锁会等待、争用甚至形成死锁；事务内不要执行耗时的网络请求。

### 15.3 显式乐观锁

```go
// 假设 product 是之前读取的快照，nextStock 已完成校验。
result := db.WithContext(ctx).Model(&Product{}).
    Where("id = ? AND version = ?", product.ID, product.Version).
    Updates(map[string]any{
        "stock": nextStock,
        "version": gorm.Expr("version + 1"),
    })
if result.Error != nil { return result.Error }
if result.RowsAffected != 1 {
    return fmt.Errorf("concurrent modification or product missing")
}
```

普通 `Version int64` 不会自动启用乐观锁；上面的 WHERE 与版本递增是手动实现。MyBatis 中也可手写相同 SQL，或使用额外插件。若选用 GORM 乐观锁插件，应按该插件的类型和行为配置。

参考：[Advanced Query：Locking](https://gorm.io/docs/advanced_query.html)。

<a id="ch16"></a>
## 16. Hooks、Context 与错误处理

### 16.1 Hooks：模型生命周期方法

```go
func (u *User) BeforeCreate(tx *gorm.DB) error {
    u.Email = strings.ToLower(strings.TrimSpace(u.Email))
    if strings.TrimSpace(u.Name) == "" {
        return fmt.Errorf("name is required")
    }
    return nil
}
```

常见生命周期有 `BeforeCreate`、`AfterCreate`、`BeforeUpdate`、`AfterUpdate`、`BeforeDelete`、`AfterDelete`、`AfterFind`，以及保存相关钩子。

Hook 返回错误会阻止后续流程，在事务内还会引起回滚。Hook 内操作数据库时使用传入的 `tx`。批量操作的接收对象不一定是逐行加载的完整记录，不要依赖未读取字段进行复杂业务授权。

MyBatis Interceptor 更偏框架执行链拦截；GORM Hook 更偏模型生命周期，不能认为二者完全相同。普通业务编排放在 Service 更容易检查。

Hook 里的发邮件、调用远程服务等外部效果不能由数据库事务撤销；需要可靠异步通知时可设计 outbox，而不是把不可撤销操作放在 `AfterCreate` 中假定万无一失。

### 16.2 Context：超时、取消与请求信息

```go
ctx, cancel := context.WithTimeout(parentCtx, 3*time.Second)
defer cancel()

var user User
err := db.WithContext(ctx).First(&user, userID).Error
```

Gin 中用 `c.Request.Context()` 作为父 Context。不要用每个请求独立的 `context.Background()` 替代请求 Context，否则客户端断开时取消信号无法沿正常路径传递。

Context 可以传递 tracing 信息，但它不是事务；`WithContext` 不会自动开启业务事务。

### 16.3 错误处理

```go
switch {
case errors.Is(err, gorm.ErrRecordNotFound):
    // 单条查询未找到。
case errors.Is(err, gorm.ErrDuplicatedKey):
    // 已开启 TranslateError，唯一约束冲突。
case errors.Is(err, gorm.ErrForeignKeyViolated):
    // 外键约束冲突。
case errors.Is(err, context.DeadlineExceeded):
    // 请求超时；具体驱动错误也可能需要分类。
case err != nil:
    // 日志记录内部详情，API 返回稳定错误信息。
}
```

传统 API 的主要执行错误从 finisher 返回句柄的 `.Error` 读取。`RowsAffected` 不是错误替代品。列表为空通常不报 `ErrRecordNotFound`；更新 / 删除无匹配行也通常需要自行检查受影响行数。

启用 `TranslateError` 可使用部分统一错误，但会失去某些驱动错误细节。若业务需要 SQLSTATE、约束名等，应选择保留驱动原始错误并按当前驱动类型分类。不要只匹配错误字符串里的中文或英文片段。

参考：[Hooks](https://gorm.io/docs/hooks.html)、[Context](https://gorm.io/docs/context.html)、[Error Handling](https://gorm.io/docs/error_handling.html)。

<a id="ch17"></a>
## 17. PostgreSQL 类型、JSONB 与约束

### 17.1 类型选择

| 数据含义 | Go 常见表示 | PostgreSQL 常见选择 | 说明 |
| --- | --- | --- | --- |
| 主键 | `int64` / `uint64` | `bigint` + identity/sequence | PG 没有通用 unsigned bigint；范围按数据库约束 |
| 文本 | `string` | `text` / `varchar(n)` | 是否限制长度按业务决定 |
| 开关 | `bool` | `boolean` | 不需要 Java 式 0/1 标记 |
| 时间点 | `time.Time` | `timestamptz` | 不保存原始时区名称，显示受会话时区影响 |
| 可空文本 | `*string` / `sql.NullString` | nullable text | 区分空字符串和 NULL |
| 金额 | 整数分 / decimal 类型 | `bigint` / `numeric(p,s)` | 精确金额避免 float64 |
| JSON 对象 | 自定义 Scanner/Valuer | `jsonb` | 支持 JSONB 查询与索引 |
| UUID | 支持驱动转换的 UUID 类型 | `uuid` | 需明确生成位置与类型实现 |

### 17.2 自定义 JSONB：类比 TypeHandler

下面以 map 为例，展示数据库类型的双向转换：

```go
import (
    "database/sql/driver"
    "encoding/json"
    "fmt"
)

type JSONMap map[string]any

func (j JSONMap) Value() (driver.Value, error) {
    if j == nil { return nil, nil }
    b, err := json.Marshal(j)
    if err != nil { return nil, err }
    return string(b), nil
}

func (j *JSONMap) Scan(value any) error {
    if value == nil { *j = nil; return nil }
    var data []byte
    switch v := value.(type) {
    case []byte:
        data = v
    case string:
        data = []byte(v)
    default:
        return fmt.Errorf("unsupported JSONB value: %T", value)
    }
    var decoded map[string]any
    if err := json.Unmarshal(data, &decoded); err != nil { return err }
    *j = JSONMap(decoded)
    return nil
}

type AuditEvent struct {
    ID      uint64  `gorm:"primaryKey"`
    Payload JSONMap `gorm:"type:jsonb"`
}
```

`Value()` 负责 Go → SQL 参数；`Scan()` 负责数据库值 → Go。它类似 MyBatis `TypeHandler` 的读写转换职责，但遵循 Go 标准数据库接口。

写入和查询：

```go
event := AuditEvent{Payload: JSONMap{"action": "login", "ip": "127.0.0.1"}}
err := db.Create(&event).Error

var events []AuditEvent
err = db.Where("payload ->> 'action' = ?", "login").Find(&events).Error
```

大规模 JSONB 查询需按实际表达式设计索引。JSON 数字解码成 `any` 时通常成为浮点表示，对精确整数要用明确结构体或合适的解码方式。

### 17.3 约束承担最终一致性

应用层校验提供更友好的错误；数据库的 NOT NULL、CHECK、UNIQUE、FOREIGN KEY 负责阻止并发与其他入口写入非法数据。不能因为 Gin 校验过就删除数据库约束。

邮箱是否忽略大小写要形成统一策略：例如在所有写入入口规范化后建立普通唯一索引，或通过数据库表达式索引保证。只在某个 HTTP 接口转小写，无法覆盖其他写入者。

参考：[自定义数据类型](https://gorm.io/docs/data_types.html)、[Indexes](https://gorm.io/docs/indexes.html)、[PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)。

<a id="ch18"></a>
## 18. 日志、SQL 安全与性能

### 18.1 SQL 日志

```go
import (
    "log"
    "os"
    "time"
    "gorm.io/gorm/logger"
)

gormLogger := logger.New(log.New(os.Stdout, "gorm ", log.LstdFlags), logger.Config{
    SlowThreshold:        200 * time.Millisecond,
    LogLevel:             logger.Warn,
    ParameterizedQueries: true,
    Colorful:             false,
})

db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{Logger: gormLogger})
```

局部 `db.Debug()` 可以查看生成 SQL。生产日志考虑敏感参数、日志量和访问权限。日志中展示的 SQL 可能不是原样可安全执行的 SQL，不能把它当作经过安全转义的脚本。

### 18.2 DryRun

```go
var users []User
stmt := db.Session(&gorm.Session{DryRun: true}).
    Where("age >= ?", 18).Find(&users).Statement
fmt.Println(stmt.SQL.String())
fmt.Println(stmt.Vars)
```

DryRun 构建 SQL，不实际查询数据库。它适合检查 SQL 形态，但不能验证数据库执行、约束、锁等待和查询计划。

### 18.3 值参数安全与标识符白名单

```go
// 参数绑定：输入作为值。
db.Where("name = ?", inputName).Find(&users)

// 危险示例：外部输入改变 SQL 结构。
db.Where("name = '" + inputName + "'").Find(&users)
```

`?` 不能代替排序列名、表名或 SQL 运算符。不要直接 `Order(c.Query("sort"))`：

```go
orders := map[string]string{
    "newest": "id DESC",
    "oldest": "id ASC",
    "age":    "age DESC, id DESC",
}
order, ok := orders[sortKey]
if !ok { order = orders["newest"] }
err := db.Order(order).Find(&users).Error
```

MyBatis `#{}` 与 `${}` 的区别可以类比“绑定值”与“替换 SQL 文本”。GORM 的 `Order`、`Select`、`Table`、`Joins`、`gorm.Expr` 等位置也可能接受 SQL 片段；框架不会把任意拼接都变安全。

### 18.4 性能检查顺序

1. 先看实际 SQL、查询次数、返回行数和耗时。
2. 为高频筛选 / 排序设计索引，并确认软删除条件的影响。
3. 避免 N+1、`SELECT *`、无上限列表和大 OFFSET。
4. 合理使用批量插入、分批导出与连接池。
5. 使用 PostgreSQL `EXPLAIN`，必要时在可控环境执行 `EXPLAIN ANALYZE`。
6. 最后再评估准备语句缓存、默认写事务开关和读写分离。

```sql
EXPLAIN
SELECT id, name FROM users
WHERE active = true AND deleted_at IS NULL
ORDER BY id DESC LIMIT 20;
```

`EXPLAIN ANALYZE` 会真正执行语句。修改类 SQL 不能随便在生产执行来“看看耗时”。

`PrepareStmt` 缓存准备语句，不缓存查询结果；PostgreSQL 驱动本身也可能已有准备语句缓存，是否启用额外机制需看驱动、连接代理和测量结果。`SkipDefaultTransaction` 改变写入边界，应先确认关联写入和失败回滚语义，不能只为追求速度盲目打开。

参考：[Logger](https://gorm.io/docs/logger.html)、[Security](https://gorm.io/docs/security.html)、[Performance](https://gorm.io/docs/performance.html)。

<a id="ch19"></a>
## 19. 完整实战：Gin + GORM + PostgreSQL

本章是一套独立的本地学习项目。它使用单文件便于复制，包含连接池、可选自动迁移、DTO 校验、分页筛选、软删除、错误转换与正常退出。更大的项目按第 20 章拆层。

### 19.1 接口约定

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/health` | 数据库健康检查 |
| POST | `/users` | 创建用户 |
| GET | `/users` | 分页列表，可按 name、active 筛选 |
| GET | `/users/:id` | 用户详情 |
| PATCH | `/users/:id` | 修改 name、age、active |
| DELETE | `/users/:id` | 软删除 |

分页参数 `page` 默认 1，`page_size` 默认 20，上限 100；排序 `sort` 支持 `newest`、`oldest`、`age`。

创建时 `active` 未传 / 为 null 则默认 true。PATCH 只更新非 nil 的字段，因此可以更新 0 和 false；本例把 null 视为未更新，且不支持清空列。未知 JSON 字段由默认绑定规则忽略，但不会进入更新白名单。这个项目没有登录和权限模块，服务仅绑定本机地址。

### 19.2 完整 main.go

在第 2 章建立的项目目录中创建 `main.go`，复制整个代码块：

```go
package main

import (
    "context"
    "errors"
    "fmt"
    "log"
    "net/http"
    "os"
    "os/signal"
    "strconv"
    "strings"
    "syscall"
    "time"

    "github.com/gin-gonic/gin"
    "gorm.io/driver/postgres"
    "gorm.io/gorm"
    "gorm.io/gorm/logger"
)

type User struct {
    ID        uint64         `gorm:"primaryKey"`
    Name      string         `gorm:"size:100;not null"`
    Email     string         `gorm:"size:255;not null;uniqueIndex"`
    Age       int            `gorm:"not null;check:users_age_nonnegative,age >= 0"`
    Active    bool           `gorm:"not null"`
    CreatedAt time.Time
    UpdatedAt time.Time
    DeletedAt gorm.DeletedAt `gorm:"index"`
}

type CreateUserRequest struct {
    Name   string `json:"name" binding:"required,max=100"`
    Email  string `json:"email" binding:"required,email,max=255"`
    Age    int    `json:"age" binding:"gte=0,lte=150"`
    Active *bool  `json:"active"`
}

type UpdateUserRequest struct {
    Name   *string `json:"name" binding:"omitempty,min=1,max=100"`
    Age    *int    `json:"age" binding:"omitempty,gte=0,lte=150"`
    Active *bool   `json:"active"`
}

type UserResponse struct {
    ID        uint64    `json:"id"`
    Name      string    `json:"name"`
    Email     string    `json:"email"`
    Age       int       `json:"age"`
    Active    bool      `json:"active"`
    CreatedAt time.Time `json:"created_at"`
    UpdatedAt time.Time `json:"updated_at"`
}

func toResponse(u User) UserResponse {
    return UserResponse{
        ID: u.ID, Name: u.Name, Email: u.Email, Age: u.Age, Active: u.Active,
        CreatedAt: u.CreatedAt, UpdatedAt: u.UpdatedAt,
    }
}

type App struct {
    DB *gorm.DB
}

func openDB(dsn string) (*gorm.DB, error) {
    db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{
        TranslateError: true,
        Logger: logger.New(log.New(os.Stdout, "gorm ", log.LstdFlags), logger.Config{
            SlowThreshold:        200 * time.Millisecond,
            LogLevel:             logger.Warn,
            ParameterizedQueries: true,
            Colorful:             false,
        }),
    })
    if err != nil { return nil, err }
    sqlDB, err := db.DB()
    if err != nil { return nil, err }
    sqlDB.SetMaxOpenConns(20)
    sqlDB.SetMaxIdleConns(5)
    sqlDB.SetConnMaxLifetime(30 * time.Minute)
    sqlDB.SetConnMaxIdleTime(5 * time.Minute)

    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    if err := sqlDB.PingContext(ctx); err != nil {
        _ = sqlDB.Close()
        return nil, err
    }
    return db, nil
}

func requestContext(c *gin.Context) (context.Context, context.CancelFunc) {
    return context.WithTimeout(c.Request.Context(), 3*time.Second)
}

func parseID(c *gin.Context) (uint64, bool) {
    // PostgreSQL bigint 是有符号 64 位，限制正数范围。
    id, err := strconv.ParseUint(c.Param("id"), 10, 63)
    if err != nil || id == 0 {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
        return 0, false
    }
    return id, true
}

func queryInt(c *gin.Context, key string, fallback, max int) (int, error) {
    raw, exists := c.GetQuery(key)
    if !exists { return fallback, nil }
    value, err := strconv.Atoi(raw)
    if err != nil || value < 1 || value > max {
        return 0, fmt.Errorf("%s must be between 1 and %d", key, max)
    }
    return value, nil
}

func writeDBError(c *gin.Context, err error) {
    switch {
    case errors.Is(err, gorm.ErrRecordNotFound):
        c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
    case errors.Is(err, gorm.ErrDuplicatedKey):
        c.JSON(http.StatusConflict, gin.H{"error": "email already exists"})
    case errors.Is(err, context.DeadlineExceeded):
        c.JSON(http.StatusGatewayTimeout, gin.H{"error": "database timeout"})
    case errors.Is(err, context.Canceled):
        c.JSON(http.StatusRequestTimeout, gin.H{"error": "request canceled"})
    default:
        log.Printf("database operation failed: %v", err)
        c.JSON(http.StatusInternalServerError, gin.H{"error": "internal server error"})
    }
}

func (a *App) Health(c *gin.Context) {
    ctx, cancel := requestContext(c)
    defer cancel()
    sqlDB, err := a.DB.DB()
    if err == nil { err = sqlDB.PingContext(ctx) }
    if err != nil {
        log.Printf("health check failed: %v", err)
        c.JSON(http.StatusServiceUnavailable, gin.H{"status": "unavailable"})
        return
    }
    c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

func (a *App) CreateUser(c *gin.Context) {
    var req CreateUserRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
        return
    }
    req.Name = strings.TrimSpace(req.Name)
    if req.Name == "" {
        c.JSON(http.StatusBadRequest, gin.H{"error": "name cannot be blank"})
        return
    }
    active := true
    if req.Active != nil { active = *req.Active }
    user := User{
        Name: req.Name, Email: strings.ToLower(strings.TrimSpace(req.Email)),
        Age: req.Age, Active: active,
    }
    ctx, cancel := requestContext(c)
    defer cancel()
    if err := a.DB.WithContext(ctx).Create(&user).Error; err != nil {
        writeDBError(c, err)
        return
    }
    c.JSON(http.StatusCreated, toResponse(user))
}

func (a *App) GetUser(c *gin.Context) {
    id, ok := parseID(c)
    if !ok { return }
    ctx, cancel := requestContext(c)
    defer cancel()
    var user User
    if err := a.DB.WithContext(ctx).First(&user, id).Error; err != nil {
        writeDBError(c, err)
        return
    }
    c.JSON(http.StatusOK, toResponse(user))
}

func (a *App) ListUsers(c *gin.Context) {
    page, err := queryInt(c, "page", 1, 100000)
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }
    size, err := queryInt(c, "page_size", 20, 100)
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }
    var active *bool
    if raw, exists := c.GetQuery("active"); exists {
        value, err := strconv.ParseBool(raw)
        if err != nil {
            c.JSON(http.StatusBadRequest, gin.H{"error": "invalid active parameter"})
            return
        }
        active = &value
    }
    orders := map[string]string{
        "newest": "id DESC", "oldest": "id ASC", "age": "age DESC, id DESC",
    }
    order, ok := orders[c.DefaultQuery("sort", "newest")]
    if !ok {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid sort parameter"})
        return
    }
    name := strings.TrimSpace(c.Query("name"))
    ctx, cancel := requestContext(c)
    defer cancel()
    build := func() *gorm.DB {
        q := a.DB.WithContext(ctx).Model(&User{})
        if name != "" { q = q.Where("name ILIKE ?", "%"+name+"%") }
        if active != nil { q = q.Where("active = ?", *active) }
        return q
    }
    var total int64
    if err := build().Count(&total).Error; err != nil {
        writeDBError(c, err)
        return
    }
    users := make([]User, 0)
    if err := build().Order(order).Offset((page-1)*size).Limit(size).
        Find(&users).Error; err != nil {
        writeDBError(c, err)
        return
    }
    items := make([]UserResponse, 0, len(users))
    for _, user := range users { items = append(items, toResponse(user)) }
    c.JSON(http.StatusOK, gin.H{
        "items": items, "total": total, "page": page, "page_size": size,
    })
}

func (a *App) UpdateUser(c *gin.Context) {
    id, ok := parseID(c)
    if !ok { return }
    var req UpdateUserRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
        return
    }
    updates := map[string]any{}
    if req.Name != nil {
        name := strings.TrimSpace(*req.Name)
        if name == "" {
            c.JSON(http.StatusBadRequest, gin.H{"error": "name cannot be blank"})
            return
        }
        updates["name"] = name
    }
    if req.Age != nil { updates["age"] = *req.Age }
    if req.Active != nil { updates["active"] = *req.Active }
    if len(updates) == 0 {
        c.JSON(http.StatusBadRequest, gin.H{"error": "no fields to update"})
        return
    }
    ctx, cancel := requestContext(c)
    defer cancel()
    var user User
    err := a.DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
        result := tx.Model(&User{}).Where("id = ?", id).Updates(updates)
        if result.Error != nil { return result.Error }
        if result.RowsAffected == 0 { return gorm.ErrRecordNotFound }
        return tx.First(&user, id).Error
    })
    if err != nil {
        writeDBError(c, err)
        return
    }
    c.JSON(http.StatusOK, toResponse(user))
}

func (a *App) DeleteUser(c *gin.Context) {
    id, ok := parseID(c)
    if !ok { return }
    ctx, cancel := requestContext(c)
    defer cancel()
    result := a.DB.WithContext(ctx).Where("id = ?", id).Delete(&User{})
    if result.Error != nil {
        writeDBError(c, result.Error)
        return
    }
    if result.RowsAffected == 0 {
        writeDBError(c, gorm.ErrRecordNotFound)
        return
    }
    c.Status(http.StatusNoContent)
}

func run() error {
    dsn := os.Getenv("DATABASE_DSN")
    if dsn == "" { return fmt.Errorf("DATABASE_DSN is required") }
    db, err := openDB(dsn)
    if err != nil { return fmt.Errorf("initialize database: %w", err) }
    sqlDB, err := db.DB()
    if err != nil { return err }
    defer sqlDB.Close()

    // 仅本地学习时启用。生产由发布流程运行版本化迁移。
    if os.Getenv("AUTO_MIGRATE") == "1" {
        ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
        err := db.WithContext(ctx).AutoMigrate(&User{})
        cancel()
        if err != nil { return fmt.Errorf("migrate database: %w", err) }
    }

    app := &App{DB: db}
    router := gin.Default()
    router.GET("/health", app.Health)
    router.POST("/users", app.CreateUser)
    router.GET("/users", app.ListUsers)
    router.GET("/users/:id", app.GetUser)
    router.PATCH("/users/:id", app.UpdateUser)
    router.DELETE("/users/:id", app.DeleteUser)

    server := &http.Server{
        Addr: "127.0.0.1:8080", Handler: router,
        ReadHeaderTimeout: 5 * time.Second,
        ReadTimeout: 10 * time.Second, WriteTimeout: 10 * time.Second,
        IdleTimeout: 60 * time.Second,
    }
    stopCtx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
    defer stop()
    serverErrors := make(chan error, 1)
    go func() { serverErrors <- server.ListenAndServe() }()
    log.Print("listening on http://127.0.0.1:8080")

    select {
    case err := <-serverErrors:
        if errors.Is(err, http.ErrServerClosed) { return nil }
        return err
    case <-stopCtx.Done():
        shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
        defer cancel()
        if err := server.Shutdown(shutdownCtx); err != nil {
            _ = server.Close()
            return err
        }
        return nil
    }
}

func main() {
    if err := run(); err != nil { log.Fatal(err) }
}
```

### 19.3 运行

Bash / WSL：

```bash
export DATABASE_DSN='host=127.0.0.1 user=app password=app123 dbname=gorm_demo port=5432 sslmode=disable TimeZone=Asia/Shanghai'
export AUTO_MIGRATE=1
go mod tidy
go fmt ./...
go run .
```

PowerShell：

```powershell
$env:DATABASE_DSN = 'host=127.0.0.1 user=app password=app123 dbname=gorm_demo port=5432 sslmode=disable TimeZone=Asia/Shanghai'
$env:AUTO_MIGRATE = '1'
go mod tidy
go fmt ./...
go run .
```

第一次启动会创建 users 表。没有开启迁移且表不存在时，`/health` 仍可能成功，因为它只验证数据库连接；CRUD 会报内部错误。这个区别帮助你区分“连接健康”和“schema 就绪”。

### 19.4 请求测试

以下多行命令适用于 Bash / WSL。PowerShell 建议用 `Invoke-RestMethod`，避免 Windows 不同版本下 curl 别名与 JSON 引号的差异。

```bash
curl http://127.0.0.1:8080/health

curl -X POST http://127.0.0.1:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"张三","email":"zhang@example.com","age":20,"active":true}'

curl 'http://127.0.0.1:8080/users?page=1&page_size=20&sort=newest'

# 将 1 替换为创建响应里的真实 id。
curl http://127.0.0.1:8080/users/1

curl -X PATCH http://127.0.0.1:8080/users/1 \
  -H 'Content-Type: application/json' \
  -d '{"age":0,"active":false}'

curl -i -X DELETE http://127.0.0.1:8080/users/1
```

PowerShell 创建与修改：

```powershell
$body = @{ name='张三'; email='zhang@example.com'; age=20; active=$true } | ConvertTo-Json
$user = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/users' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$patch = @{ age=0; active=$false } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "http://127.0.0.1:8080/users/$($user.id)" -ContentType 'application/json' -Body $patch
```

### 19.5 应该观察到什么

| 场景 | 预期 |
| --- | --- |
| 创建合法用户 | 201，返回主键及时间字段 |
| 再次使用同一 email 创建 | 409 |
| GET 不存在或已删除 id | 404 |
| 非数字 / 0 id | 400 |
| PATCH `age:0`、`active:false` | 200，正确写入零值 |
| PATCH `{}` | 400 |
| page_size=1000 | 400 |
| 未命中列表 | 200，`items: []` |
| 成功删除 | 204，无响应体 |
| 删除后再次创建同一 email | 409，唯一索引包含软删除记录 |
| 数据库不可用 | health 返回 503；业务错误按分类返回 |

Count 与列表是独立查询，当前示例不保证两者在并发修改下使用同一快照。并发 PATCH 采用字段更新，没有乐观版本校验，同一字段仍可能最后一次写入覆盖前一次；有编辑冲突检测需求时结合第 15 章。

参考：[Gin Binding](https://gin-gonic.com/en/docs/binding/)、[GORM Context](https://gorm.io/docs/context.html)、[Error Handling](https://gorm.io/docs/error_handling.html)。

<a id="ch20"></a>
## 20. Repository、Service 与项目组织

### 20.1 与 Java 分层的对应

| Java 项目 | Go 项目中的职责 |
| --- | --- |
| Controller | Gin Handler：HTTP 参数与响应 |
| Service | 业务规则、事务边界、跨数据访问协调 |
| Mapper / DAO | Repository：持久化查询与写入 |
| Entity / PO | 数据库模型 |
| DTO / VO | 请求与响应结构体 |
| 配置与 DataSource | 配置加载与数据库初始化 |

GORM 不强制分层，也不自动生成 MyBatis 式 Mapper 代理。项目较小时可以直接在 Handler 调用 GORM；逻辑复杂后，再按职责拆分。

推荐文件布局示例：

```text
cmd/server/main.go
internal/config/config.go
internal/database/postgres.go
internal/model/user.go
internal/repository/user.go
internal/service/user.go
internal/handler/user.go
internal/dto/user.go
migrations/000001_create_users.up.sql
migrations/000001_create_users.down.sql
go.mod
go.sum
```

### 20.2 Repository 接口

```go
type UserRepository interface {
    GetByID(ctx context.Context, id uint64) (*User, error)
    Create(ctx context.Context, user *User) error
}

type GormUserRepository struct {
    db *gorm.DB
}

func NewUserRepository(db *gorm.DB) *GormUserRepository {
    return &GormUserRepository{db: db}
}

func (r *GormUserRepository) GetByID(ctx context.Context, id uint64) (*User, error) {
    var user User
    if err := r.db.WithContext(ctx).First(&user, id).Error; err != nil {
        return nil, err
    }
    return &user, nil
}

func (r *GormUserRepository) Create(ctx context.Context, user *User) error {
    return r.db.WithContext(ctx).Create(user).Error
}
```

接口应该表达业务实际需要的能力。不要为每个 GORM 方法创建一个完全相同的转发方法，也不要默认给所有模型建立几十个泛型 CRUD 操作。

### 20.3 Service 事务必须传到 Repository

```go
err := db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
    txRepo := NewUserRepository(tx)
    if err := txRepo.Create(ctx, &user); err != nil { return err }
    // 其他 Repository 也使用同一个 tx 构造。
    return nil
})
```

如果 Service 开了事务，但 Repository 内部始终用根 `db`，事务就没有覆盖那些写入。也不要把共享 Repository 的 `r.db` 临时改成 tx，处理完再改回去；并发请求会互相干扰。

模型、请求 DTO 和响应 DTO 分开，可以避免数据库字段演进意外改变 HTTP 契约，也方便隐藏内部字段。

<a id="ch21"></a>
## 21. 泛型 API：与传统 API 的区别

GORM 从 v1.30.0 起提供 `gorm.G[T]`。新的查询可以用泛型获得明确的结果类型和直接错误返回；传统 API 仍可使用。不要把两个 API 的方法签名混在一起。

### 21.1 写法对照

```go
// 传统 API。
var user User
err := db.WithContext(ctx).Where("id = ?", 1).First(&user).Error

// 泛型 API：直接返回 User 和 error。
user, err := gorm.G[User](db).Where("id = ?", 1).First(ctx)
```

上面的两段应分别运行，不能在同一作用域原样重复声明。

| 操作 | 传统 API | 泛型 API |
| --- | --- | --- |
| 单条查询 | 写入 `&user`，读取 `.Error` | `First(ctx)` 返回对象与 error |
| 列表查询 | 写入 `&users` | `Find(ctx)` 返回切片与 error |
| Context | `WithContext(ctx)` | 执行方法显式接收 ctx |
| 更新 / 删除结果 | `.RowsAffected` 和 `.Error` | 常用方法返回 rows 和 error |
| 模型类型 | `Model(&User{})` 等 | `gorm.G[User](db)` |
| Save | 传统 API 有 | 泛型 API 不提供 |
| FirstOrCreate | 传统 API 有 | 泛型 API 不提供 |

### 21.2 泛型 CRUD 局部示例

```go
func GenericCRUD(ctx context.Context, db *gorm.DB) error {
    user := User{Name: "泛型用户", Email: "generic@example.com", Age: 23, Active: true}
    if err := gorm.G[User](db).Create(ctx, &user); err != nil {
        return err
    }

    found, err := gorm.G[User](db).Where("id = ?", user.ID).First(ctx)
    if err != nil { return err }

    users, err := gorm.G[User](db).Where("active = ?", true).Find(ctx)
    if err != nil { return err }
    _ = users

    // 单字段更新可以写入 false。
    rows, err := gorm.G[User](db).Where("id = ?", found.ID).
        Update(ctx, "active", false)
    if err != nil { return err }
    if rows != 1 { return gorm.ErrRecordNotFound }

    // struct Updates 仍有非零字段语义，显式 Select 可以选择零值字段。
    rows, err = gorm.G[User](db).Where("id = ?", found.ID).
        Select("Age").Updates(ctx, User{Age: 0})
    if err != nil { return err }
    if rows != 1 { return gorm.ErrRecordNotFound }

    rows, err = gorm.G[User](db).Where("id = ?", found.ID).Delete(ctx)
    if err != nil { return err }
    if rows != 1 { return gorm.ErrRecordNotFound }
    return nil
}
```

这个函数是行为演示，重复运行会因软删除记录的 email 唯一约束产生冲突。它也没有把整个流程包装成一个事务。

### 21.3 泛型 Preload 与事务

沿用第 11 章带 Orders 的替代 User 模型：

```go
users, err := gorm.G[User](db).Preload("Orders", func(p gorm.PreloadBuilder) error {
    p.Where("status = ?", "paid").Order("id DESC")
    return nil
}).Find(ctx)
```

关联构建器签名与传统 `Preload` 回调不同。事务仍由 `Transaction` 包住，在回调中使用 `gorm.G[User](tx)`：

```go
err := db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
    return gorm.G[User](tx).Create(ctx, &user)
})
```

泛型明确了结果模型，不意味着字符串列名和原生 SQL 自动获得完整编译期验证。代码生成与 `gorm.G[T]` 也是不同工具；不要把 MyBatis Generator、GORM Gen 和泛型 API 混为一个机制。

参考：[GORM 泛型指南](https://gorm.io/docs/the_generics_way.html)、[API 签名](https://pkg.go.dev/gorm.io/gorm)。

<a id="ch22"></a>
## 22. 从 MyBatis 迁移时的常见误区

| 习惯 / 现象 | 原因 | 推荐处理 |
| --- | --- | --- |
| 把 `*gorm.DB` 当作专属单连接 | 根句柄一般使用连接池 | 启动初始化并共享根句柄 |
| 每个 HTTP 请求都 `gorm.Open` | 反复创建池与连接开销 | 应用级初始化 |
| 每个请求结束 `sqlDB.Close()` | 关闭了所有请求共用的池 | 应用退出时关闭 |
| `Updates(User{Active:false})` 不生效 | struct 更新忽略零值 | map / Select / 单列 Update |
| `Where(User{Age:0})` 查出很多行 | struct 条件忽略零值 | 显式 SQL 或 map 条件 |
| `First` 查询多条不报错 | 自动取最多一条 | 唯一约束保证单条语义 |
| 空列表被当成数据库错误 | Find 为空通常成功 | API 返回空列表 |
| Save 覆盖其他列 | 它保存所有字段 | PATCH 使用白名单 Updates |
| Save 修改不存在 id 却插入 | 更新没有命中可能回退创建 | 显式 Update + RowsAffected |
| 删除后数据库仍有记录 | 模型开启软删除 | 判断是业务需求还是需 Unscoped |
| 删除后相同邮箱仍冲突 | 原唯一索引覆盖软删除行 | 保留占用或设计部分索引 |
| 为所有请求共享带 Where 的 base | 条件链可能被污染 | 每次构造 / 正确使用 Session |
| Service 开事务但部分写入未回滚 | Repository 用了根 db | 显式传入 tx |
| 以为默认写事务包住整个 Service | 默认边界不是任意业务流程 | 显式 Transaction |
| 以为读取关联字段会查库 | 没有代理式自动懒加载 | Preload 或显式关联查询 |
| 一对多 Join 分页缺订单 | LIMIT 限制了 Join 行 | 先分页用户，再预加载订单 |
| 预加载 paid 订单但用户没被筛掉 | Preload 条件只筛关联 | EXISTS / Join 筛主记录 |
| 认为 PrepareStmt 是二级缓存 | 它缓存语句，不缓存结果 | 另行设计结果缓存与失效 |
| 用 JSON 标签改数据库列名 | 两类标签职责不同 | 使用 gorm column 标签 |
| 用普通 Version 字段期待自动乐观锁 | 没有自动启用策略 | 手写版本条件 / 配置插件 |
| 把 HTTP map 直接传给 Updates | 容易出现越权字段修改 | DTO + 字段白名单 |
| 只在应用中先查重 | 并发时仍可能重复 | 数据库 UNIQUE + 冲突处理 |
| 把 AutoMigrate 当完整发布迁移 | 没有完整版本与回滚流程 | 独立版本化迁移 |

<a id="ch23"></a>
## 23. GORM 方法与数据库英文术语速查

### 23.1 方法名速查

下面以传统 API 名称为主。框架的方法名与数据库概念并不完全一对一。

| 方法 | 读法 / 含义 | SQL / 用途 | 注意点 |
| --- | --- | --- | --- |
| `Create` | 创建 | INSERT | 传指针获得回填 |
| `CreateInBatches` | 分批创建 | 多行 / 分批 INSERT | 明确批大小 |
| `First` | 第一条 | 主键升序 LIMIT 1 | 未找到会报错 |
| `Take` | 取一条 | LIMIT 1 | 默认不加主键排序 |
| `Last` | 最后一条 | 主键降序 LIMIT 1 | 未找到会报错 |
| `Find` | 查找列表 | SELECT | 无默认分页上限 |
| `Scan` | 映射结果 | SELECT 结果到 DTO | 对齐列名和别名 |
| `Pluck` | 提取一列 | 单列 SELECT | 目标一般是切片 |
| `Where` | 条件 | WHERE | 值使用参数绑定 |
| `Or` | 或 | OR | 注意括号 |
| `Not` | 非 | NOT | 明确 NULL 语义 |
| `Select` | 选择字段 | SELECT 列 / 写入字段 | 两种上下文含义 |
| `Omit` | 省略字段 | 排除写入等字段 | 不替代权限设计 |
| `Order` | 排序 | ORDER BY | 外部输入白名单 |
| `Limit` | 限制条数 | LIMIT | 列表必须合理限量 |
| `Offset` | 偏移 | OFFSET | 大偏移可能慢 |
| `Count` | 计数 | COUNT | 总数用 int64 |
| `Distinct` | 去重 | DISTINCT | Join 计数要审查 |
| `Group` | 分组 | GROUP BY | 非分组列须合法 |
| `Having` | 分组过滤 | HAVING | 过滤聚合结果 |
| `Model` | 模型上下文 | 指定模型信息 | 主键非零可加条件 |
| `Table` | 表上下文 | 表名 / 表表达式 | 不等价于完整模型配置 |
| `Update` | 单列更新 | UPDATE | 可写零值 |
| `Updates` | 多列更新 | UPDATE | struct 默认忽略零值 |
| `UpdateColumn(s)` | 列更新 | UPDATE | 跳过更新 Hooks / 时间维护 |
| `Save` | 保存全字段 | 更新或创建 | 普通 PATCH 谨慎使用 |
| `Delete` | 删除 | DELETE / 软删除 UPDATE | 取决于模型 |
| `Unscoped` | 去掉默认软删范围 | 包含已删除 / 永久删 | 权限与数据风险 |
| `Preload` | 预加载 | 额外关联查询 | 条件筛关联，不筛主对象 |
| `Joins` | 连接 | JOIN | 区分关联 Join 与手写 Join |
| `Association` | 关联模式 | 操作关系 | 删除关系不一定删除对象 |
| `Raw` | 原生查询 SQL | 手写 SELECT 等 | 通常接 Scan |
| `Exec` | 执行原生 SQL | 手写写操作 | 不自动补模型生命周期 |
| `Scopes` | 可复用范围 | Go 查询函数组合 | 别把权限仅放在可选 Scope |
| `Clauses` | SQL 子句配置 | ON CONFLICT / 锁等 | 理解数据库方言 |
| `Transaction` | 事务回调 | BEGIN / COMMIT / ROLLBACK | 回调里使用 tx |
| `Begin` / `Commit` / `Rollback` | 开始 / 提交 / 回滚 | 手动事务 | 检查每一步错误 |
| `SavePoint` / `RollbackTo` | 保存点 / 回滚到 | SAVEPOINT | 不等同独立事务 |
| `WithContext` | 携带上下文 | 超时 / 取消 | 不是开启事务 |
| `Session` | 新会话配置 | 查询状态 / 配置管理 | 正确使用复用边界 |
| `Debug` | 调试日志 | 查看 SQL | 考虑敏感参数 |
| `AutoMigrate` | 自动迁移 | schema 演进 | 不自动删除废弃列 |
| `Migrator` | 迁移操作接口 | 表 / 列 / 索引操作 | 不自动提供版本历史 |

### 23.2 英文名词速查

| 英文 | 中文 | 常见场景 |
| --- | --- | --- |
| ORM | 对象关系映射 | struct ↔ table |
| CRUD | 增删改查 | Create、Read、Update、Delete |
| Query | 查询 | SQL 查询与查询构建 |
| Insert | 插入 | 新增记录 |
| Update | 更新 | 修改已有记录 |
| Delete | 删除 | 删除已有记录 |
| Upsert | 插入或更新 | 冲突时更新 |
| Migration / Migrate | 迁移 / 执行迁移 | 数据库结构演进 |
| Schema | 数据库结构；也可指 PG 命名空间 | 表定义 / public schema |
| DDL | 数据定义语言 | CREATE / ALTER / DROP |
| DML | 数据操作语言 | INSERT / UPDATE / DELETE 等 |
| Primary Key | 主键 | 唯一标识记录 |
| Foreign Key | 外键 | 关联与引用完整性 |
| Constraint | 约束 | NOT NULL / UNIQUE / CHECK |
| Index | 索引 | 加速访问或保证唯一性 |
| Composite Index | 复合索引 | 多列索引 |
| Partial Index | 部分索引 | 仅对符合条件的行建索引 |
| Transaction | 事务 | 多步骤原子操作 |
| Commit | 提交 | 生效事务内修改 |
| Rollback | 回滚 | 撤销事务内修改 |
| Savepoint | 保存点 | 事务局部回滚 |
| Isolation Level | 隔离级别 | 并发事务可见性 |
| Atomicity | 原子性 | 全部成功或全部失败 |
| Consistency | 一致性 | 满足约束与业务规则 |
| Isolation | 隔离性 | 并发事务之间的影响 |
| Durability | 持久性 | 已提交数据的持久保存 |
| ACID | 四项事务性质 | 事务设计 |
| Connection Pool | 连接池 | 复用连接、控制资源 |
| Prepared Statement | 预处理语句 | 参数化语句执行与复用 |
| Parameter Binding | 参数绑定 | 把输入作为 SQL 值 |
| Result Mapping | 结果映射 | SQL 行转对象 |
| Association | 关联 | 模型之间的关系 |
| Eager Loading | 预先加载 | Preload |
| Lazy Loading | 延迟加载 | 访问时再查；GORM 不自动代理实现 |
| Join | 连接 | 多表查询 |
| Subquery | 子查询 | 查询嵌套 |
| Aggregate | 聚合 | COUNT / SUM / AVG |
| Pagination | 分页 | LIMIT / OFFSET / 游标 |
| Cursor | 游标 / 翻页位置 | 根据最后一条排序值继续 |
| Soft Delete | 软删除 / 逻辑删除 | 标记 deleted_at |
| Hard Delete | 物理删除 | 真正移除行 |
| Hook | 钩子 | 创建 / 更新等生命周期 |
| Scope | 查询范围 | 可复用条件组合 |
| Batch | 批量 / 批次 | 多行插入、分批处理 |
| Rows Affected | 受影响行数 | 判断更新 / 删除是否命中 |
| Optimistic Lock | 乐观锁 | version 条件更新 |
| Pessimistic Lock | 悲观锁 | FOR UPDATE |
| Deadlock | 死锁 | 多事务互相等待资源 |
| Idempotency | 幂等性 | 重试不产生额外效果 |
| N+1 Query | N+1 查询 | 主查询后逐条查关联 |

<a id="ch24"></a>
## 24. 练习、验证方式与排错

### 24.1 建议练习顺序

1. 跑通第 19 章，创建两个用户，查看 PostgreSQL 中的主键与时间。
2. 用 PATCH 将 `active` 改为 false、`age` 改为 0，确认 API 与数据库一致。
3. 删除用户，对比普通查询、`Unscoped` 查询和原生 SQL。
4. 分别用 struct、map 和 Select 更新零值，解释 SQL 的区别。
5. 为用户添加订单，比较循环查询与 Preload 的 SQL 数量。
6. 故意让转账第二步失败，确认第一步被回滚。
7. 给 Product 增加版本条件，模拟两个编辑者同时提交。
8. 写一个版本化迁移添加 status 列，替换本地 AutoMigrate 流程。

### 24.2 编译与运行验证

保存 main.go 并安装依赖后，在自己的项目执行：

```bash
go mod tidy
go fmt ./...
go vet ./...
go build ./...
```

随后用第 19 章请求表验证运行行为。本文编写环境没有 Go 工具链和 Docker，因此没有实际编译或连接 PostgreSQL 运行；交付前检查了 Markdown 结构、完整示例的语法结构与官方 API 签名。上面的构建和运行命令是读者执行的验证步骤，不代表已经在本文环境通过。

有业务 Repository / Service 后，优先对数据库真实语义做集成测试，而不是只断言框架调用顺序。测试可以使用隔离的 PostgreSQL 测试库，避免 SQLite 与 PostgreSQL 在 JSONB、锁、约束和方言上的差异掩盖问题。

| 应验证的行为 | 为什么 |
| --- | --- |
| 重复 email 被唯一约束拒绝 | 并发一致性依赖数据库 |
| 零值更新成功 | 防止 struct 语义误用 |
| 事务第二步失败后第一步撤销 | 验证真实事务覆盖范围 |
| 软删除后普通查询不可见 | 验证默认查询范围 |
| Count 与筛选列表范围一致 | 防止分页总数错误 |
| 乐观锁只允许一个旧版本写入成功 | 验证并发冲突识别 |

### 24.3 常见错误定位

| 报错 / 现象 | 先检查 |
| --- | --- |
| connection refused | 容器状态、端口、host、是否同一网络 |
| password authentication failed | 实际账号密码；旧数据卷不会重读初始化密码 |
| database does not exist | DSN 的 dbname 与初始化数据库名 |
| relation "users" does not exist | 是否迁移、当前 schema/search_path、TableName |
| duplicate key value violates unique constraint | email 唯一约束、软删除记录、是否规范化 |
| record not found | ID 是否正确、是否软删、目标结构体是否带旧主键 |
| WHERE conditions required | 更新 / 删除没有有效条件 |
| unsupported data type | 模型字段是否支持映射、是否实现 Scanner/Valuer |
| invalid field / association not found | 关联字段名、外键标签、模型版本是否匹配 |
| 查询很慢但 CPU 不高 | 索引、锁等待、连接池等待、返回数据量 |
| current transaction is aborted | 前一 SQL 已失败，需回滚或回到保存点 |
| deadlock detected | 不同操作的加锁顺序、事务时长、重试策略 |

排错时先检查应用日志中的 SQL 和错误，再检查数据库状态。不要通过关闭唯一约束、全表更新保护或随意删除数据卷来掩盖具体错误。

<a id="ch25"></a>
## 25. 官方参考资料

本文用独立示例解释 API 和类比关系，以下文档用于核对框架行为。后续升级依赖时，应以项目锁定版本的 API 和对应官方说明为准。

### GORM

- [总览与安装](https://gorm.io/docs/)
- [模型声明](https://gorm.io/docs/models.html)
- [数据库连接](https://gorm.io/docs/connecting_to_the_database.html)
- [Create](https://gorm.io/docs/create.html)
- [Query](https://gorm.io/docs/query.html)
- [Advanced Query](https://gorm.io/docs/advanced_query.html)
- [Update](https://gorm.io/docs/update.html)
- [Delete](https://gorm.io/docs/delete.html)
- [SQL Builder](https://gorm.io/docs/sql_builder.html)
- [关联操作](https://gorm.io/docs/associations.html)
- [Belongs To](https://gorm.io/docs/belongs_to.html)
- [Has One](https://gorm.io/docs/has_one.html)
- [Has Many](https://gorm.io/docs/has_many.html)
- [Many To Many](https://gorm.io/docs/many_to_many.html)
- [Preload](https://gorm.io/docs/preload.html)
- [Transactions](https://gorm.io/docs/transactions.html)
- [Migration](https://gorm.io/docs/migration.html)
- [Context](https://gorm.io/docs/context.html)
- [Error Handling](https://gorm.io/docs/error_handling.html)
- [Hooks](https://gorm.io/docs/hooks.html)
- [Scopes](https://gorm.io/docs/scopes.html)
- [Method Chaining](https://gorm.io/docs/method_chaining.html)
- [自定义数据类型](https://gorm.io/docs/data_types.html)
- [Indexes](https://gorm.io/docs/indexes.html)
- [Logger](https://gorm.io/docs/logger.html)
- [Security](https://gorm.io/docs/security.html)
- [Performance](https://gorm.io/docs/performance.html)
- [泛型 API 指南](https://gorm.io/docs/the_generics_way.html)
- [Go API 文档](https://pkg.go.dev/gorm.io/gorm)

### MyBatis、Gin 与 PostgreSQL

- [MyBatis Mapper XML 与 Result Maps](https://mybatis.org/mybatis-3/sqlmap-xml.html)
- [MyBatis Dynamic SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)
- [MyBatis Configuration](https://mybatis.org/mybatis-3/configuration.html)
- [MyBatis-Spring Transactions](https://mybatis.org/spring/transactions.html)
- [Gin Binding](https://gin-gonic.com/en/docs/binding/)
- [PostgreSQL 官方 Docker 镜像](https://hub.docker.com/_/postgres)
- [PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html)
