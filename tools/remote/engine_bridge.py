#!/usr/bin/env python3
"""Authenticated, loopback-only transport for A-4. No inference or job execution here."""
import argparse
import hmac
import http.client
import json
import os
import re
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

HOST = "engine.dominionai.kr"
MAX_BODY = 25 * 1024 * 1024
ID = r"[A-Za-z0-9_.:@-]{1,256}"
# Only A-4 native API routes. No static web UI, desktop OAuth callback,
# arbitrary reverse proxy, developer endpoints or remote worker ingress.
ROUTES = {
    "GET": [
        r"/v1/(me|characters|conversations|tasks|schedules|memories|friends|friends/requests|connections|explore|vault|credits/transactions|inbox)",
        r"/v1/conversations/"+ID+r"(/messages)?",
        r"/v1/mobile/(capabilities|peers|messenger/threads)",
        r"/v1/mobile/peers/"+ID+r"/messages",
        r"/v1/mobile/messenger/threads/"+ID+r"/(messages|attachments/"+ID+r")",
        r"/v1/mobile/operations/"+ID,
        r"/api/(events|memories|settings/owner-profile|oauth/providers|mcp|rooms|approvals|providers|tools|skills|voice/status)",
        r"/api/rooms/"+ID+r"(/state|/messages)?",
        r"/api/(jobs|executions|skills)/"+ID,
        r"/api/executions/"+ID+r"/operations",
        r"/api/sessions/"+ID+r"/messages",
        r"/api/attachments/"+ID+r"/content",
        r"/api/providers/"+ID+r"/models",
    ],
    "POST": [
        r"/api/(memories|settings/owner-profile|mcp|browser/action|approvals|attachments|rooms|providers|skills/import-repo|voice/transcribe|voice/speak)",
        r"/api/rooms/"+ID+r"(/reset|/messages)?",
        r"/api/(oauth/"+ID+r"/open|jobs/"+ID+r"/cancel|skills/"+ID+r")",
        r"/v1/mobile/(realtime/calls|operations|voice/aligned)",
        r"/v1/mobile/operations/"+ID+r"/decision",
        r"/v1/mobile/messenger/threads/"+ID+r"/attachments",
        r"/v1/conversations/"+ID+r"/(messages|calls)",
        r"/v1/friends/requests/"+ID+r"/accept",
        r"/v1/connections/"+ID+r"/(connect|disconnect)",
    ],
    "PATCH": [r"/v1/me"],
    "DELETE": [r"/api/(memories|oauth|rooms)/"+ID],
}
PATTERNS = {method: [re.compile(x) for x in paths] for method, paths in ROUTES.items()}

def allowed(method, target):
    try:
        parts = urlsplit(target)
        path = parts.path
        if parts.scheme or parts.netloc or parts.fragment or not target.startswith("/"):
            return False
        if "%" in path or "\\" in path or "//" in path or any(x in (".", "..") for x in path.split("/")):
            return False
        if len(target) > 8192 or any(ord(c) < 32 or ord(c) > 126 for c in target):
            return False
        return any(p.fullmatch(path) for p in PATTERNS.get(method, ()))
    except ValueError:
        return False

def valid_token(token):
    return len(token) >= 32 and len(token) <= 4096 and all(33 <= ord(c) <= 126 for c in token) and not token.startswith("dapp_")

def assert_engine(port, token):
    # The engine must itself enforce remote owner authentication. Fail closed
    # even if a different branch/version is listening on the chosen port.
    for credential in (None, token):
        conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
        try:
            headers = {"Host": HOST, "Accept": "application/json"}
            if credential:
                headers["X-Dominion-Token"] = credential
            conn.request("GET", "/v1/me", headers=headers)
            response = conn.getresponse()
            raw = response.read(65537)
            if credential is None:
                if response.status not in (401, 403):
                    raise RuntimeError("Native engine did not reject unauthenticated remote access")
            elif response.status != 200 or len(raw) > 65536 or not json.loads(raw).get("data", {}).get("id"):
                raise RuntimeError("Native engine rejected configured owner credential")
        finally:
            conn.close()

class Bridge(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    def __init__(self, port, engine_port, token):
        self.engine_port = engine_port
        self.token = token
        super().__init__(("127.0.0.1", port), Handler)

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "DOMINION-Bridge"
    sys_version = ""
    def setup(self):
        super().setup()
        self.connection.settimeout(30)

    def log_message(self, *_):
        # Never log user text, query strings, tokens or attachment names.
        pass

    def deny(self, status, message):
        self.close_connection = True
        body = json.dumps({"error": message}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def handle_expect_100(self):
        self.deny(417, "Unsupported request expectation")
        return False

    def forward(self):
        self.close_connection = True
        if self.headers.get_all("Host") != [HOST]:
            return self.deny(403, "Invalid host")
        tokens = self.headers.get_all("X-Dominion-Token") or []
        if len(tokens) != 1 or not valid_token(tokens[0]) or not hmac.compare_digest(tokens[0], self.server.token):
            return self.deny(401, "Owner authentication required")
        if not allowed(self.command, self.path):
            return self.deny(404, "Not an A-4 native API route")
        if self.headers.get_all("Transfer-Encoding"):
            return self.deny(400, "Fixed request length required")
        lengths = self.headers.get_all("Content-Length") or ["0"]
        if len(lengths) != 1 or not re.fullmatch(r"[0-9]{1,9}", lengths[0]):
            return self.deny(400, "Invalid request length")
        size = int(lengths[0])
        if size > MAX_BODY:
            return self.deny(413, "Request too large")
        if self.command in ("GET", "DELETE") and size:
            return self.deny(400, "Unexpected request body")
        body = self.rfile.read(size) if size else None
        if size and len(body) != size:
            return self.deny(400, "Incomplete request body")
        # Keep the external Host; never impersonate the engine's loopback owner.
        headers = {"Host": HOST, "X-Dominion-Token": self.server.token}
        for name in ("Accept", "Content-Type", "X-Client-Version", "X-Client-Platform"):
            value = self.headers.get(name)
            if value is not None:
                headers[name] = value
        upstream = http.client.HTTPConnection("127.0.0.1", self.server.engine_port, timeout=330)
        started = False
        try:
            upstream.request(self.command, self.path, body=body, headers=headers)
            response = upstream.getresponse()
            # Never follow or forward an external redirect with owner credentials.
            if 300 <= response.status < 400:
                return self.deny(502, "Native API returned redirect")
            self.send_response(response.status)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.send_header("X-Content-Type-Options", "nosniff")
            for name in ("Content-Type", "Content-Disposition", "Content-Encoding"):
                value = response.getheader(name)
                if value is not None:
                    self.send_header(name, value)
            self.end_headers()
            started = True
            # read1 forwards SSE as it arrives; http.client removes upstream
            # chunk framing and the downstream response is close-delimited.
            while True:
                chunk = response.read1(65536)
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()
        except (OSError, http.client.HTTPException):
            if not started:
                self.deny(502, "DOMINION native engine unavailable")
        finally:
            upstream.close()

    do_GET = do_POST = do_PATCH = do_DELETE = forward

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine-port", type=int, default=4317)
    parser.add_argument("--port", type=int, default=4318)
    args = parser.parse_args()
    if not (1 <= args.engine_port <= 65535 and 1 <= args.port <= 65535 and args.engine_port != args.port):
        parser.error("Invalid ports")
    token = os.environ.get("DOMINION_ADMIN_TOKEN", "")
    if not valid_token(token):
        parser.error("Set the engine's DOMINION_ADMIN_TOKEN (at least 32 characters) locally")
    assert_engine(args.engine_port, token)
    with Bridge(args.port, args.engine_port, token) as server:
        print("Authenticated A-4 bridge ready on loopback port", args.port, flush=True)
        server.serve_forever()

if __name__ == "__main__":
    main()
