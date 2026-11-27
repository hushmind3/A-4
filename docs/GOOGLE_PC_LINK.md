# Google-account native engine link (4317)

The deployed DOMINION Cloud Worker now includes the existing AssistantSocket relay,
scoped PC/mobile app approvals and native API route allowlist. A-4 uses native
Retrofit requests over bounded WebSocket RPC, rather than a second execution engine.

On the **PC running existing DOMINION** (port 4317), from a checkout of A-4:

```sh
python3 -m venv .a4-relay
.a4-relay/bin/python -m pip install -r tools/remote/requirements-cloud.txt
.a4-relay/bin/python tools/remote/cloud_engine_connector.py --register
```

Windows: use `.a4-relay\Scripts\python.exe` in place of `.a4-relay/bin/python`.
Use the same Google account when approving this PC. If the engine uses
DOMINION_ADMIN_TOKEN, set the same environment variable locally before running.
The connector does not store this engine token or expose it to Cloudflare.
The connector checks the actual local owner API and room API before registration.
After the first registration, run the same command without --register.
Use --revoke to remove the PC grant. The scoped PC grant lasts seven days;
revoke and re-register when expired. The token file is private on the PC.

In A-4: Settings > DOMINION Cloud > Google account > PC connection approval.
Existing inference-only sessions retain their scope. A separate explicit
assistant_remote grant on the same account is required to control the PC.
Select the online "DOMINION 4317" device. The first newly approved session
automatically connects if exactly one native device is online and no engine
is already connected. No 4318 bridge or inbound Cloudflare Tunnel required.
The prior tunnel configuration remains untouched as an independent fallback.

Transport constraints: 585 KB binary request/response cap, 165-second client
RPC timeout, native API only, periodic snapshots instead of SSE; large uploads
and audio require a future chunked transport. No automatic retries of writes.
True duplex LiveKit media still needs the native grant and running agent.
Push inbox and FCM server endpoints are deployed, but actual FCM delivery
requires Firebase project configuration and the A-4 Android integration.
The PC must run the connector; Google login by itself is not a running socket.
