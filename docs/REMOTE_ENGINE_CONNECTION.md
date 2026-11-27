# A-4 remote engine connection

Cloudflare remote-managed tunnel: `dominion-mobile-engine`
Public hostname: `https://engine.dominionai.kr`
Tunnel target: `http://127.0.0.1:4318` (authenticated local bridge).
Native DOMINION default: `127.0.0.1:4317`.

## Run on the computer that actually runs DOMINION

1. Start DOMINION with a strong existing `DOMINION_ADMIN_TOKEN` (32 or more printable ASCII characters). The same credential is entered in A-4. If no token has been configured, generate one locally and configure the engine before exposing it. Never commit it.
2. In a terminal on that computer, with the same environment variable set, run `python3 tools/remote/engine_bridge.py`. The bridge first probes the native API with and without the token. It refuses to start unless the native server rejects unauthenticated remote access and accepts the actual credential.
3. In the Cloudflare dashboard open the named tunnel, copy its platform-specific connector command and run it on the same computer. Keep the connector token private. No token is in this repo. The tunnel points to the bridge, not directly to the native engine.
4. In A-4 Settings > Engine, choose the DOMINION Cloud remote address, enter the engine administrator token, verify and connect. In DOMINION Cloud, sign in with Google and explicitly remember the connected engine. This uses the existing cloud account; it does not make a free-inference app token into an owner token.

The bridge binds only to loopback and preserves the external host, enforces the owner token, limits requests to native mobile API routes, and forwards actual responses including SSE. No independent AI harness. Run `cd tools/remote && python3 -m unittest -v` for transport tests.

The connector must be installed and running on the user's own PC. An inactive tunnel is not a successful end-to-end connection. Cloudflare Access is not enabled in the current account; this setup uses native owner authentication and the local bridge rather than pretending Access is configured. No existing website or inference worker is replaced. This is a single-owner route, not a multi-tenant engine registry or a public distribution default.
