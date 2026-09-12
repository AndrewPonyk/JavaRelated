"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type {
  AnalyticsSummary,
  BlockCategory,
  LayoutSuggestionSummary,
  OrganizationSummary,
  PageSummary,
  SiteDocument,
  SiteSummary
} from "@/domain/site";
import type { ApiResponse } from "@/types/api";

const blockTypes: BlockCategory[] = ["HERO", "CTA", "PRICING", "TESTIMONIALS", "FORM", "FAQ"];

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(init?.headers ?? {})
    }
  });
  const payload = (await response.json()) as ApiResponse<T>;

  if (!payload.ok) {
    throw new Error(payload.error.message);
  }

  return payload.data;
}

function emptyDocument(): SiteDocument {
  return { version: 1, blocks: [] };
}

function newBlock(type: BlockCategory) {
  return {
    id: `${type.toLowerCase()}-${crypto.randomUUID()}`,
    type,
    props: {
      headline: `${type[0]}${type.slice(1).toLowerCase()} block`,
      body: "Edit this block copy for the campaign.",
      buttonLabel: type === "FAQ" || type === "TESTIMONIALS" ? undefined : "Learn more",
      buttonHref: "#lead-form"
    }
  };
}

export function BuilderWorkspace() {
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
  const [activeOrgId, setActiveOrgId] = useState("");
  const [sites, setSites] = useState<SiteSummary[]>([]);
  const [activeSiteId, setActiveSiteId] = useState("");
  const [pages, setPages] = useState<PageSummary[]>([]);
  const [activePageId, setActivePageId] = useState("");
  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [suggestions, setSuggestions] = useState<LayoutSuggestionSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [newSiteName, setNewSiteName] = useState("Spring Launch");
  const [figmaJson, setFigmaJson] = useState('[{"id":"1:2","name":"Hero Frame","type":"FRAME"}]');

  const activeSite = sites.find((site) => site.id === activeSiteId) ?? null;
  const activePage = pages.find((page) => page.id === activePageId) ?? null;
  const activeDocument = activePage?.document ?? emptyDocument();

  const activeOrg = useMemo(
    () => organizations.find((organization) => organization.id === activeOrgId) ?? null,
    [organizations, activeOrgId]
  );

  async function loadOrganizations() {
    const orgs = await api<OrganizationSummary[]>("/api/organizations");

    if (orgs.length === 0) {
      const created = await api<OrganizationSummary>("/api/organizations", {
        method: "POST",
        body: JSON.stringify({ name: "Demo Marketing Team", slug: "demo-marketing" })
      });
      setOrganizations([created]);
      setActiveOrgId(created.id);
      return created.id;
    }

    setOrganizations(orgs);
    setActiveOrgId((current) => current || orgs[0].id);
    return orgs[0].id;
  }

  async function loadSites(organizationId: string) {
    const loadedSites = await api<SiteSummary[]>(`/api/sites?organizationId=${organizationId}`);
    setSites(loadedSites);
    setActiveSiteId((current) => current || loadedSites[0]?.id || "");
    return loadedSites[0]?.id || "";
  }

  async function loadPages(siteId: string) {
    if (!siteId) {
      setPages([]);
      setActivePageId("");
      return;
    }

    const loadedPages = await api<PageSummary[]>(`/api/sites/${siteId}/pages`);
    setPages(loadedPages);
    setActivePageId((current) => current || loadedPages[0]?.id || "");
  }

  async function loadAnalytics(organizationId: string, siteId?: string) {
    if (!organizationId) {
      return;
    }

    const query = new URLSearchParams({ organizationId });
    if (siteId) {
      query.set("siteId", siteId);
    }
    setAnalytics(await api<AnalyticsSummary>(`/api/analytics?${query.toString()}`));
  }

  async function loadSuggestions(siteId: string) {
    if (!siteId) {
      setSuggestions([]);
      return;
    }

    setSuggestions(await api<LayoutSuggestionSummary[]>(`/api/sites/${siteId}/suggestions`));
  }

  async function refreshAll() {
    setError(null);
    setIsLoading(true);
    try {
      const organizationId = activeOrgId || (await loadOrganizations());
      const siteId = await loadSites(organizationId);
      await loadPages(siteId);
      await loadAnalytics(organizationId, siteId);
      await loadSuggestions(siteId);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load workspace");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void refreshAll();
    // The initial workspace bootstrap runs once; selection-specific loading is handled below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!activeOrgId) {
      return;
    }

    void loadSites(activeOrgId)
      .then((siteId) => Promise.all([loadAnalytics(activeOrgId, siteId), loadSuggestions(siteId), loadPages(siteId)]))
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : "Unable to load organization"));
    // This effect is scoped to organization changes; site changes have a dedicated loader.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeOrgId]);

  useEffect(() => {
    if (!activeSiteId) {
      return;
    }

    void Promise.all([
      loadPages(activeSiteId),
      loadSuggestions(activeSiteId),
      activeOrgId ? loadAnalytics(activeOrgId, activeSiteId) : Promise.resolve()
    ]).catch((loadError) =>
      setError(loadError instanceof Error ? loadError.message : "Unable to load site")
    );
    // Organization changes reload the active site separately; this effect reacts to site selection.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSiteId]);

  async function createSite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!activeOrgId || !newSiteName.trim()) {
      return;
    }

    try {
      const slug = newSiteName
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/(^-|-$)/g, "");
      const site = await api<SiteSummary>("/api/sites", {
        method: "POST",
        body: JSON.stringify({ organizationId: activeOrgId, name: newSiteName, slug })
      });
      setSites((current) => [site, ...current]);
      setActiveSiteId(site.id);
      setNotice("Site created");
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Unable to create site");
    }
  }

  async function savePage(document: SiteDocument) {
    if (!activeSiteId || !activePage) {
      return;
    }

    try {
      const updated = await api<PageSummary>(`/api/sites/${activeSiteId}/pages/${activePage.id}`, {
        method: "PATCH",
        body: JSON.stringify({ document })
      });
      setPages((current) => current.map((page) => (page.id === updated.id ? updated : page)));
      setNotice("Page saved");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save page");
    }
  }

  async function addBlock(type: BlockCategory) {
    await savePage({
      version: activeDocument.version,
      blocks: [...activeDocument.blocks, newBlock(type)]
    });
  }

  async function updateBlock(blockId: string, field: string, value: string) {
    await savePage({
      version: activeDocument.version,
      blocks: activeDocument.blocks.map((block) =>
        block.id === blockId ? { ...block, props: { ...block.props, [field]: value } } : block
      )
    });
  }

  async function removeBlock(blockId: string) {
    await savePage({
      version: activeDocument.version,
      blocks: activeDocument.blocks.filter((block) => block.id !== blockId)
    });
  }

  async function publishSite() {
    if (!activeSiteId) {
      return;
    }

    try {
      const site = await api<SiteSummary>(`/api/sites/${activeSiteId}`, {
        method: "PATCH",
        body: JSON.stringify({ status: "PUBLISHED" })
      });
      setSites((current) => current.map((item) => (item.id === site.id ? site : item)));
      setNotice("Site published");
    } catch (publishError) {
      setError(publishError instanceof Error ? publishError.message : "Unable to publish site");
    }
  }

  async function recordLead() {
    if (!activeOrgId || !activeSiteId || !activePage) {
      return;
    }

    try {
      await api("/api/conversion-events", {
        method: "POST",
        body: JSON.stringify({
          organizationId: activeOrgId,
          siteId: activeSiteId,
          pageSlug: activePage.slug,
          eventName: "form_submit",
          source: "builder-preview",
          metadata: { path: "preview" }
        })
      });
      await loadAnalytics(activeOrgId, activeSiteId);
      setNotice("Conversion event recorded");
    } catch (eventError) {
      setError(eventError instanceof Error ? eventError.message : "Unable to record conversion");
    }
  }

  async function generateSuggestion() {
    if (!activeSiteId) {
      return;
    }

    try {
      const suggestion = await api<LayoutSuggestionSummary>(`/api/sites/${activeSiteId}/suggestions`, {
        method: "POST"
      });
      setSuggestions((current) => [suggestion, ...current]);
      setNotice("Layout suggestion generated");
    } catch (suggestionError) {
      setError(suggestionError instanceof Error ? suggestionError.message : "Unable to generate suggestion");
    }
  }

  async function importFigma() {
    if (!activeOrgId) {
      return;
    }

    try {
      const nodes = JSON.parse(figmaJson) as unknown;
      const page = await api<PageSummary>("/api/figma/import", {
        method: "POST",
        body: JSON.stringify({
          organizationId: activeOrgId,
          siteId: activeSiteId || undefined,
          name: "Imported Figma Page",
          nodes
        })
      });
      setPages((current) => [page, ...current]);
      setActivePageId(page.id);
      setNotice("Figma page imported");
    } catch (importError) {
      setError(importError instanceof Error ? importError.message : "Unable to import Figma selection");
    }
  }

  if (isLoading) {
    return <div className="rounded-lg border border-slate-200 bg-white p-6">Loading workspace...</div>;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[280px_minmax(0,1fr)_320px]">
      <aside className="space-y-4 rounded-lg border border-slate-200 bg-white p-4">
        <div>
          <label className="text-xs font-semibold uppercase text-slate-500" htmlFor="organization">
            Organization
          </label>
          <select
            className="mt-2 w-full rounded-md border border-slate-300 px-3 py-2"
            id="organization"
            value={activeOrgId}
            onChange={(event) => setActiveOrgId(event.target.value)}
          >
            {organizations.map((organization) => (
              <option key={organization.id} value={organization.id}>
                {organization.name}
              </option>
            ))}
          </select>
        </div>

        <form className="space-y-2" onSubmit={createSite}>
          <label className="text-xs font-semibold uppercase text-slate-500" htmlFor="site-name">
            New site
          </label>
          <input
            className="w-full rounded-md border border-slate-300 px-3 py-2"
            id="site-name"
            value={newSiteName}
            onChange={(event) => setNewSiteName(event.target.value)}
          />
          <button className="w-full rounded-md bg-signal px-3 py-2 text-sm font-semibold text-white" type="submit">
            Create site
          </button>
        </form>

        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase text-slate-500">Sites</p>
          {sites.map((site) => (
            <button
              className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
                site.id === activeSiteId
                  ? "border-signal bg-blue-50 text-signal"
                  : "border-slate-200 bg-white text-slate-700"
              }`}
              key={site.id}
              onClick={() => setActiveSiteId(site.id)}
              type="button"
            >
              <span className="block font-semibold">{site.name}</span>
              <span className="text-xs">{site.status.toLowerCase()}</span>
            </button>
          ))}
        </div>
      </aside>

      <section className="space-y-4">
        {(error || notice) && (
          <div
            className={`rounded-lg border p-3 text-sm ${
              error ? "border-red-200 bg-red-50 text-red-700" : "border-emerald-200 bg-emerald-50 text-emerald-800"
            }`}
          >
            {error || notice}
            <button className="ml-3 underline" onClick={() => { setError(null); setNotice(null); }} type="button">
              dismiss
            </button>
          </div>
        )}

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase text-slate-500">
                {activeOrg?.name ?? "Workspace"}
              </p>
              <h2 className="text-2xl font-semibold text-ink">{activeSite?.name ?? "Create a site"}</h2>
            </div>
            <div className="flex gap-2">
              <button className="rounded-md border border-slate-300 px-3 py-2 text-sm" onClick={recordLead} type="button">
                Track lead
              </button>
              <button className="rounded-md bg-ink px-3 py-2 text-sm font-semibold text-white" onClick={publishSite} type="button">
                Publish
              </button>
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <div className="mb-4 flex flex-wrap gap-2">
            {blockTypes.map((type) => (
              <button
                className="rounded-md border border-slate-300 px-3 py-2 text-xs font-semibold"
                key={type}
                onClick={() => addBlock(type)}
                type="button"
              >
                Add {type.toLowerCase()}
              </button>
            ))}
          </div>

          <div className="space-y-4">
            {activeDocument.blocks.map((block) => (
              <article className="rounded-lg border border-slate-200 p-4" key={block.id}>
                <div className="mb-3 flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">{block.type}</p>
                  <button className="text-sm text-red-600" onClick={() => removeBlock(block.id)} type="button">
                    Remove
                  </button>
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <input
                    className="rounded-md border border-slate-300 px-3 py-2"
                    value={(block.props.headline as string | undefined) ?? ""}
                    onChange={(event) => updateBlock(block.id, "headline", event.target.value)}
                    aria-label="Headline"
                  />
                  <input
                    className="rounded-md border border-slate-300 px-3 py-2"
                    value={(block.props.buttonLabel as string | undefined) ?? ""}
                    onChange={(event) => updateBlock(block.id, "buttonLabel", event.target.value)}
                    aria-label="Button label"
                  />
                  <textarea
                    className="min-h-24 rounded-md border border-slate-300 px-3 py-2 md:col-span-2"
                    value={(block.props.body as string | undefined) ?? ""}
                    onChange={(event) => updateBlock(block.id, "body", event.target.value)}
                    aria-label="Body copy"
                  />
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <aside className="space-y-4">
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="text-xs font-semibold uppercase text-slate-500">Analytics</p>
          <p className="mt-2 text-3xl font-semibold text-ink">{analytics?.totalEvents ?? 0}</p>
          <p className="text-sm text-slate-600">tracked events</p>
          <p className="mt-3 text-sm text-slate-600">
            Conversion rate: {Math.round((analytics?.conversionRate ?? 0) * 100)}%
          </p>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs font-semibold uppercase text-slate-500">Suggestions</p>
            <button className="rounded-md border border-slate-300 px-2 py-1 text-xs" onClick={generateSuggestion} type="button">
              Generate
            </button>
          </div>
          <div className="mt-3 space-y-3">
            {suggestions.map((suggestion) => (
              <article className="rounded-md bg-slate-50 p-3" key={suggestion.id}>
                <h3 className="text-sm font-semibold text-ink">{suggestion.title}</h3>
                <p className="mt-1 text-xs text-slate-600">{suggestion.rationale}</p>
                <button className="mt-2 text-xs font-semibold text-signal" onClick={() => savePage(suggestion.proposedDocument)} type="button">
                  Apply suggestion
                </button>
              </article>
            ))}
          </div>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="text-xs font-semibold uppercase text-slate-500">Figma import</p>
          <textarea
            className="mt-3 min-h-28 w-full rounded-md border border-slate-300 px-3 py-2 text-xs"
            value={figmaJson}
            onChange={(event) => setFigmaJson(event.target.value)}
          />
          <button className="mt-3 w-full rounded-md bg-coral px-3 py-2 text-sm font-semibold text-white" onClick={importFigma} type="button">
            Import selection
          </button>
        </div>
      </aside>
    </div>
  );
}
