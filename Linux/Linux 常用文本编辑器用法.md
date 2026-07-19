# Linux 常用文本编辑器用法

Linux 中编辑文本文件，最常见的是终端编辑器 `vim`（或 `vi`）和 `nano`。服务器通常没有图形界面，因此需要优先掌握终端编辑器的基本操作。

------

## 一、编辑器选择

| 编辑器 | 特点 | 适用场景 |
| --- | --- | --- |
| `vim` / `vi` | 功能强大，几乎所有 Linux 都能使用，但有模式概念 | 服务器配置、代码编辑、快速修改文件 |
| `nano` | 操作直观，底部显示快捷键 | 初学者、简单修改配置 |
| `emacs` | 功能完整，可扩展性强 | 长期开发、熟悉 Emacs 生态的用户 |
| `gedit` | GNOME 桌面环境下的图形化编辑器 | 本地 Linux 桌面环境 |

查看某个编辑器是否已经安装：

```bash
command -v vim
command -v nano
command -v emacs
```

安装常用编辑器（Debian / Ubuntu）：

```bash
sudo apt update
sudo apt install vim nano
```

安装常用编辑器（CentOS / RHEL）：

```bash
sudo dnf install vim-enhanced nano
```

------

## 二、vim / vi 基本用法

### 1. 打开、创建和退出文件

```bash
vim file.txt              # 打开文件，不存在时创建
vi file.txt               # 使用 vi 打开
vim +10 file.txt          # 打开后定位到第 10 行
vim +/keyword file.txt    # 打开后定位到 keyword
vim -R file.txt           # 只读方式打开
```

vim 有三种最常用的模式：

- **普通模式（Normal）**：移动光标、复制、删除、搜索和执行命令。
- **插入模式（Insert）**：输入文本。
- **命令行模式（Command-line）**：执行保存、退出、替换等命令。

按 `Esc` 可以从插入模式返回普通模式。刚打开文件时通常已经处于普通模式。

### 2. 进入插入模式

在普通模式下：

| 按键 | 作用 |
| --- | --- |
| `i` | 在光标前插入 |
| `a` | 在光标后插入 |
| `I` | 在当前行开头插入 |
| `A` | 在当前行末尾插入 |
| `o` | 在当前行下方新建一行并插入 |
| `O` | 在当前行上方新建一行并插入 |
| `Esc` | 返回普通模式 |

### 3. 保存和退出

先按 `Esc`，再输入以下命令并回车：

| 命令 | 作用 |
| --- | --- |
| `:w` | 保存 |
| `:q` | 退出 |
| `:wq` 或 `ZZ` | 保存并退出 |
| `:q!` | 放弃修改并强制退出 |
| `:w new.txt` | 另存为 `new.txt` |
| `:x` | 文件有修改时保存并退出 |

常见退出流程：

```text
Esc → :wq → Enter
```

如果提示 `E45: 'readonly' option is set`，说明文件以只读方式打开；确认有权限后可以使用：

```vim
:w!
```

### 4. 光标移动

普通模式下可以使用方向键，也可以使用：

| 按键 | 作用 |
| --- | --- |
| `h` / `j` / `k` / `l` | 左 / 下 / 上 / 右 |
| `0` | 移动到行首 |
| `^` | 移动到行首第一个非空字符 |
| `$` | 移动到行尾 |
| `gg` | 文件第一行 |
| `G` | 文件最后一行 |
| `nG` | 移动到第 n 行，例如 `20G` |
| `Ctrl + f` | 向下翻一页 |
| `Ctrl + b` | 向上翻一页 |
| `w` | 移动到下一个单词开头 |
| `b` | 移动到上一个单词开头 |

显示行号：

```vim
:set number
```

关闭行号：

```vim
:set nonumber
```

### 5. 删除、复制和粘贴

| 命令 | 作用 |
| --- | --- |
| `x` | 删除当前字符 |
| `dd` | 删除当前行 |
| `ndd` | 从当前行开始删除 n 行，例如 `3dd` |
| `D` | 删除光标到行尾的内容 |
| `dw` | 删除一个单词 |
| `yy` | 复制当前行 |
| `nyy` | 复制 n 行 |
| `p` | 在光标后粘贴 |
| `P` | 在光标前粘贴 |
| `u` | 撤销 |
| `Ctrl + r` | 重做 |
| `.` | 重复上一次修改命令 |

### 6. 搜索和替换

```vim
/keyword          "向下搜索 keyword
?keyword          "向上搜索 keyword
n                 "跳到下一个匹配项
N                 "跳到上一个匹配项
:noh              "取消搜索高亮
```

替换命令格式：

```vim
:s/old/new/             "替换当前行的第一个匹配项
:s/old/new/g            "替换当前行的所有匹配项
:%s/old/new/g           "替换全文所有匹配项
:%s/old/new/gc          "全文替换，每次替换前询问
:10,20s/old/new/g       "替换第 10 到 20 行
```

`/`、`&` 等特殊字符出现在搜索内容中时，需要使用反斜杠转义，或更换分隔符：

```vim
:%s#/old/path#/new/path#g
```

### 7. 多文件和分屏

```bash
vim file1.txt file2.txt
```

在 vim 命令行模式中：

```vim
:next                 "下一个文件
:previous             "上一个文件
:ls                   "查看已打开的文件
:e another.txt        "打开另一个文件
:sp file.txt          "水平分屏
:vs file.txt          "垂直分屏
```

分屏之间切换：

```text
Ctrl + w，然后按 h / j / k / l
```

关闭当前分屏：

```vim
:q
```

### 8. 编辑配置文件的常见流程

```bash
cp app.conf app.conf.bak
vim app.conf
```

修改后，在 vim 中执行 `:wq` 保存退出，再检查内容：

```bash
cat app.conf
```

如果文件属于其他用户，需要管理员权限：

```bash
sudo vim /etc/nginx/nginx.conf
```

修改服务配置后，建议先检查配置语法，再重启服务。例如：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

------

## 三、nano 基本用法

`nano` 不区分普通模式和插入模式，打开后可以直接输入文字。快捷键中的 `^` 表示 `Ctrl`，例如 `^O` 就是 `Ctrl + O`。

### 1. 打开文件

```bash
nano file.txt
nano +10,5 file.txt       # 定位到第 10 行、第 5 列
sudo nano /etc/hosts
```

### 2. 常用快捷键

| 快捷键 | 作用 |
| --- | --- |
| `Ctrl + O` | 写入文件（保存），然后回车确认文件名 |
| `Ctrl + X` | 退出 |
| `Ctrl + X`，再按 `Y` | 保存修改并退出 |
| `Ctrl + X`，再按 `N` | 放弃修改并退出 |
| `Ctrl + W` | 搜索 |
| `Alt + W` | 查找下一个 |
| `Ctrl + K` | 剪切当前行 |
| `Ctrl + U` | 粘贴剪切内容 |
| `Alt + 6` | 复制当前行 |
| `Ctrl + _` | 跳转到指定行和列 |
| `Ctrl + C` | 显示当前行、列信息 |
| `Ctrl + G` | 查看帮助 |
| `Ctrl + A` | 移动到行首 |
| `Ctrl + E` | 移动到行尾 |
| `Ctrl + W` | 搜索文本 |

保存并退出的常见流程：

```text
Ctrl + O → Enter → Ctrl + X
```

------

## 四、emacs 和图形化编辑器

### 1. emacs 常用操作

```bash
emacs file.txt
emacs -nw file.txt        # 在当前终端中运行，不打开图形窗口
```

常用快捷键：

| 快捷键 | 作用 |
| --- | --- |
| `Ctrl + X`，`Ctrl + S` | 保存 |
| `Ctrl + X`，`Ctrl + C` | 退出 |
| `Ctrl + G` | 取消当前命令 |
| `Ctrl + S` | 向前搜索 |
| `Ctrl + R` | 向后搜索 |
| `Ctrl + K` | 删除光标到行尾的内容 |
| `Ctrl + Y` | 粘贴 |
| `Ctrl + _` | 撤销 |
| `Alt + X` | 执行命令 |

### 2. gedit

在带 GNOME 桌面的 Linux 中，可以使用：

```bash
gedit file.txt
```

通过 SSH 远程登录时通常没有图形显示环境，此时应使用 `vim` 或 `nano`，不要依赖 `gedit` 等图形化工具。

------

## 五、实际使用建议

1. 只需要改一两行配置时，优先使用 `nano`，或者使用 `vim` 的搜索和替换功能。
2. 编辑 `/etc`、服务配置等重要文件前，先备份原文件。
3. 不要直接使用 `sudo` 执行不熟悉的替换命令；先检查搜索范围和替换结果。
4. 修改服务配置后先执行配置检查命令，再 reload 或 restart 服务。
5. 在 vim 中遇到“不知道怎么退出”时，按 `Esc`，输入 `:q!`，再按回车即可放弃修改退出。
6. 查看文件而不是编辑文件时，优先使用 `less`、`cat`、`head`、`tail`，不必进入编辑器：

```bash
less app.log
head -n 20 app.log
tail -f app.log
```

## 六、快速选择

```text
需要编辑服务器文件？      vim /etc/hosts
第一次接触终端编辑器？      nano file.txt
需要批量查找替换？          vim 的 :%s/old/new/g 或 sed
需要图形化编辑？            gedit file.txt
只想查看文件？              less file.txt
```
