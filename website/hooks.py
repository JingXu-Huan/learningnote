"""Publish Git-visible Markdown notes without copying repository attachments."""

from __future__ import annotations

import hashlib
import logging
import posixpath
import shutil
import subprocess
from collections import Counter
from pathlib import Path, PurePosixPath
from urllib.parse import quote, unquote, urlsplit
from xml.etree import ElementTree

from bs4 import BeautifulSoup
from markdown.extensions import Extension
from markdown.extensions.toc import slugify_unicode
from markdown.inlinepatterns import InlineProcessor
from markdown.treeprocessors import Treeprocessor
from mkdocs.utils import get_relative_url

ROOT = Path(__file__).resolve().parents[1]
LOGGER = logging.getLogger("mkdocs.pages")
INTERNAL_NAMES = {"agents.md", "claude.md"}


def is_note(path: str) -> bool:
    parts = PurePosixPath(path).parts
    return (
        bool(parts)
        and PurePosixPath(path).suffix.lower() == ".md"
        and PurePosixPath(path).name.lower() not in INTERNAL_NAMES
        and parts[0] != "website"
        and not any(part.startswith(".") or part == ".." for part in parts)
    )


class Library:
    def __init__(self, paths: list[str]):
        self.routes = {
            path: "notes/" + hashlib.sha256(path.encode()).hexdigest()[:20] + ".md"
            for path in sorted(paths)
            if is_note(path)
        }
        if len(set(self.routes.values())) != len(self.routes):
            raise ValueError("Note route collision")
        self.sources = {route: path for path, route in self.routes.items()}
        self.missing: set[tuple[str, str]] = set()

    def resolve(self, target: str, source: str) -> str | None:
        target = unquote(target).replace("\\", "/").strip()
        if not target:
            return source if source in self.routes else None
        if PurePosixPath(target).suffix.lower() != ".md":
            target += ".md"
        candidates = [
            posixpath.normpath(posixpath.join(posixpath.dirname(source), target)),
            posixpath.normpath(target.lstrip("/")),
        ]
        for candidate in candidates:
            if candidate in self.routes:
                return candidate
        matches = [path for path in self.routes if path.endswith("/" + target)]
        if len(matches) == 1:
            return matches[0]
        if len(matches) > 1:
            parent = PurePosixPath(source).parent.parts

            def proximity(path: str) -> int:
                score = 0
                for a, b in zip(parent, PurePosixPath(path).parent.parts):
                    if a != b:
                        break
                    score += 1
                return score

            best = max(proximity(path) for path in matches)
            nearest = [path for path in matches if proximity(path) == best]
            if best > 0 and len(nearest) == 1:
                return nearest[0]
        return None


class WikiLink(InlineProcessor):
    def handleMatch(self, match, data):
        embedded, value = match.group(1, 2)
        target, separator, label = value.partition("|")
        node = ElementTree.Element("a")
        node.set("href", target.strip())
        node.set("data-wikilink", "true")
        if embedded:
            node.set("data-embedded", "true")
        node.text = label if separator else target
        return node, match.start(0), match.end(0)


class WikiLinks(Extension):
    def extendMarkdown(self, md):
        md.inlinePatterns.register(WikiLink(r"(!?)\[\[([^\]\n]+)\]\]", md), "wiki", 175)
        md.treeprocessors.register(NoteReferences(md), "note_references", 2)


LIBRARY = Library([])
CURRENT_PAGE = None
CURRENT_FILES = None


def resolve_reference(href: str, wiki: bool, source: str):
    target, _, fragment = href.partition("#")
    parsed = urlsplit(target)
    if parsed.scheme in {"http", "https", "mailto", "tel"} or target.startswith("//"):
        return None, None, None
    if not target and not wiki:
        return None, None, None
    if parsed.scheme:
        return None, None, "附件暂未发布"
    original = LIBRARY.resolve(parsed.path, source)
    route = LIBRARY.routes.get(original) if original else None
    if (
        route is None
        and source == "index.md"
        and parsed.path in {"index.md", "catalogue.md"}
    ):
        route = parsed.path
    if route is None:
        suffix = PurePosixPath(unquote(parsed.path)).suffix.lower()
        attachments = {
            ".pdf",
            ".png",
            ".jpg",
            ".jpeg",
            ".svg",
            ".gif",
            ".webp",
            ".mp4",
            ".mp3",
            ".zip",
            ".canvas",
            ".docx",
            ".xlsx",
        }
        if suffix in attachments or (not wiki and suffix not in {"", ".md"}):
            return None, None, "附件暂未发布"
        LIBRARY.missing.add((source, href))
        return None, None, "未找到笔记"
    anchor = slugify_unicode(unquote(fragment), "-") if wiki else unquote(fragment)
    return route, quote(anchor, safe="-_"), None


class NoteReferences(Treeprocessor):
    """Rewrite parsed Markdown before MkDocs checks/relativizes links."""

    def run(self, root):
        if CURRENT_PAGE is None:
            return root
        source = LIBRARY.sources.get(CURRENT_PAGE.file.src_uri, "index.md")
        for element in root.iter():
            reason = None
            if element.tag == "img":
                text = element.get("alt") or "附件"
                reason = "附件暂未发布"
            elif element.tag == "a" and element.get("href") is not None:
                wiki = element.get("data-wikilink") == "true"
                route, fragment, reason = resolve_reference(
                    element.get("href"), wiki, source
                )
                if route:
                    if CURRENT_FILES.get_file_from_path(route) is None:
                        raise ValueError(f"Missing generated route: {route}")
                    element.set(
                        "href",
                        posixpath.relpath(
                            route, posixpath.dirname(CURRENT_PAGE.file.src_uri) or "."
                        ),
                    )
                    if fragment:
                        element.set("href", element.get("href") + "#" + fragment)
                    element.set("data-note-resolved", "true")
                text = "".join(element.itertext())
                element.attrib.pop("data-wikilink", None)
                element.attrib.pop("data-embedded", None)
            else:
                continue
            if reason:
                element.tag = "span"
                element.attrib.clear()
                element.set("class", "unpublished-reference")
                element.set("title", reason)
                for child in list(element):
                    element.remove(child)
                element.text = f"{text}（{reason}）"
        return root


def on_page_markdown(markdown, page, config, files):
    global CURRENT_PAGE, CURRENT_FILES
    CURRENT_PAGE, CURRENT_FILES = page, files
    return markdown


def tracked_notes() -> list[str]:
    output = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
    ).decode("utf-8")
    return sorted({path for path in output.split("\0") if is_note(path)})


def label(path: str) -> str:
    return PurePosixPath(path).stem


def note_link(path: str) -> str:
    return f"[{label(path)}](<{quote(path, safe='/')}>)"


def navigation(paths: list[str], library: Library) -> list:
    tree: dict = {}
    for path in paths:
        if path == "CONTRIBUTING.md":
            continue
        branch = tree
        parts = PurePosixPath(path).parts
        for directory in parts[:-1]:
            branch = branch.setdefault(directory, {})
        branch[parts[-1]] = library.routes[path]

    def walk(branch: dict) -> list:
        result = []

        def order(item):
            name, value = item
            overview = not isinstance(value, dict) and (
                name.lower() == "readme.md"
                or label(name).endswith(("教程", "快速上手", "知识总结"))
            )
            return (not overview, name.casefold())

        for name, value in sorted(branch.items(), key=order):
            result.append(
                {
                    name if isinstance(value, dict) else label(name): walk(value)
                    if isinstance(value, dict)
                    else value
                }
            )
        return result

    nav = [{"首页": "index.md"}, {"全部笔记": "catalogue.md"}, *walk(tree)]
    if "CONTRIBUTING.md" in library.routes:
        nav.append({"贡献指南": library.routes["CONTRIBUTING.md"]})
    return nav


def on_config(config):
    global LIBRARY
    for plugin in config.plugins.values():
        if type(plugin).__module__ == "shadcn.plugins.search":
            # Generated copies have no Git history and dates are not displayed.
            plugin.has_commits = False
    expected = ROOT / ".cache" / "pages" / "docs"
    docs = expected.resolve()
    # Refuse symlink/reparse-point redirection or deletion outside our build area.
    if docs != expected or not docs.is_relative_to(ROOT):
        raise ValueError("docs_dir must be the repository's .cache/pages/docs")
    config.docs_dir = str(docs)
    paths = tracked_notes()
    LIBRARY = Library(paths)
    if docs.exists():
        shutil.rmtree(docs)
    docs.mkdir(parents=True)
    for source, route in LIBRARY.routes.items():
        original = (ROOT / source).resolve()
        if not original.is_relative_to(ROOT):
            raise ValueError(f"Note outside repository: {source}")
        destination = docs / route
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            original.read_text(encoding="utf-8-sig"), encoding="utf-8"
        )

    groups = Counter(
        PurePosixPath(path).parts[0]
        if len(PurePosixPath(path).parts) > 1
        else "仓库文档"
        for path in paths
    )
    home = [
        "# 景旭的编程笔记",
        "",
        "从课程知识到后端实践，按目录学习，也可以搜索一个具体问题。",
        "",
        "## 系统教程",
        "",
    ]
    for path in [
        "技术栈/操作系统/操作系统教程.md",
        "技术栈/Go语言/Go语言从0到1快速上手.md",
        "技术栈/分布式与网络/计算机网络/计算机网络知识总结.md",
    ]:
        if path in LIBRARY.routes:
            home.append("- " + note_link(path))
    home.extend(
        [
            "",
            "## 按目录浏览",
            "",
            f"当前收录 **{len(paths)} 篇** Markdown 笔记。左侧按目录展开主题，右侧查看本页目录；手机上通过顶部菜单浏览。",
            "",
            "| 目录 | 笔记数 |",
            "| --- | ---: |",
            *[f"| {group} | {count} |" for group, count in sorted(groups.items())],
            "",
            "[浏览全部笔记](catalogue.md)",
            "",
            "## 阅读说明",
            "",
            "- 搜索支持中文和英文；长文可通过页内目录定位。",
            "- 原笔记中的双向链接会转换为站内链接。",
            "- PDF、图片和其他附件暂未发布，文中会保留提示。",
            "- 原始 Markdown 保留在 GitHub，可通过每页的源码入口查看。",
            "",
        ]
    )
    if "CONTRIBUTING.md" in LIBRARY.routes:
        home.extend(
            [
                "## 参与贡献",
                "",
                (
                    "欢迎纠正错误、补充解释和分享学习笔记。"
                    "请先阅读[贡献指南](CONTRIBUTING.md)，按 Fork、主题分支和 PR 的流程提交。"
                ),
                "",
            ]
        )
    (docs / "index.md").write_text("\n".join(home), encoding="utf-8")
    catalogue = ["# 全部笔记", "", "按仓库目录排列；也可以使用侧栏和站内搜索。", ""]
    previous = None
    for path in paths:
        parent = str(PurePosixPath(path).parent)
        if parent != previous:
            catalogue.extend([f"## {'仓库文档' if parent == '.' else parent}", ""])
            previous = parent
        catalogue.append("- " + note_link(path))
    (docs / "catalogue.md").write_text("\n".join(catalogue) + "\n", encoding="utf-8")
    assets = docs / "assets"
    assets.mkdir()
    for name in ("mathjax.js", "wiki.js", "wiki.css", "search-worker.js"):
        shutil.copyfile(ROOT / "website" / "assets" / name, assets / name)
    config.nav = navigation(paths, LIBRARY)
    config.markdown_extensions.append(WikiLinks())
    LOGGER.info(
        "Selected %d Markdown notes; repository attachments excluded", len(paths)
    )
    return config


def unpublished(soup, node, text: str, reason: str):
    replacement = soup.new_tag("span")
    replacement["class"] = "unpublished-reference"
    replacement["title"] = reason
    replacement.string = f"{text}（{reason}）"
    node.replace_with(replacement)


def on_page_content(html, page, config, files):
    source = LIBRARY.sources.get(page.file.src_uri, "index.md")
    soup = BeautifulSoup(html, "html.parser")
    for node in soup.find_all(
        ["picture", "img", "video", "audio", "iframe", "object", "embed"]
    ):
        if node.parent is not None:
            unpublished(soup, node, node.get("alt") or "附件", "附件暂未发布")
    for node in soup.find_all("a", href=True):
        if node.has_attr("data-note-resolved"):
            node.attrs.pop("data-note-resolved", None)
            continue
        href = node["href"]
        wiki = node.has_attr("data-wikilink")
        node.attrs.pop("data-wikilink", None)
        node.attrs.pop("data-embedded", None)
        route, fragment, reason = resolve_reference(href, wiki, source)
        if reason:
            unpublished(soup, node, node.get_text(), reason)
            continue
        if route is None:
            continue
        target_file = files.get_file_from_path(route) if route else None
        if target_file is None:
            raise ValueError(f"Missing generated route: {route}")
        node["href"] = get_relative_url(target_file.url, page.file.url)
        if fragment:
            node["href"] += "#" + fragment
    return str(soup)


def on_page_context(context, page, config, nav):
    # shadcn 0.12.1 queues source copies even when hide_source_files=True.
    # Keep the source hidden and use GitHub source links instead of local copies.
    for plugin in config.plugins.values():
        if type(plugin).__module__ == "shadcn.plugins.search":
            plugin.raw_markdown.clear()
    source = LIBRARY.sources.get(page.file.src_uri)
    if source:
        page.edit_url = config.repo_url + "/edit/master/" + quote(source, safe="/")
    else:
        page.edit_url = None
    return context


def on_env(env, config, files):
    translations = {
        "Menu": "目录",
        "Toggle Menu": "打开目录",
        "Toggle menu": "打开目录",
        "Toggle theme": "切换深浅色",
        "Toggle layout": "切换阅读宽度",
        "On This Page": "本页目录",
        "Previous": "上一页",
        "Next": "下一页",
        "Search documentation...": "搜索笔记…",
        "Search...": "搜索…",
        "Copy Page": "复制内容",
    }
    fallback = env.globals.get("_", lambda message: message)
    env.globals["_"] = lambda message: translations.get(message) or fallback(message)
    return env


def on_post_page(output, page, config):
    return output.replace('<html lang="en">', '<html lang="zh-CN">', 1)


def on_post_build(config):
    site = Path(config.site_dir)
    forbidden = {".md", ".pdf", ".docx", ".xlsx", ".zip", ".canvas"}
    unwanted = [
        path
        for path in site.rglob("*")
        if path.is_file() and path.suffix.lower() in forbidden
    ]
    if unwanted:
        raise ValueError(f"Unexpected source or attachment in site: {unwanted}")
    for route in LIBRARY.routes.values():
        if not (site / route.removesuffix(".md") / "index.html").is_file():
            raise ValueError(f"Note was not rendered: {route}")
    if LIBRARY.missing:
        report = ROOT / ".cache" / "pages" / "unresolved-links.txt"
        report.write_text(
            "\n".join(
                f"{source}: {target}" for source, target in sorted(LIBRARY.missing)
            ),
            encoding="utf-8",
        )
        LOGGER.info(
            "%d existing unresolved references shown as text; see %s",
            len(LIBRARY.missing),
            report,
        )
