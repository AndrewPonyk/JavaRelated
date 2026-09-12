"""Tests: custom XSS engine — canary detection + context classification."""

from __future__ import annotations

import html as html_mod

import httpx
from app.services.xss_scanner import InjectionContext, XssScanner

TARGET = "http://t/search?q=original&lang=en"


def _echo_route(escape: bool = False, wrapper: str = "<div>{v}</div>"):
    """Reflect every query-param value back, optionally HTML-escaped."""

    def handler(request: httpx.Request) -> httpx.Response:
        def render(value: str) -> str:
            return html_mod.escape(value, quote=False) if escape else value

        body = wrapper.replace("{v}", " ".join(render(v) for v in request.url.params.values()))
        return httpx.Response(200, text=body)

    return handler


class TestContextClassification:
    def test_script_context(self):
        body = "<script>var q = 'REFL';</script>"
        ctx = XssScanner._classify_context(body, body.find("REFL"))
        assert ctx == InjectionContext.SCRIPT

    def test_attribute_context(self):
        body = '<input value="REFL" />'
        ctx = XssScanner._classify_context(body, body.find("REFL"))
        assert ctx == InjectionContext.ATTRIBUTE

    def test_comment_context(self):
        body = "<!-- REFLECTED HERE -->"
        ctx = XssScanner._classify_context(body, body.find("REFLECTED"))
        assert ctx == InjectionContext.COMMENT

    def test_html_body_context(self):
        body = "<p>Hello REFL</p>"
        ctx = XssScanner._classify_context(body, body.find("REFL"))
        assert ctx == InjectionContext.HTML_BODY

    def test_closed_script_does_not_leak_into_next_tag(self):
        body = "<script>var a=1;</script><div>REFL</div>"
        ctx = XssScanner._classify_context(body, body.find("REFL"))
        assert ctx == InjectionContext.HTML_BODY


class TestUrlRewrite:
    def test_param_replaced_others_kept(self):
        rewritten = XssScanner._rewrite_url(TARGET, "q", "INJ")
        assert "q=INJ" in rewritten
        assert "lang=en" in rewritten
        assert "original" not in rewritten


class TestCorpus:
    def test_loads_all_contexts(self):
        corpus = XssScanner().load_corpus()
        assert len(corpus[InjectionContext.HTML_BODY]) > 3
        assert len(corpus[InjectionContext.ATTRIBUTE]) >= 1
        assert len(corpus[InjectionContext.SCRIPT]) >= 1
        # every template carries the canary placeholder
        for templates in corpus.values():
            assert all("{c}" in t for t in templates)


class TestProbing:
    async def test_raw_reflection_is_a_hit(self, respx_mock, monkeypatch):
        respx_mock.route().mock(side_effect=_echo_route())
        scanner = XssScanner()
        # pin the template so the classified context is deterministic
        monkeypatch.setattr(scanner, "_payloads", lambda: ["<b>{c}</b>"])
        hits = await scanner.probe_url(TARGET)
        assert len(hits) == 2  # q and lang both reflect raw
        hit = hits[0]
        assert hit.payload == "<b>{c}</b>"  # template recorded
        assert hit.context == InjectionContext.HTML_BODY
        assert hit.evidence_excerpt  # response window captured

    async def test_html_escaped_reflection_is_not_a_hit(self, respx_mock, monkeypatch):
        # The live corpus deliberately includes quote-free vectors (e.g. the
        # base64 data-URI) that survive entity encoding — a real finding, not
        # a control failure. This test pins an angle-bracket template so the
        # "encoding defeats the payload" mechanism is verified in isolation.
        respx_mock.route().mock(side_effect=_echo_route(escape=True))
        scanner = XssScanner()
        monkeypatch.setattr(scanner, "_payloads", lambda: ["<b>{c}</b>"])
        hits = await scanner.probe_url(TARGET)
        assert hits == []  # encoding works — control holds

    async def test_no_reflection_is_not_a_hit(self, respx_mock):
        respx_mock.route().mock(return_value=httpx.Response(200, text="<p>nothing</p>"))
        assert await XssScanner().probe_url(TARGET) == []

    async def test_network_error_is_swallowed(self, respx_mock):
        respx_mock.route().mock(side_effect=httpx.ConnectError("down"))
        assert await XssScanner().probe_url(TARGET) == []

    async def test_probe_urls_aggregates_across_urls(self, respx_mock):
        reflecting = "http://t/a?q=1"

        def selective(request: httpx.Request) -> httpx.Response:
            if request.url.path.startswith("/a"):
                return httpx.Response(200, text=f"<div>{request.url.params.get('q')}</div>")
            return httpx.Response(200, text="<p>static</p>")

        respx_mock.route().mock(side_effect=selective)
        hits = await XssScanner().probe_urls([reflecting, "http://t/static?x=1"])
        assert len(hits) == 1
        assert hits[0].url == reflecting
        assert hits[0].param == "q"
