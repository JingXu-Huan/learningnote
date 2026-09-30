(function () {
  const assetUrl = new URL(".", document.currentScript.src);
  const menuButton = document.getElementById("menu-button");
  const menuDialog = document.getElementById("bottom-sidebar");
  if (menuButton && menuDialog) {
    menuButton.setAttribute("aria-controls", "bottom-sidebar");
    menuButton.addEventListener("click", () => {
      menuButton.dataset.state = "open";
      menuButton.setAttribute("aria-expanded", "true");
    });
    menuDialog.addEventListener("close", () => {
      menuButton.dataset.state = "closed";
      menuButton.setAttribute("aria-expanded", "false");
    });
  }
  const input = document.getElementById("mkdocs-search-query");
  const results = document.getElementById("mkdocs-search-results");
  if (!input || !results) return;
  let worker;
  let requestId = 0;
  let timer;
  const status = (text) => {
    const paragraph = document.createElement("p");
    paragraph.textContent = text;
    results.replaceChildren(paragraph);
  };
  const search = () => {
    const id = ++requestId;
    const query = input.value.trim();
    if (!query) { results.replaceChildren(); return; }
    status("正在搜索…");
    if (!worker) {
      worker = new Worker(new URL("search-worker.js", assetUrl));
      worker.onerror = () => {
        status("搜索暂时不可用，请重新输入关键词再试。");
        worker.terminate();
        worker = null;
      };
      worker.onmessage = ({ data }) => {
        if (data.id !== requestId) return;
        if (data.error) { status("搜索索引加载失败，请重新输入关键词再试。"); return; }
        if (!data.results.length) { status("没有找到相关笔记，请换个关键词。"); return; }
        const fragment = document.createDocumentFragment();
        for (const result of data.results) {
          const article = document.createElement("article");
          const heading = document.createElement("h3");
          const link = document.createElement("a");
          link.href = new URL(result.location, `${base_url}/`).href;
          link.textContent = result.title;
          heading.append(link);
          const paragraph = document.createElement("p");
          paragraph.textContent = result.summary;
          article.append(heading, paragraph);
          fragment.append(article);
        }
        results.replaceChildren(fragment);
      };
    }
    worker.postMessage({ id, query });
  };
  input.addEventListener("input", () => {
    ++requestId;
    clearTimeout(timer);
    timer = setTimeout(search, 150);
  });
})();

(async () => {
  const diagrams = [...document.querySelectorAll(".mermaid")];
  if (!diagrams.length) return;
  const originals = diagrams.map((element) => element.textContent);
  try {
    const { default: mermaid } = await import("https://cdn.jsdelivr.net/npm/mermaid@12.0.0/dist/mermaid.esm.min.mjs");
    let rendering = false;
    let pending = false;
    const render = async () => {
      if (rendering) { pending = true; return; }
      rendering = true;
      try {
        mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: document.documentElement.classList.contains("dark") ? "dark" : "default" });
        for (const [index, element] of diagrams.entries()) {
          element.textContent = originals[index];
          element.removeAttribute("data-processed");
        }
        await mermaid.run({ nodes: diagrams, suppressErrors: true });
      } finally {
        rendering = false;
        if (pending) { pending = false; void render(); }
      }
    };
    await render();
    new MutationObserver(() => void render()).observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  } catch (error) {
    console.warn("图表暂时无法加载，已保留原始内容。", error);
  }
})();
