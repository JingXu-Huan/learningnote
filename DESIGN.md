---
name: 景旭的编程笔记
description: 继承蓝白笔记站的中文阅读层级，并为源码学习图提供克制的交互控件。
colors:
  primary: "#1d4ed8"
  wiki-focus: "#2563eb"
  wiki-dark-accent: "#93c5fd"
  page-bg: "#f4f6f9"
  paper: "#fff"
  ink: "#172b45"
  muted: "#56667a"
  line: "#dce3ed"
  pale: "#eef3ff"
  control-focus: "#7a9df0"
  nav-selected-bg: "#e7eeff"
  nav-selected-ink: "#173faa"
typography:
  body:
    fontFamily: 'Geist, "Microsoft YaHei", "PingFang SC", system-ui, sans-serif'
    fontSize: "14px"
    lineHeight: 1.6
  reading:
    fontFamily: 'Geist, "Microsoft YaHei", "PingFang SC", sans-serif'
    fontSize: "1rem"
    lineHeight: 1.85
  reading-heading:
    fontSize: "clamp(1.8rem, 2.6vw, 2.4rem)"
    fontWeight: 700
    lineHeight: 1.35
    letterSpacing: "-0.025em"
  page-heading:
    fontSize: "25px"
    fontWeight: 650
    lineHeight: 1.35
    letterSpacing: "-0.025em"
  graph-title:
    fontSize: "16px"
    fontWeight: 600
  graph-secondary:
    fontSize: "13px"
  functional-small:
    fontSize: "11px"
  control:
    fontSize: "12px"
  source:
    fontFamily: 'Consolas, "SFMono-Regular", monospace'
    fontSize: "11px"
    lineHeight: 1.7
rounded:
  field: "5px"
  mode: "6px"
  control: "7px"
  navigation: "8px"
  graph-node: "9px"
  panel: "12px"
spacing:
  mode-gap: "4px"
  control-y: "8px"
  toolbar-y: "12px"
  toolbar-x: "16px"
  panel-gap: "18px"
  lab-panel: "22px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.paper}"
    typography: "{typography.control}"
    rounded: "{rounded.control}"
    padding: "8px 14px"
  button-secondary:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.control}"
    rounded: "{rounded.control}"
    padding: "8px 11px"
  mode-selected:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.primary}"
    rounded: "{rounded.mode}"
    padding: "8px 17px"
  scenario-selected:
    backgroundColor: "{colors.nav-selected-bg}"
    textColor: "{colors.nav-selected-ink}"
    rounded: "{rounded.navigation}"
    padding: "11px 12px"
  diagram-panel:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.panel}"
  graph-node-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.paper}"
    rounded: "{rounded.graph-node}"
---

# Design System: 景旭的编程笔记

## Overview

**Creative North Star: "蓝白学习笔记"**

视觉依据是 `website/assets/wiki.css` 已有的蓝色强调、白色阅读面、中文导航与正文层级。当前票务架构学习页在此身份下扩展图形节点、回放和参数控件，保留内容优先的阅读方式。本记录从现有代码提取，未重新设定视觉身份，也未宣称覆盖 MkDocs 主题的全部底层变量。

具体页面的 Read / Operate 模式与内容组织保存在 `.impeccable/surfaces/ticket-architecture.md`；这里仅记录可复用的视觉约束。正文、交互状态与源码定位各有层级，解释不能被装饰挤出主要阅读区域。

**Key Characteristics:**

- 蓝色用于链接、当前选择与主要动作。
- 白色内容面、浅灰背景和细边线组织阅读区域。
- 中文系统字体回退，源码路径使用等宽字体。
- 节点与控件状态同时通过文字、形状或边线表达。

## Colors

### Primary

`primary` 直接继承 `wiki.css` 的 `--wiki-accent`；交互页对应 `--blue`。用于链接、选中节点、回放主动作和派单结果强调。`pale` 为已访问节点、步骤和类型标记提供浅底色。`nav-selected-bg` 与 `nav-selected-ink` 对应当前业务目录项。

### Neutral

`page-bg`、`paper`、`ink`、`muted`、`line` 来自交互页 `:root`，分别组织页面底、内容面、主文字、辅助文字与分隔线。其他组件特有值仍以组件 CSS 为依据，不把所有单次出现的颜色升级成全局调色板。

`wiki-focus` 与 `wiki-dark-accent` 记录原站已有焦点和暗色强调；交互页当前显式采用 light 色彩方案，不据此推断已实现暗色切换。其控件焦点使用独立的 `control-focus`。

## Typography

正文使用现有 Geist 与中文系统字体栈；交互页没有下载外部字体，Geist 不存在时由本机字体回退。源码行号和路径使用 Consolas / SFMono-Regular 等宽栈。

笔记长文保持 `reading` 的较宽行距与 `reading-heading` 的响应式标题。交互页使用更紧凑的 `body`，主标题为 `page-heading`，图节点标题为 `graph-title`，按钮与辅助控件使用 `control`。右侧解释正文在现有代码中为 13px / 1.85，源码路径为 `source`；不要把小号路径文字误当成长文正文标准。

交互页可见功能小字的标称字号至少为 `functional-small`，包括手机图例、步骤、类型标记、源码路径与复制动作；手机仅隐藏下载按钮的文字，图标保留。节点副字、步骤编号与区域标签使用 `graph-secondary`，不能重新缩成原先的细小标签。SVG 字号随整图缩放，标称字号与最终屏幕像素应分别理解。

## Layout

全站阅读布局继承 `wiki.css`：桌面文章可用宽度随屏幕扩展，1280px 起阅读面最大 76rem；全宽模式最大 96rem。正文段落和标题间距由现有文章样式维持。

票务交互页是独立的学习表面：76px 顶栏，左侧 196px 业务目录，中间自适应图，右侧 302px 详情。图与详情间距为 `panel-gap`。1600px 起目录为 210px、详情为 326px。1360px 及以下，详情移到图下；760px 及以下，目录变成横向可滚动导航。

图不再设置固定的 850px 最小宽度。脚本根据当前 viewBox 的 `graphWidth`、`graphHeight` 乘以 `zoom`，四舍五入后给 SVG 设置明确的像素宽高。全景 viewBox 为 1000 × 750；桌面全景容器采用自然高度，手机保留 430px 可滚动窗口。进入手机全景时初始缩放为 85%，仍可手动缩放或执行适配。

业务回放在桌面按三列排列，760px 及以下改为 viewBox 宽度为 360 的单列流程，容器高 430px。viewBox 使用图形坐标单位，最终像素尺寸仍由缩放计算。回放图高按步骤数量计算；1360px 及以下的非手机回放容器高 540px。适配按钮根据容器可用宽度计算比例，桌面回放同时考虑可用高度，最大不超过 100%。当前步骤超出窗口时，`followCurrent` 会立即滚动到其中心附近；步骤变化、缩放与窗口尺寸变化后都会重新调用。窗口尺寸变化使用 100ms 延后重绘并适配，而不是依赖 CSS 最小宽度掩盖尺寸变化。

图框、播放条和详情是连续的阅读任务区域。派单推演以三行示例客服参数呈现；不要把每个字段或说明拆成独立悬浮卡片。这是当前表面的布局记录，不要求其他笔记复制三栏结构。

## Elevation & Depth

当前界面主要依靠背景色与细边线区分区域。图框和推演面静止时没有大阴影。顶栏选中模式使用一层很浅的阴影，仅帮助识别当前选项；精确值保存在 sidecar。节点通过描边、填色与文字变化表示状态。

## Shapes

面板使用 `panel` 圆角，按钮使用 `control`，字段与步骤标记使用 `field`。图节点的 SVG 圆角对应 `graph-node`。小圆点表达业务目录项，虚实线表达图中关系；形状服务导航和业务识别，不承担额外品牌装饰。

## Components

### Buttons

主要按钮为蓝底白字，用于播放和分配一单；次要按钮为白底、细边线，用于上一步、下一步和重置。当前代码没有单独的主按钮悬停色，不在记录中补造。控件都有可见焦点；禁用按钮降低透明度并改变光标。

### Navigation

模式切换采用浅灰容器和白色选中项，通过 `aria-selected` 表达状态。业务目录采用整行点击区域，当前项为浅蓝底与深蓝字，并填充圆点；窄屏沿横向浏览。原笔记树仍使用 `wiki.css` 的层级缩进、展开箭头和当前页标记。

### Cards / Containers

图面板以白底、细线、圆角构成，并将工具条、图和图例分区。派单面板沿用同一底色与边线。它们用于承载连续任务，不是数据 KPI 卡片。

### Inputs / Fields

数字参数、选择器和进度滑块保留原生输入能力。数字输入为白底细边线、`field` 圆角，数字使用等宽数字特性；滑块和复选框沿用蓝色强调。字段标签与解释文字保持可见。

### Graph / State / Source

节点默认白底灰线，悬停或焦点时蓝线浅底，当前节点蓝底白字，走过节点浅蓝底。实线、长虚线和点线分别配合图例表达调用、异步和数据访问。右侧状态采用名称—值的两列关系，源码标题下方显示可换行的等宽路径，并提供复制动作。

图形字号、移动端单列、真实尺寸适配及当前步骤跟随共同支持阅读。改变缩放时调整 SVG 的像素尺寸，不只改变显示的百分比；跟随只滚动图形容器，不把整篇页面拉走。实现依据为 `website/visualizations/ticket-architecture/index.html` 的节点样式、`renderFlow`、`followCurrent`、`applyZoom`、`fitGraph` 与 resize 监听。

CSS 节点过渡为 0.2s；`prefers-reduced-motion` 下停用过渡与动画。业务回放由用户触发，速度是学习控件参数，不是生产任务耗时。打印样式隐藏操作条，保留图与说明。

## Do's and Don'ts

### Do:

- **Do** 继承笔记站现有蓝色强调、白色阅读面与中文层级。
- **Do** 将图节点、文字说明、状态和源码出处保持在同一阅读任务中。
- **Do** 用文字、线型和选中标记补充颜色含义。
- **Do** 保留键盘焦点、窄屏横向浏览与减少动态效果偏好。

### Don't:

- **Don't** 为这个学习页另造品牌字体、装饰性调色板或展示型视觉身份。
- **Don't** 将交互页的小号路径字号推广为全站正文字号。
- **Don't** 用只有颜色变化的状态代替节点文字和图例。
- **Don't** 把页面三栏布局或 Read / Operate 模式推广成所有笔记页面的强制模板。
