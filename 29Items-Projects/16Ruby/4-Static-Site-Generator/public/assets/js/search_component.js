(function () {
  const root = document.querySelector("[data-search-component]");
  if (!root) return;

  const input = root.querySelector("[data-search-component] input, #site-search");
  const status = root.querySelector("[data-search-status]");
  const results = root.querySelector("[data-search-results]");

  let controller;
  let currentQuery = "";

  function setStatus(message) {
    status.textContent = message;
  }

  function renderResults(items) {
    results.innerHTML = "";

    if (items.length === 0) {
      setStatus("No results");
      return;
    }

    setStatus(`${items.length} result${items.length === 1 ? "" : "s"}`);

    for (const item of items) {
      const li = document.createElement("li");
      const link = document.createElement("a");
      const summary = document.createElement("p");
      const tags = document.createElement("span");

      link.href = item.url;
      appendHighlighted(link, `${item.title} (${item.version})`, currentQuery);
      summary.textContent = item.summary || item.keywords.join(", ");
      tags.className = "search-tags";
      tags.textContent = (item.tags || []).join(", ");

      li.append(link, summary, tags);
      results.append(li);
    }
  }

  async function fetchResults(query) {
    currentQuery = query;
    if (query.length > 80) {
      setStatus("Search query is too long");
      results.innerHTML = "";
      return;
    }

    if (controller) controller.abort();
    controller = new AbortController();

    setStatus("Loading...");

    try {
      const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`, {
        headers: { Accept: "application/json" },
        signal: controller.signal
      });

      if (!response.ok) throw new Error(`Search failed with ${response.status}`);

      const payload = await response.json();
      renderResults(payload.results || []);
    } catch (error) {
      if (error.name === "AbortError") return;
      fetchStaticResults(query);
    }
  }

  function appendHighlighted(element, text, query) {
    element.textContent = "";
    if (!query) {
      element.textContent = text;
      return;
    }

    const lowerText = text.toLowerCase();
    const lowerQuery = query.toLowerCase();
    let cursor = 0;
    let index = lowerText.indexOf(lowerQuery);

    while (index !== -1) {
      element.append(document.createTextNode(text.slice(cursor, index)));
      const mark = document.createElement("mark");
      mark.textContent = text.slice(index, index + query.length);
      element.append(mark);
      cursor = index + query.length;
      index = lowerText.indexOf(lowerQuery, cursor);
    }

    element.append(document.createTextNode(text.slice(cursor)));
  }

  async function fetchStaticResults(query) {
    try {
      const response = await fetch("/search/index.json", { headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(`Static search failed with ${response.status}`);
      const records = await response.json();
      const normalized = query.toLowerCase();
      const filtered = normalized
        ? records.filter((record) => {
            return [
              record.title,
              record.summary,
              (record.keywords || []).join(" "),
              (record.tags || []).join(" ")
            ].join(" ").toLowerCase().includes(normalized);
          })
        : records;
      renderResults(filtered.slice(0, 10));
    } catch (error) {
      setStatus("Search is unavailable");
      results.innerHTML = "";
    }
  }

  input.addEventListener("input", function (event) {
    fetchResults(event.target.value.trim());
  });

  input.addEventListener("keydown", function (event) {
    if (event.key !== "Enter") return;
    const firstLink = results.querySelector("a");
    if (firstLink) firstLink.click();
  });

  fetchResults("");
})();
