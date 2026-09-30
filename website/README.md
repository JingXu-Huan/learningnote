# Markdown 笔记站点

站点地址：https://jingxu-huan.github.io/learningnote/

## 发布范围

- Git 跟踪的 Markdown 学习笔记，以及本地预览时未被 Git 忽略的新笔记。
- 保留原仓库目录作为导航层级，站内路由使用源文件路径的稳定哈希，避免中文、空格和括号造成 URL 歧义。
- 不复制仓库中的 PDF、图片、视频、压缩包等附件。正文中的媒体嵌入和本地附件链接显示“附件暂未发布”。外部普通参考链接仍保留。
- 排除隐藏目录、AGENTS.md、CLAUDE.md 和 website 下的站点维护文档。
- 原始笔记不改写；构建时在 .cache/pages/docs 中生成副本及首页、总目录。
- Obsidian 双向链接转换为站内链接；缺失或无法唯一确定的笔记显示提示，报告写到 .cache/pages/unresolved-links.txt。
- Mermaid、数学公式、表格、代码高亮和任务列表由阅读主题支持。Mermaid 和 MathJax 的浏览器运行库使用公共 CDN；主题自身的样式、脚本与图标属于站点运行资源。

## 本地预览（PowerShell）

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r website/requirements.txt
.\.venv\Scripts\python.exe -m mkdocs serve
```

终端会打印预览地址。预览和发布使用同一份 mkdocs.yml 与构建 hook。

## 检查与构建

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s website -p 'test_*.py'
.\.venv\Scripts\python.exe -m mkdocs build --strict
```

静态产物位于 .cache/pages/site，不提交到 Git。添加、删除或移动笔记后，下一次构建自动更新导航与搜索。

## GitHub Actions

推送到 master，且修改 Markdown 或站点配置时，自动检查、构建并部署；Actions 中也可手动运行 Publish Markdown notes。Pull Request 仅检查与构建，不部署。

Pages 发布来源使用 GitHub Actions（build_type=workflow），部署使用官方 Pages artifact 与 deploy-pages Actions，不需要 gh-pages 分支或额外令牌。

依赖版本和 Action 提交固定。更新时同时验证链接转换测试与完整文档构建。外部历史链接和原笔记中的旧标题锚点不在本次内容修复范围。

参考：[GitHub Pages 自定义工作流](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)、[MkDocs 配置](https://www.mkdocs.org/user-guide/configuration/)、[Material 图表](https://squidfunk.github.io/mkdocs-material/reference/diagrams/)、[Material 公式](https://squidfunk.github.io/mkdocs-material/reference/math/)。
