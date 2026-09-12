"""Deliberately vulnerable target for e2e tests — DO NOT EXPOSE.

Stdlib-only on purpose (no deps, tiny image). Two sinks:

- ``/?q=...``   reflects the query raw into HTML body → reflected XSS
- ``/item?id=`` single-quote input yields a fake MySQL error string →
  error-based SQL-injection signal for sqlmap

Everything else is inert. Started only via ``docker compose --profile e2e``.
"""

from __future__ import annotations

import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

logging.basicConfig(level=logging.INFO, format="%(asctime)s vuln-lab %(message)s")

PAGE = """<!doctype html>
<html><head><title>Vuln Lab</title></head>
<body>
<h1>Vulnerable Lab</h1>
<p><a href="/?q=hello">search</a> <a href="/item?id=1">item 1</a></p>
<div id="result">{reflected}</div>
<div id="item">{item}</div>
</body></html>"""

SQL_ERROR = (
    "You have an error in your SQL syntax; check the manual that corresponds "
    "to your MySQL server version for the right syntax to use near ''''' "
    "at line 1: SELECT * FROM items WHERE id = '{value}'"
)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802 - http.server API
        url = urlparse(self.path)
        query = parse_qs(url.query)

        if url.path in ("", "/"):
            body = PAGE.format(
                reflected=query.get("q", [""])[0],  # raw reflection — the XSS sink
                item=query.get("id", [""])[0],
            ).encode()
            status, ctype = 200, "text/html; charset=utf-8"
        elif url.path == "/item":
            value = query.get("id", [""])[0]
            if "'" in value:
                body = SQL_ERROR.format(value=value).encode()
                status, ctype = 500, "text/plain; charset=utf-8"
            else:
                body = f'{{"id": "{value}", "name": "widget"}}'.encode()
                status, ctype = 200, "application/json"
        else:
            body = b"not found"
            status, ctype = 404, "text/plain; charset=utf-8"

        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: object) -> None:
        logging.info("%s %s", self.address_string(), fmt % args)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
