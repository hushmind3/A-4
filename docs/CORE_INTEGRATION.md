# Mobile core integration status

The mobile runtime is the primary execution environment; see [MOBILE_FIRST.md](MOBILE_FIRST.md).
Google authentication and Cloudflare model inference use the existing scoped app session.
A free inference credential is not a PC owner credential.

Optional PC-specific work can use the existing scoped WebSocket relay or an explicitly
configured owner-authenticated HTTPS endpoint. See [GOOGLE_PC_LINK.md](GOOGLE_PC_LINK.md).
Native transcript synchronization, SSE on supported direct endpoints, credential-scoped
job monitoring, and the existing PC-dependent voice adapter remain available in PC mode.
No dedicated inbound bridge is required. Model-only PC inference is a separate feature
and is not implemented by the optional remote-work relay.

Independent realtime voice/video, external inference providers, cross-device memory sync,
and verified background push still need their own implementation and integration tests.
