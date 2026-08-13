# Python 基础 😎😎😎

## 学习目标

学完本章，你应该能够：

- 在 Windows、macOS 或 Linux 上确认 Python 解释器版本，并使用 `venv` 创建隔离环境。
- 理解 Python 的缩进、变量绑定、动态类型和基本输入输出。
- 正确使用数字、布尔值、字符串、`None`、运算符与类型转换。
- 使用分支、循环和推导式完成小型数据处理任务。
- 识别初学阶段最常见的语法、类型、精度和可变对象问题。

> 本系列以 **Python 3.10+** 为最低基线，语义按当前 Python 3.14 官方文档整理。示例优先采用 3.10 已支持的写法；使用更高版本特性时会单独说明。

## 目录

- [1. Python 与解释器](#1-python-与解释器)
- [2. 安装与环境管理](#2-安装与环境管理)
- [3. 第一个程序与语法规则](#3-第一个程序与语法规则)
- [4. 变量、对象与基本类型](#4-变量对象与基本类型)
- [5. 输入、输出与格式化](#5-输入输出与格式化)
- [6. 运算符](#6-运算符)
- [7. 条件分支](#7-条件分支)
- [8. 循环](#8-循环)
- [9. 推导式初识](#9-推导式初识)
- [10. 异常信息与调试入门](#10-异常信息与调试入门)
- [11. 常见坑](#11-常见坑)
- [12. 综合练习](#12-综合练习)
- [13. 本章检查清单](#13-本章检查清单)

------

## 1. Python 与解释器

Python 是一门动态类型、解释执行、支持多种编程范式的高级语言。日常所说的“运行 Python”，通常是让 **CPython 解释器**读取源码、编译为字节码，再由虚拟机执行。

### 1.1 源码、解释器与包管理器

| 名称 | 作用 | 常见命令 |
| --- | --- | --- |
| Python 源码 | 以 `.py` 结尾的文本文件 | `python app.py` |
| 解释器 | 执行源码或进入交互模式 | `python`、Windows 下的 `py` |
| 标准库 | 随 Python 一起安装的模块 | `import pathlib` |
| 第三方包 | 从 PyPI 等来源安装的扩展 | `python -m pip install httpx` |
| 虚拟环境 | 为项目隔离解释器与依赖 | `python -m venv .venv` |

交互模式适合快速验证表达式：

```python
>>> 2 ** 10
1024
>>> "Python".lower()
'python'
```

脚本模式适合保存和复用代码：

```python
# hello.py
name = "JingXu"
print(f"你好，{name}！")
```

### 1.2 查看版本与解释器路径

在终端执行：

```text
python --version
python -c "import sys; print(sys.executable); print(sys.version)"
```

Windows 安装了 Python Launcher 时，还可以执行：

```text
py -0p
py -3.14 --version
```

同一台机器可能有多个 Python。排查“明明安装了包却导入失败”时，首先确认运行脚本、创建环境和执行 `pip` 的解释器是不是同一个。

------

## 2. 安装与环境管理

### 2.1 安装建议

- Windows：从 [Python 官网](https://www.python.org/downloads/) 安装，或使用系统包管理器；安装后确认 `python`/`py` 可用。
- macOS：不要依赖系统自带 Python，建议通过官网安装包或包管理器安装独立版本。
- Linux：发行版通常自带 Python；不要随意删除或覆盖系统 Python，可为开发项目额外安装版本。
- IDE：PyCharm 与 VS Code 都可以，但 IDE 选择的解释器必须与终端环境一致。

### 2.2 使用 `venv` 隔离项目

每个项目使用独立虚拟环境，避免不同项目的依赖版本互相冲突。

```text
# 在项目根目录创建虚拟环境
python -m venv .venv

# PowerShell 激活
.\.venv\Scripts\Activate.ps1

# Windows cmd 激活
.venv\Scripts\activate.bat

# macOS / Linux 激活
source .venv/bin/activate

# 退出虚拟环境
deactivate
```

激活只是把虚拟环境的可执行目录临时放到 `PATH` 前面。即使不激活，也可以直接调用 `.venv` 中的 Python。

### 2.3 使用 `pip`

推荐始终通过目标解释器调用 `pip`：

```text
python -m pip install --upgrade pip
python -m pip install httpx
python -m pip show httpx
python -m pip list
python -m pip uninstall httpx
```

记录和复现依赖的基础方式：

```text
python -m pip freeze > requirements.txt
python -m pip install -r requirements.txt
```

`pip freeze` 会记录环境中的全部已安装包，适合小项目或环境快照；正式项目还应区分直接依赖与间接依赖，并明确 Python 版本。

### 2.4 最小项目结构

```text
demo/
├─ .venv/             # 不提交到 Git
├─ .gitignore
├─ requirements.txt
└─ main.py
```

`.gitignore` 至少排除：

```text
.venv/
__pycache__/
*.py[cod]
.env
```

------

## 3. 第一个程序与语法规则

### 3.1 缩进定义代码块

Python 不使用 `{}` 包围代码块，而使用缩进。社区约定每级 **4 个空格**，不要混用 Tab 和空格。

```python
temperature = 28

if temperature >= 30:
    print("天气较热")
else:
    print("温度适宜")
```

冒号 `:` 后通常开始新代码块。空代码块可临时使用 `pass`：

```python
def todo() -> None:
    pass
```

### 3.2 注释与文档字符串

```python
# 单行注释：解释“为什么”，不要机械复述代码
tax_rate = 0.06


def calculate_total(price: float) -> float:
    """计算含税价格。"""
    return price * (1 + tax_rate)
```

三引号字符串本质上仍是字符串。放在模块、类或函数的第一条语句时，才成为可由 `help()`、`.__doc__` 读取的文档字符串。

### 3.3 一行一条语句

分号虽可分隔语句，但不推荐：

```python
# 推荐
name = "Alice"
age = 20

# 不推荐
name = "Alice"; age = 20
```

长表达式优先放在圆括号、方括号或花括号内自然换行：

```python
total = (
    base_price
    + delivery_fee
    - discount
)
```

### 3.4 命名约定

| 对象 | 约定 | 示例 |
| --- | --- | --- |
| 变量、函数、模块 | `snake_case` | `user_name`、`load_data` |
| 类 | `PascalCase` | `OrderService` |
| 常量 | `UPPER_SNAKE_CASE` | `MAX_RETRIES` |
| 内部实现 | 单下划线前缀 | `_parse_token` |

标识符区分大小写；不要用 `list`、`str`、`id` 等内置名称作为普通变量名，以免遮蔽内置对象。

------

## 4. 变量、对象与基本类型

### 4.1 变量是名称绑定

Python 变量不保存“盒子里的值”，而是名称指向对象：

```python
count = 1          # count 指向整数对象 1
count = "one"     # 重新绑定到字符串对象，语法允许
```

Python 是动态类型语言：变量无需提前声明类型，但每个运行期对象都有明确类型。

```python
value = 42
print(type(value))        # <class 'int'>
print(isinstance(value, int))  # True
```

业务代码中，判断类型关系通常优先使用 `isinstance()`，因为它能正确处理继承关系。

### 4.2 多重赋值与解包

```python
x, y = 10, 20
x, y = y, x

first, *middle, last = [1, 2, 3, 4, 5]
print(first, middle, last)  # 1 [2, 3, 4] 5
```

左右元素数量必须匹配，除非一侧使用一个带 `*` 的收集变量。

### 4.3 数字类型

```python
integer = 42          # int：任意精度整数，受可用内存限制
decimal = 3.14        # float：通常为 IEEE 754 双精度浮点数
complex_number = 2 + 3j

binary = 0b1010       # 10
octal = 0o12          # 10
hexadecimal = 0xA     # 10
readable = 1_000_000  # 下划线仅提升可读性
```

常见转换：

```python
int("101", 2)       # 5
float("3.14")       # 3.14
str(2026)            # '2026'
round(3.14159, 2)    # 3.14
```

`bool` 是 `int` 的子类，`True == 1`，但业务含义上应把布尔值与数量分开处理。

### 4.4 布尔值与真值判断

以下对象在条件中为假：

- `False`、`None`。
- 数字零，如 `0`、`0.0`、`0j`。
- 空容器或空序列，如 `""`、`[]`、`()`、`{}`、`set()`。
- 自定义对象的 `__bool__()` 返回 `False`，或未定义 `__bool__()` 且 `__len__()` 返回 `0`。

其他对象通常为真：

```python
items = []
if not items:
    print("列表为空")
```

不要写冗余比较 `if items != []` 或 `if enabled == True`。

### 4.5 字符串初识

字符串是不可变的 Unicode 文本序列：

```python
single = 'Python'
double = "后端开发"
multiline = """第一行
第二行"""
raw_path = r"C:\new\test"  # 原始字符串减少反斜杠转义
```

索引从 `0` 开始，负数从末尾计数；切片左闭右开：

```python
language = "Python"
language[0]       # 'P'
language[-1]      # 'n'
language[1:4]     # 'yth'
language[::-1]    # 'nohtyP'
```

字符串的系统用法见下一章 [核心数据结构](./02-核心数据结构.md)。

### 4.6 `None`

`None` 表示“没有值”或“尚未设置”，其类型是 `NoneType`。判断时使用身份运算符：

```python
result = None

if result is None:
    print("暂无结果")
```

不要写 `result == None`。`is` 检查是否为同一个对象，正适合单例对象 `None`。

### 4.7 删除名称

```python
temporary = "data"
del temporary
```

`del` 删除名称绑定，不等于立刻销毁对象；对象何时回收由内存管理机制决定。

------

## 5. 输入、输出与格式化

### 5.1 `input()` 永远返回字符串

```python
name = input("请输入姓名：").strip()
age_text = input("请输入年龄：")
age = int(age_text)
print(name, age)
```

用户输入不可信，类型转换可能抛出 `ValueError`。后续异常章节会介绍稳健处理方式。

### 5.2 `print()` 常用参数

```python
print("A", "B", "C", sep=" | ")
print("加载中", end="...")
print("完成")
```

`print()` 默认以空格分隔参数并在末尾换行。调试复杂对象时，`repr()` 往往比 `str()` 更能暴露转义字符和边界：

```python
text = "line1\nline2"
print(text)
print(repr(text))
```

### 5.3 f-string

f-string 可直接嵌入表达式，是 Python 3.10+ 中首选的字符串格式化方式：

```python
name = "Alice"
score = 93.456

print(f"{name} 的成绩是 {score:.2f}")
print(f"通过：{score >= 60}")
print(f"调试信息：{score=}")
```

常用格式：

| 写法 | 含义 | 示例结果 |
| --- | --- | --- |
| `{value:.2f}` | 小数点后两位 | `3.14` |
| `{ratio:.1%}` | 百分比 | `85.0%` |
| `{number:,}` | 千位分隔 | `1,000,000` |
| `{value:>10}` | 右对齐，宽度 10 | 前方补空格 |
| `{value!r}` | 使用 `repr()` | 显示引号与转义 |

------

## 6. 运算符

### 6.1 算术运算符

```python
7 + 3    # 10
7 - 3    # 4
7 * 3    # 21
7 / 3    # 2.333...，真除法总返回浮点数
7 // 3   # 2，向负无穷取整
7 % 3    # 1
7 ** 3   # 343
```

注意负数地板除：

```python
-7 // 3  # -3，而不是 -2
-7 % 3   # 2，并满足 a == (a // b) * b + a % b
```

### 6.2 比较运算符与链式比较

```python
age >= 18
name != ""
1 < score <= 100
```

链式比较等价于用 `and` 连接且中间表达式只求值一次，适合表达区间。

### 6.3 逻辑运算符与短路

```python
is_valid = age >= 18 and has_id_card
can_enter = is_admin or has_ticket
is_empty = not items
```

`and` 和 `or` 返回参与运算的某个对象，不一定返回布尔值：

```python
display_name = nickname or username or "匿名用户"
```

短路求值意味着右侧可能不执行：

```python
if user is not None and user.is_active:
    print("有效用户")
```

### 6.4 身份与成员运算符

```python
value is None
value is not None

"py" in "python"
3 in [1, 2, 3]
"name" in {"name": "Alice"}  # 字典成员判断针对键
```

`==` 比较值是否相等，`is` 比较是否为同一个对象。除 `None` 等单例判断外，不要用 `is` 比较数字或字符串值。

### 6.5 位运算符

`&`、`|`、`^`、`~`、`<<`、`>>` 对整数二进制位操作，常用于权限掩码、协议字段等场景：

```python
READ = 0b001
WRITE = 0b010
permission = READ | WRITE

can_write = bool(permission & WRITE)
```

不要把位运算符 `&`、`|` 与逻辑运算符 `and`、`or` 混用。

### 6.6 优先级与括号

大致记忆：幂运算 → 正负号 → 乘除 → 加减 → 比较 → `not` → `and` → `or`。复杂表达式不要依赖记忆，使用括号表达意图：

```python
if is_member and (amount >= 100 or has_coupon):
    apply_discount()
```

------

## 7. 条件分支

### 7.1 `if` / `elif` / `else`

```python
score = 85

if score >= 90:
    level = "A"
elif score >= 80:
    level = "B"
elif score >= 60:
    level = "C"
else:
    level = "D"
```

分支从上到下判断，命中后不再检查后续条件。因此更具体或更严格的条件通常写在前面。

### 7.2 条件表达式

```python
status = "成年" if age >= 18 else "未成年"
```

条件表达式适合简单二选一。嵌套条件表达式会显著降低可读性，应改用普通分支。

### 7.3 `match` 结构化模式匹配

Python 3.10+ 可用 `match` / `case`。它不是简单的 `switch`，还可以按数据结构解构匹配：

```python
def handle_command(command: tuple[str, ...]) -> str:
    match command:
        case ("quit",):
            return "退出"
        case ("load", filename):
            return f"加载 {filename}"
        case ("move", x, y):
            return f"移动到 ({x}, {y})"
        case _:
            return "未知命令"
```

`case _` 是兜底分支。模式中的普通名称是“捕获变量”，不是拿已有变量做值比较；匹配常量通常使用字面量、枚举成员或限定名称。

------

## 8. 循环

### 8.1 `for` 遍历可迭代对象

Python 的 `for` 直接遍历元素，不要求手动维护下标：

```python
languages = ["Python", "Java", "Go"]

for language in languages:
    print(language)
```

需要序号时使用 `enumerate()`：

```python
for index, language in enumerate(languages, start=1):
    print(index, language)
```

并行遍历使用 `zip()`：

```python
names = ["Alice", "Bob"]
scores = [90, 85]

for name, score in zip(names, scores, strict=True):
    print(name, score)
```

`strict=True` 自 Python 3.10 起可用；长度不一致时抛出 `ValueError`，可避免静默截断。

### 8.2 `range()`

```python
for number in range(5):          # 0, 1, 2, 3, 4
    print(number)

for number in range(2, 10, 2):  # 2, 4, 6, 8
    print(number)
```

`range(start, stop, step)` 依然是左闭右开，且返回惰性的 `range` 对象，不会预先创建全部整数。

### 8.3 `while`

```python
remaining = 3

while remaining > 0:
    print(f"还剩 {remaining} 次")
    remaining -= 1
```

`while` 适合循环次数事先未知、由状态决定退出的场景。确保状态会变化，避免死循环。

### 8.4 `break`、`continue` 与循环 `else`

```python
numbers = [2, 4, 7, 8]

for number in numbers:
    if number % 2 == 0:
        continue
    print(f"找到奇数：{number}")
    break
else:
    print("没有找到奇数")
```

- `continue`：跳过本轮剩余语句，进入下一轮。
- `break`：立即结束当前最内层循环。
- 循环 `else`：循环正常耗尽时执行；被 `break` 提前终止时不执行。

循环 `else` 很适合表达“搜索未命中”，但团队不熟悉时也可改为辅助函数提前 `return`。

### 8.5 不要边遍历边随意修改容器

```python
# 推荐：构造新列表
active_users = [user for user in users if user.is_active]

# 或遍历副本
for key in list(config):
    if config[key] is None:
        del config[key]
```

直接修改正在遍历的列表会漏处理元素；改变正在遍历的字典大小会抛出 `RuntimeError`。

------

## 9. 推导式初识

推导式用于从一个可迭代对象映射、筛选并构造新容器：

```python
numbers = [1, 2, 3, 4, 5]

squares = [number ** 2 for number in numbers]
even_squares = [number ** 2 for number in numbers if number % 2 == 0]
```

等价的普通循环：

```python
even_squares = []
for number in numbers:
    if number % 2 == 0:
        even_squares.append(number ** 2)
```

字典与集合也有推导式：

```python
name_lengths = {name: len(name) for name in ["Alice", "Bob"]}
unique_lengths = {len(name) for name in ["Alice", "Bob", "David"]}
```

判断标准：一层映射加一层筛选通常清晰；包含复杂分支、副作用或多层嵌套时，普通循环更易维护。完整用法见 [核心数据结构](./02-核心数据结构.md)。

------

## 10. 异常信息与调试入门

遇到错误时，从 Traceback **最后一行**确认异常类型和消息，再向上定位自己代码中的文件与行号：

```text
Traceback (most recent call last):
  File "main.py", line 2, in <module>
    age = int("十八")
ValueError: invalid literal for int() with base 10: '十八'
```

常见异常：

| 异常 | 常见原因 |
| --- | --- |
| `SyntaxError` | 语法错误，如漏写冒号 |
| `IndentationError` | 缩进层级错误或混用 Tab/空格 |
| `NameError` | 名称未定义或拼写错误 |
| `TypeError` | 操作不支持当前类型或参数不匹配 |
| `ValueError` | 类型可接受，但具体值不合法 |
| `IndexError` | 序列下标越界 |
| `KeyError` | 字典中不存在指定键 |
| `AttributeError` | 对象没有指定属性或方法 |

调试顺序：

1. 制造最小可复现输入。
2. 阅读完整 Traceback，不只看“报错了”。
3. 使用 `print(repr(value), type(value))` 或 IDE 断点检查关键状态。
4. 验证边界：空输入、零、负数、最大值、重复值和非法格式。
5. 修复根因后删除临时调试输出，并补充测试或断言。

------

## 11. 常见坑

### 11.1 浮点数不能精确表示所有十进制小数

```python
print(0.1 + 0.2)  # 0.30000000000000004
```

浮点比较可使用容差；金额等十进制定点业务应使用 `decimal.Decimal`：

```python
import math
from decimal import Decimal

assert math.isclose(0.1 + 0.2, 0.3)
price = Decimal("0.1") + Decimal("0.2")
```

构造 `Decimal` 时优先传字符串，不要把已有浮点误差带进去。

### 11.2 `input()` 不是数字

```python
age = input("年龄：")
# age + 1 会触发 TypeError
age_number = int(age)
```

### 11.3 `is` 不是值比较

```python
name == "Alice"   # 正确：比较值
result is None    # 正确：判断 None
```

整数和字符串可能因实现优化而复用对象，使用 `is` 比较值会产生偶发且不可移植的结果。

### 11.4 可变对象赋值不会自动复制

```python
original = [1, 2]
alias = original
alias.append(3)
print(original)  # [1, 2, 3]
```

`alias` 和 `original` 指向同一个列表。浅拷贝、深拷贝和嵌套对象问题见下一章。

### 11.5 布尔运算的返回值可能不是 `bool`

```python
value = [] or "default"  # 'default'
```

若接口明确要求布尔值，使用 `bool(value)` 转换，不要依赖 `and` / `or` 的操作数返回规则。

### 11.6 遮蔽内置名称

```python
# 不推荐：之后无法正常调用 list(...)
list = [1, 2, 3]
```

如果已经在交互环境中遮蔽，可 `del list` 恢复对内置名称的查找；代码中应直接换成语义化变量名。

### 11.7 反斜杠与 Windows 路径

```python
bad = "C:\new\test"       # \n、\t 会被解释为换行和制表符
raw = r"C:\new\test"
```

实际文件操作更推荐后续章节介绍的 `pathlib.Path`，避免手写路径分隔符。

### 11.8 忽略返回的新对象

字符串不可变，方法通常返回新字符串：

```python
name = " alice "
name.strip()        # 返回值被丢弃
name = name.strip() # 正确接收
```

------

## 12. 综合练习

### 练习 1：成绩等级

读取 `0~100` 的整数成绩并输出等级：`90~100` 为 A、`80~89` 为 B、`60~79` 为 C，其余为 D。非法数字或超出范围时输出明确提示。

提示：先完成类型转换，再判断范围；异常处理会在后续章节系统介绍。

### 练习 2：FizzBuzz

输出 `1~100`：3 的倍数输出 `Fizz`，5 的倍数输出 `Buzz`，同时是二者倍数输出 `FizzBuzz`，否则输出原数。

关键点：更严格的“同时整除”条件应放在前面。

### 练习 3：质数判断

编写程序判断大于等于 2 的整数是否为质数。只需要检查到该数平方根，并尝试使用循环 `else` 表达“没有找到因数”。

### 练习 4：统计文本

给定一行文本，忽略两端空白后统计：字符总数、数字字符数、英文字母数和空格数。

可查阅：`str.isdigit()`、`str.isalpha()`、`str.isspace()`。

### 练习 5：购物折扣

输入订单金额与是否会员。会员满 100 元打 9 折，非会员满 200 元打 95 折，输出两位小数的最终金额。思考如何处理负数和无效的会员标记。

### 参考实现：FizzBuzz

```python
for number in range(1, 101):
    if number % 15 == 0:
        output = "FizzBuzz"
    elif number % 3 == 0:
        output = "Fizz"
    elif number % 5 == 0:
        output = "Buzz"
    else:
        output = str(number)

    print(output)
```

------

## 13. 本章检查清单

- [ ] 能确认终端与 IDE 使用的是哪个 Python 解释器。
- [ ] 能创建、激活、退出虚拟环境，并通过 `python -m pip` 管理包。
- [ ] 能解释动态类型、名称绑定、`==` 与 `is` 的区别。
- [ ] 能正确处理 `int`、`float`、`bool`、`str` 与 `None`。
- [ ] 能使用 f-string 输出常见数字格式。
- [ ] 能独立编写 `if`、`match`、`for`、`while` 和循环控制逻辑。
- [ ] 知道何时使用 `enumerate()`、`zip()` 和 `range()`。
- [ ] 能读懂常见 Traceback，并检查空值、边界值和非法输入。
- [ ] 能解释浮点误差、名称遮蔽和可变对象别名问题。

------

[← 返回 Python 学习路线](./README.md) · [下一章：核心数据结构 →](./02-核心数据结构.md)

