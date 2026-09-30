function normalize(value) {
  return value.normalize("NFKC").toLowerCase();
}

function prepareDocuments(documents) {
  return documents.map((document) => ({
    ...document,
    titleSearch: normalize(document.title),
    textSearch: normalize(document.text),
  }));
}

function rankDocuments(documents, query) {
  const normalized = normalize(query).trim();
  if (!normalized) return [];
  const terms = normalized.split(/\s+/);
  return documents
    .filter((document) => terms.every((term) => document.titleSearch.includes(term) || document.textSearch.includes(term)))
    .map((document) => {
      const position = document.textSearch.indexOf(terms[0]);
      const score = (document.titleSearch === normalized ? 1000 : 0)
        + (document.titleSearch.includes(normalized) ? 100 : 0)
        + terms.filter((term) => document.titleSearch.includes(term)).length * 20;
      // Keep the excerpt in original characters, including NFKC-expanding text.
      const offset = normalize(document.text).length === document.text.length ? Math.max(0, position - 35) : 0;
      return { location: document.location, title: document.title, summary: document.text.slice(offset, offset + 160), score };
    })
    .sort((a, b) => b.score - a.score || a.location.localeCompare(b.location))
    .slice(0, 30);
}

if (typeof module !== "undefined") {
  module.exports = { prepareDocuments, rankDocuments };
} else {
  let documents;
  let loading;
  self.onmessage = async ({ data }) => {
    try {
      if (!loading) {
        loading = fetch("../search/search_index.json")
          .then((response) => {
            if (!response.ok) throw new Error("Search index unavailable");
            return response.json();
          })
          .then((index) => { documents = prepareDocuments(index.docs); });
      }
      await loading;
      self.postMessage({ id: data.id, results: rankDocuments(documents, data.query) });
    } catch {
      loading = null;
      self.postMessage({ id: data.id, error: true });
    }
  };
}
