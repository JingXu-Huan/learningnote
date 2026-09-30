# 贡献指南

欢迎一起完善这份编程知识库。纠正错字、补充解释、修正知识错误、更新参考资料和新增学习笔记，都可以提交贡献。

[阅读站点](https://jingxu-huan.github.io/learningnote/) · [GitHub 仓库](https://github.com/JingXu-Huan/learningnote) · [反馈问题](https://github.com/JingXu-Huan/learningnote/issues)

## 开始之前

- 先搜索站点和仓库，优先完善已有笔记，避免重复主题。
- 一个 PR 聚焦一个主题；不要混入无关笔记、批量格式化或个人环境文件。
- 大规模重写、目录迁移、新增独立教程，建议先开 Issue 说明目标和范围；小修正可以直接提 PR。
- 保留原笔记中的个人经历和语境。知识纠错请说明依据，涉及版本差异时注明适用版本。
- 引用他人内容时标明来源，确认有权使用；不要整篇搬运受限资料，也不要提交密码、令牌或他人的隐私信息。

## 提交步骤

### 1. Fork 仓库

在 GitHub 仓库页面点击 **Fork**，将仓库复制到自己的账号。普通贡献者在自己的分支修改，通过 PR 提交到本仓库的 `master`。

如果只改几处文字，也可以在 GitHub 中打开原始 `.md` 文件，点击编辑按钮，按页面提示在自己的 Fork 中创建分支并提交修改，再进入下面的 PR 步骤。

### 2. 克隆并创建主题分支

需要本地编辑时，安装 Git，然后在 PowerShell 中执行。将用户名和分支名换成自己的值：

```powershell
$githubUser = '你的 GitHub 用户名'
git clone "https://github.com/$githubUser/learningnote.git"
Set-Location learningnote
git remote add upstream https://github.com/JingXu-Huan/learningnote.git
git fetch upstream
git switch -c docs/os-memory upstream/master
```

`origin` 指向自己的 Fork，`upstream` 指向原仓库。主题分支从最新的 `upstream/master` 创建；不要直接在 Fork 的 `master` 上堆积多个主题的修改。

后续开始另一个贡献时，先保存或提交当前修改，再执行 `git fetch upstream`，用新的分支名从 `upstream/master` 创建分支即可。

### 3. 编写并检查笔记

按下文的笔记规范修改源文件。提交前至少确认：内容正确、标题清楚、链接目标存在、没有混入附件或无关改动。

以下命令中的文件路径是示例，请换成实际修改的文件；有多个文件时逐个指定：

```powershell
git status --short
git diff --check
git diff -- '技术栈/操作系统/操作系统教程.md'
git add -- '技术栈/操作系统/操作系统教程.md'
git diff --cached
git commit -m '补充操作系统内存管理说明'
git push -u origin docs/os-memory
```

提交信息使用简短中文，说明做了什么，例如“修正进程调度计算示例”“新增 Redis 持久化对比”。只暂存本次贡献涉及的文件。

### 4. 创建 Draft PR

在 GitHub 点击 **Compare & pull request**，确认目标仓库是 `JingXu-Huan/learningnote`，目标分支是 `master`，来源是自己的 Fork 和主题分支。选择 **Create draft pull request**。

按 PR 模板填写修改内容、原因、参考来源和检查结果。有关联 Issue 时在描述中注明，例如 `Closes #123`。不需要安装站点工具才能贡献普通笔记，也不需要为自己的 Fork 配置 Pages。

PR 的 GitHub Actions 会运行链接转换测试和站点构建。检查通过、内容自查完成后，将 PR 标记为 **Ready for review**，等待维护者评审。根据意见继续修改并推送到同一主题分支，PR 会自动更新。

### 5. 合并与发布

维护者确认后合并到 `master`。主仓库的 Actions 自动构建并部署到 GitHub Pages；PR 阶段只检查和构建，不发布正式站点。

合并后可以删除自己 Fork 中已经合并的主题分支。下一次贡献重新从最新的 `upstream/master` 开始。

## Markdown 自动收录条件

| 必要条件 | 说明 |
| --- | --- |
| 文件扩展名为 `.md` | 大小写均可，推荐统一小写 |
| 使用 UTF-8 编码 | 支持带 BOM 的 UTF-8；不要使用 GBK 等编码 |
| 位于可发布的目录 | 收录 `技术栈/`、`计算机网络/`、`Linux/`、`项目与成长/` 和根目录的 `CONTRIBUTING.md`；归档、草稿、隐藏目录及站点维护文件不收录 |
| 包含有效学习内容 | 空白、只有标题、只有 Markdown 图片嵌入以及 `Untitled`、`未命名`、`草稿` 占位文件不会收录；短小的命令与章节链接仍可发布 |
| 提交到仓库并合并到 `master` | 本地预览可包含未被忽略的新笔记，线上只包含已经提交的文件 |
| Actions 构建和部署成功 | 合并后可到仓库 Actions 页面查看结果 |

**不需要 YAML front matter，也不需要手动登记导航。** 新增 Markdown 后，目录、导航和搜索会随构建更新。标题、目录和参考资料属于写作规范，不是文件被发现的前置条件。

站点仅发布 Markdown 内容。仓库中的 PDF、图片、视频等附件暂不托管；正文中的附件引用显示“附件暂未发布”。新增贡献应让核心内容能够独立阅读，避免依赖截图或 PDF 才能理解。历史附件保留原位。

## 笔记编写规范

### 目录与文件名

- 优先放入现有大类的合适主题目录，例如 `技术栈/操作系统/`、`技术栈/数据库/`、`项目与成长/`。
- 文件名表达主题，不使用 `Untitled.md` 等占位名称。连续教程可以用数字编号，并在教程入口加入章节链接。
- 使用中文为主，专有名词、API 名和命令保留英文。
- 重命名或移动已有文件会改变站点地址，也可能影响其他笔记的引用；请在 PR 中说明并更新相关链接。

### 内容与排版

- 推荐一个 `#` 主标题，正文按 `##`、`###` 组织；长文添加并维护页内目录。
- 使用 GitHub Flavored Markdown。标题、列表和代码块前后留空行，分隔线使用 `------`。
- 代码块注明语言，例如 `java`、`sql`、`powershell`；命令给出适用环境。纯文本使用 `text`。
- 先说明概念，再给出例子和适用场景。计算题写清条件、步骤、单位和结论，面试题解释原因和边界。
- 教程正文使用客观说明、操作步骤和验收标准，不写“我的计划”“我建议”或“我可以帮你”等个人叙述与聊天邀请。示例输入、代码字符串和真实面试记录中的说话者视角可以保留，但应明确其示例或记录性质。
- 表格适合比较差异；不要用过宽的表格承载整篇正文。数学公式和 Mermaid 图可以直接写在 Markdown 中。
- 参考资料优先使用官方文档、教材和原始论文；注明链接及与结论相关的版本或时间。

### 链接

优先使用相对路径 Markdown 链接，也支持 Obsidian 双向链接。例如，当前笔记位于 `技术栈/操作系统/` 时，可以引用已有教程入口：

```markdown
[操作系统教程](操作系统教程.md)
[[操作系统教程|操作系统教程]]
```

含空格的路径可写为 `[说明](<文件名 有空格.md>)`。同名笔记较多时写出完整的仓库相对路径，不依赖模糊匹配。注意文件名大小写：Windows 上可用的错误大小写链接，在 Linux 构建环境中可能失效。

缺失或无法唯一确定的引用会显示“未找到笔记”，构建报告位于 `.cache/pages/unresolved-links.txt`。构建通过不代表所有旧链接都正确；请检查自己新增或修改的链接，包括标题锚点。

## 本地预览与检查

普通文字修改可以先用编辑器或 GitHub 的 Markdown 预览。需要检查站内导航、链接、公式或图表时，使用 Python 3.13 在仓库根目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r website/requirements.txt
.\.venv\Scripts\python.exe -m mkdocs serve
```

在浏览器打开终端输出的地址，确认修改的笔记和链接正常。按 `Ctrl+C` 停止预览。已有 `.venv` 时可复用，无需重复创建。

如果修改了站点配置、构建 hook 或依赖，请额外运行：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s website -p 'test_*.py'
.\.venv\Scripts\python.exe -m mkdocs build --strict
```

只编辑仓库中的源 Markdown；不要修改生成的 `notes/<哈希>/` 页面，也不要提交 `.cache/`、`.venv/` 等本地产物。完整检查以 PR 的 Actions 结果为准。

## 反馈问题

暂时不便修改时，可以提交 Issue。请给出笔记标题、源文件路径或站点地址，说明具体段落、问题和建议；知识错误尽量附上依据。请求新增主题时，说明学习目标和已有资料即可。

贡献流程参考：[GitHub 贡献项目指南](https://docs.github.com/en/get-started/exploring-projects-on-github/contributing-to-a-project)、[GitHub 贡献指南文件说明](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/setting-guidelines-for-repository-contributors)。
