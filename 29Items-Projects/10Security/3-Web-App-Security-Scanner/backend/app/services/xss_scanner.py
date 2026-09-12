"""Custom XSS payload engine (in-process, fully async).

Detection strategy (canary-based):
  1. every payload template carries a {c} canary placeholder,
  2. per probe we substitute a fresh random canary,
  3. reflection = canary present in response,
  4. HIT = the full payload-with-canary survives raw (metacharacters intact),
  5. reflection context classified from bytes surrounding the canary.

Authorized-testing tool: only ever aimed at targets that already passed
the scope gate (app.api.deps.assert_target_allowed).
"""

from __future__ import annotations

import asyncio
import random
import re
import secrets
import string
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import httpx

_PAYLOAD_DIR = Path(__file__).parent / "payloads"
_CANARY_ALPHABET = string.ascii_lowercase + string.digits


class InjectionContext(StrEnum):
    HTML_BODY = "html_body"
    ATTRIBUTE = "attribute"
    SCRIPT = "script"
    COMMENT = "comment"
    UNKNOWN = "unknown"


@dataclass
class XssHit:
    url: str
    param: str
    payload: str  # template as loaded from the corpus
    context: InjectionContext
    evidence_excerpt: str  # capped response window around the reflection


class XssScanner:
    """Stateless scanner — safe to share across scans."""

    def __init__(self, concurrency: int = 5, max_payloads_per_param: int = 14) -> None:
        self._semaphore = asyncio.Semaphore(concurrency)
        self._max_payloads = max_payloads_per_param
        self._corpus: dict[InjectionContext, list[str]] | None = None

    # ── corpus ─────────────────────────────────────────────────────────

    def load_corpus(self) -> dict[InjectionContext, list[str]]:
        """Load payload templates grouped by their file's context name."""
        if self._corpus is not None:
            return self._corpus
        context_by_file = {
            "html_body.txt": InjectionContext.HTML_BODY,
            "attribute.txt": InjectionContext.ATTRIBUTE,
            "script.txt": InjectionContext.SCRIPT,
            "uri.txt": InjectionContext.UNKNOWN,  # misc probes
        }
        corpus: dict[InjectionContext, list[str]] = {}
        for filename, context in context_by_file.items():
            path = _PAYLOAD_DIR / filename
            if not path.exists():
                continue
            templates = [
                line.strip()
                for line in path.read_text(encoding="utf-8").splitlines()
                if line.strip() and not line.lstrip().startswith("#") and "{c}" in line
            ]
            corpus.setdefault(context, []).extend(templates)
        self._corpus = corpus
        return corpus

    def _payloads(self) -> list[str]:
        corpus = self.load_corpus()
        flat = [p for templates in corpus.values() for p in templates]
        # bounded, deterministic-ish rotation per scan
        return (
            flat[: self._max_payloads]
            if len(flat) <= self._max_payloads
            else (random.sample(flat, self._max_payloads))
        )

    # ── probing ────────────────────────────────────────────────────────

    async def probe_url(
        self,
        url: str,
        params: list[str] | None = None,
        client: httpx.AsyncClient | None = None,
    ) -> list[XssHit]:
        """Probe query params of `url` with the corpus; first hit wins per param.

        `params`: explicit param names; when omitted, taken from the query string.
        """
        own_client = client is None
        client = client or httpx.AsyncClient(timeout=15.0, follow_redirects=False)
        try:
            split = urlsplit(url)
            query_params = [k for k, _ in parse_qsl(split.query)]
            targets = params if params is not None else query_params
            tasks = [self._probe_param(client, url, p) for p in targets]
            results = await asyncio.gather(*tasks)
            return [hit for hits in results for hit in hits]
        finally:
            if own_client:
                await client.aclose()

    async def probe_urls(
        self, urls: list[str], concurrency: int = 4, per_url_limit: int = 8
    ) -> list[XssHit]:
        """Probe many discovered URLs (from ZAP spider), bounded."""
        sem = asyncio.Semaphore(concurrency)
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=False) as client:

            async def _one(u: str) -> list[XssHit]:
                async with sem:
                    return await self.probe_url(u, client=client)

            results = await asyncio.gather(*[_one(u) for u in urls[: per_url_limit * concurrency]])
        return [hit for hits in results for hit in hits]

    async def _probe_param(self, client: httpx.AsyncClient, url: str, param: str) -> list[XssHit]:
        hits: list[XssHit] = []
        for template in self._payloads():
            async with self._semaphore:
                hit = await self._probe_once(client, url, param, template)
            if hit is not None:
                hits.append(hit)
                break  # first verified hit per param is enough
        return hits

    async def _probe_once(
        self, client: httpx.AsyncClient, url: str, param: str, template: str
    ) -> XssHit | None:
        canary = "z" + "".join(secrets.choice(_CANARY_ALPHABET) for _ in range(9))
        marked = template.replace("{c}", canary)
        test_url = self._rewrite_url(url, param, marked)
        try:
            resp = await client.get(test_url)
        except httpx.HTTPError:
            return None  # network flake — not a finding
        body = resp.text
        idx = body.find(canary)
        if idx < 0:
            return None  # not reflected at all
        if marked not in body:
            return None  # reflected but encoded/sanitized — control works
        context = self._classify_context(body, idx)
        return XssHit(
            url=url,
            param=param,
            payload=template,
            context=context,
            evidence_excerpt=self._excerpt(body, idx, len(marked)),
        )

    # ── helpers ────────────────────────────────────────────────────────

    @staticmethod
    def _rewrite_url(url: str, param: str, value: str) -> str:
        split = urlsplit(url)
        pairs = dict(parse_qsl(split.query))
        pairs[param] = value
        return urlunsplit((split.scheme, split.netloc, split.path, urlencode(pairs), ""))

    @staticmethod
    def _classify_context(body: str, idx: int) -> InjectionContext:
        """Classify from bytes BEFORE the reflection (block-aware ordering)."""
        before = body[:idx]
        last_open = max(before.rfind("<script"), before.rfind("<SCRIPT"))
        last_close = max(before.rfind("</script"), before.rfind("</SCRIPT"))
        if last_open > last_close:
            return InjectionContext.SCRIPT
        if before.rfind("<!--") > before.rfind("-->"):
            return InjectionContext.COMMENT
        if re.search(r"=\s*[\"'][^\"']*$", before):
            return InjectionContext.ATTRIBUTE
        if re.search(r">\s*[^<]*$", before):
            return InjectionContext.HTML_BODY
        return InjectionContext.UNKNOWN

    @staticmethod
    def _excerpt(body: str, idx: int, length: int, window: int = 120, cap: int = 512) -> str:
        start = max(0, idx - window)
        excerpt = body[start : idx + length + window]
        return excerpt[:cap]
