import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

import markdown
from bs4 import BeautifulSoup

from website import hooks


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.previous = hooks.LIBRARY
        hooks.LIBRARY = hooks.Library(
            [
                "技术栈/操作系统/教程.md",
                "技术栈/操作系统/章节 (一).md",
                "技术栈/操作系统/13-6.2 属性填充.md",
                "技术栈/Java/快速入门.md",
                "项目与成长/快速入门.md",
            ]
        )
        self.page = Mock()
        self.page.file.src_uri = hooks.LIBRARY.routes["技术栈/操作系统/教程.md"]
        self.page.file.url = "notes/current/"
        self.files = Mock()
        self.files.get_file_from_path.side_effect = lambda route: (
            Mock(url=route.removesuffix(".md") + "/") if route else None
        )

    def tearDown(self):
        hooks.LIBRARY = self.previous

    def render(self, text):
        html = markdown.markdown(text, extensions=[hooks.WikiLinks(), "fenced_code"])
        return BeautifulSoup(
            hooks.on_page_content(html, self.page, Mock(), self.files), "html.parser"
        )

    def test_source_selection_excludes_attachments_and_internal_files(self):
        for path in [
            "a.pdf",
            "a.png",
            "a.js",
            "AGENTS.md",
            "目录/CLAUDE.md",
            ".cache/test.md",
            "website/README.md",
            "../escape.md",
        ]:
            self.assertFalse(hooks.is_note(path), path)
        self.assertTrue(hooks.is_note("教程/章节.MD"))

    def test_links_with_chinese_spaces_alias_and_fragment(self):
        html = self.render("[[章节 (一)#内存 管理|地址转换]]")
        link = html.find("a")
        self.assertEqual(link.text, "地址转换")
        self.assertTrue(
            link["href"].endswith("/#%E5%86%85%E5%AD%98-%E7%AE%A1%E7%90%86")
        )
        self.assertNotIn("data-wikilink", link.attrs)

    def test_ordinary_markdown_link_uses_same_route(self):
        html = self.render("[章节](<章节%20(一).md>)")
        route = hooks.LIBRARY.routes["技术栈/操作系统/章节 (一).md"].removesuffix(".md")
        self.assertEqual(html.a["href"], "../" + Path(route).name + "/")

    def test_code_examples_remain_literal(self):
        html = self.render("`[[章节 (一)]]`\n\n```text\n[[章节 (一)]]\n```")
        self.assertIsNone(html.find("a"))
        self.assertEqual(len(html.find_all("code")), 2)

    def test_wikilink_title_with_dots_is_not_mistaken_for_attachment(self):
        html = self.render("[[13-6.2 属性填充]]")
        self.assertIsNotNone(html.find("a"))
        self.assertNotIn("附件暂未发布", html.text)

    def test_images_html_embeds_and_local_pdf_are_not_requested(self):
        html = self.render(
            '![示意图](../image.png)\n\n<img src="remote.jpg">\n\n[讲义](slides.pdf)'
        )
        self.assertIsNone(html.find("img"))
        self.assertIsNone(html.find("a"))
        self.assertIn("附件暂未发布", html.text)

    def test_missing_or_ambiguous_links_do_not_create_dead_links(self):
        hooks.LIBRARY = hooks.Library([*hooks.LIBRARY.routes, "技术栈/Go/快速入门.md"])
        html = self.render("[[不存在]] [[快速入门]]")
        self.assertIsNone(html.find("a"))
        self.assertEqual(html.text.count("未找到笔记"), 2)

    def test_duplicate_names_choose_unique_nearest_directory(self):
        self.assertEqual(
            hooks.LIBRARY.resolve("快速入门", "技术栈/Java/笔记.md"),
            "技术栈/Java/快速入门.md",
        )
        self.assertIsNone(hooks.LIBRARY.resolve("快速入门", "其他/笔记.md"))

    def test_routes_do_not_depend_on_other_files_or_contents(self):
        path = "技术栈/操作系统/教程.md"
        self.assertEqual(hooks.Library([path]).routes[path], hooks.LIBRARY.routes[path])

    def test_contribution_guide_has_one_named_navigation_entry(self):
        paths = [*hooks.LIBRARY.routes, "CONTRIBUTING.md"]
        library = hooks.Library(paths)
        nav = hooks.navigation(paths, library)
        self.assertEqual(nav[-1], {"贡献指南": library.routes["CONTRIBUTING.md"]})
        self.assertEqual(str(nav).count(library.routes["CONTRIBUTING.md"]), 1)
        self.assertNotIn("'CONTRIBUTING':", str(nav))

    def test_navigation_without_contribution_guide_keeps_existing_entries(self):
        nav = hooks.navigation(list(hooks.LIBRARY.routes), hooks.LIBRARY)
        self.assertEqual(nav[:2], [{"首页": "index.md"}, {"全部笔记": "catalogue.md"}])
        self.assertNotIn("贡献指南", str(nav))

    def test_shadcn_cannot_publish_queued_markdown_sources(self):
        plugin_type = type("SearchPlugin", (), {"__module__": "shadcn.plugins.search"})
        plugin = plugin_type()
        plugin.raw_markdown = {"original.md": "site/notes/source.md"}
        config = SimpleNamespace(
            plugins={"search": plugin}, repo_url="https://github.com/example/notes"
        )
        hooks.on_page_context({}, self.page, config, [])
        self.assertFalse(plugin.raw_markdown)
        self.assertIn("/edit/master/", self.page.edit_url)

    def test_chinese_document_language_does_not_rewrite_article_content(self):
        output = '<html lang="en"><body>example lang="en"</body></html>'
        result = hooks.on_post_page(output, self.page, Mock())
        self.assertTrue(result.startswith('<html lang="zh-CN">'))
        self.assertIn('example lang="en"', result)


if __name__ == "__main__":
    unittest.main()
