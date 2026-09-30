# Markdown 笔记站点

站点地址：https://jingxu-huan.github.io/learningnote/

笔记贡献流程见仓库根目录的[贡献指南](../CONTRIBUTING.md)。本页说明站点维护与构建方式。

## 阅读主题

使用 `mkdocs-shadcn==0.12.1`，版本已核对 PyPI 并固定。主题提供 Geist 字体、浅色与深色模式、阅读目录和代码高亮。通过 `website/overrides/` 适配中文控件、多层目录、移动端菜单与搜索，并保留每篇笔记的 GitHub 源码入口。

发布路由沿用源文件路径的稳定哈希。只复制明确列出的站点运行资产，不复制仓库附件或原始 Markdown。shadcn 0.12.1 的源码复制队列由构建 hook 清除，最终产物检查仍禁止 `.md` 等源文件。

搜索复用 MkDocs 生成的正文索引，在 Web Worker 中匹配中文短语和英文关键词，优先返回标题匹配。索引在第一次搜索时加载，避免每次阅读都下载索引；结果最多显示 30 条。

## 发布范围

- 仅收录 `技术栈/`、`计算机网络/`、`Linux/`、`项目与成长/` 下的 Markdown 学习内容，以及根目录的 `CONTRIBUTING.md` 贡献指南。本地预览也收录这些目录下未被 Git 忽略的新笔记。
- 保留原仓库目录作为导航层级，站内路由使用源文件路径的稳定哈希，避免中文、空格和括号造成 URL 歧义。
- 不复制仓库中的 PDF、图片、视频、压缩包等附件。正文中的媒体嵌入和本地附件链接显示“附件暂未发布”。外部普通参考链接仍保留。
- 排除隐藏目录、`AGENTS.md`、`CLAUDE.md`、`website/`、`资料归档/`、`归档/`、`草稿/`、`drafts/`、`tmp/`，以及上述学习目录之外的文件。归档源文件保留在仓库中。
- 排除 `Untitled`、`未命名`、`草稿` 及其数字编号文件；空白、只有标题或只有 Markdown 图片嵌入的笔记不发布。短小但包含命令、正文或章节链接的学习笔记仍会收录。
- 排除规则同时作用于页面生成、导航、总目录和搜索索引；未移动的笔记保持原有发布路由，重命名或移动文件会改变页面地址。
- 原始笔记不改写；构建时在 .cache/pages/docs 中生成副本及首页、总目录。
- Obsidian 双向链接转换为站内链接；缺失或无法唯一确定的笔记显示提示，报告写到 .cache/pages/unresolved-links.txt。
- Mermaid、数学公式、表格、代码高亮和任务列表均保留。Mermaid 12.0.0 和 MathJax 的浏览器运行库使用公共 CDN；图表随深浅色切换重新绘制。主题自身的字体、样式、脚本与图标属于站点运行资源。

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
node --test website/test_search.js
.\.venv\Scripts\python.exe -m mkdocs build --strict
```

静态产物位于 .cache/pages/site，不提交到 Git。添加、删除或移动笔记后，下一次构建自动更新导航与搜索。

## GitHub Actions

推送到 master，且修改 Markdown 或站点配置时，自动检查、构建并部署；Actions 中也可手动运行 Publish Markdown notes。Pull Request 仅检查与构建，不部署。

Pages 发布来源使用 GitHub Actions（build_type=workflow），部署使用官方 Pages artifact 与 deploy-pages Actions，不需要 gh-pages 分支或额外令牌。

依赖版本和 Action 提交固定。更新时同时验证链接转换测试与完整文档构建。外部历史链接和原笔记中的旧标题锚点不在本次内容修复范围。

参考：[GitHub Pages 自定义工作流](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)、[MkDocs 配置](https://www.mkdocs.org/user-guide/configuration/)、[shadcn 主题配置](https://asiffer.github.io/mkdocs-shadcn/get_started/)、[Mermaid](https://mermaid.js.org/)。
