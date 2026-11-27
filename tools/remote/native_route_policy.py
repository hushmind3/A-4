"""Native PC RPC allowlist; no listener or public reverse proxy."""
import re
from urllib.parse import urlsplit

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
        r"/api/(memories|settings/owner-profile|oauth/providers|mcp|rooms|approvals|providers|tools|skills|voice/status)",
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
