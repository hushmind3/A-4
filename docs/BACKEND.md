# Backend integration

Source baseline: [DOMINION branch1](https://github.com/hushmind3/dominion/tree/0ebc2d1667982fae6a8396d3bb39c46af361c36a). Inspect the actual deployed version before connecting.

## Authorization

The fixed DOMINION Cloud gateway supports app pairing using a hash of a generated bearer, browser Google approval, status polling, shared quota/model catalogue and revocation. Its current app token has a seven-day lifetime; no automatic refresh contract was found. It does not authorize private engine access.

The engine connection settings accept an explicit HTTPS origin and a separate owner token, matching the existing `X-Dominion-Token` guard. Both credential stores use Android Keystore and AES-GCM; redirects and silent request replay are disabled. This is owner-controlled access, not device-scoped authorization or automatic Cloudflare tunnel discovery. Never expose an unauthenticated engine. Server switching preserves old local databases. No provider keys are embedded.

## Native engine routes

| Method | Path | Client behavior |
|---|---|---|
| GET | /v1/me, /v1/characters, /v1/conversations | Owner, agent and room projections |
| GET | /v1/conversations/{id}/messages | History; merge by server message ID |
| POST | /api/rooms | Create real room with existing agent, model pool and tools enabled |
| GET/POST | /api/rooms/{id} | Read/update room tool settings and model pool |
| POST | /api/rooms/{id}/messages | Durable message/job submission with clientMessageId and reasoningLanguage |
| GET | /api/providers, /api/providers/{id}/models | Existing configured model catalogue |
| GET | /api/tools | Existing available tool definitions and approval flags |
| POST | /api/skills/import-repo | Explicit user-confirmed import of Korean skill repository |
| POST | /v1/conversations/{id}/calls | Existing LiveKit session token |

The native submission endpoint returns a stored message ID and job ID and implements server-side duplicate protection for stable clientMessageId values. It does not use the older mobile text endpoint's synthetic acknowledgement. The client persists the job ID with the outgoing message and merges subsequent history. Acknowledgement means accepted, not completed. Full job-event restoration, cancellation and approval UI still require integration. Korean UI sends reasoningLanguage=ko; the server's existing agent system prompt and tool policies remain authoritative.

Existing engine owner settings are not globally changed. New mobile rooms default to tools enabled. Only the dedicated assistant room is initialized automatically on first send; other rooms require explicit configuration. Model pool changes require user action and are verified by a subsequent read. A Cloud model is only usable through the harness when configured as a provider.

## Korean skills

[NomaDamas/k-skill](https://github.com/NomaDamas/k-skill) has real Korean administrative, legal, travel, public-data and language-oriented skills; the inspected revision is c41a11703b0ea25b8c5c3c62bd0f06f31997f7f8. Many entries are instruction stubs requiring the external CLI, a hosted/self-hosted proxy or credentials. The native settings screen delegates import to DOMINION's existing skill importer. This is not proof that every skill can execute: dependencies, authorization, runtime skill selection and live provider results require verification. Do not bypass identity checks, final-submission approvals or service terms.

## Local history and media

The existing Room database stores messages, conversations and local records transactionally with background writes and legacy migration. SQL substring search is bounded. Full paging and job recovery are not implemented. Old prototype/direct-cloud files remain untouched but are not displayed through a separate direct-cloud chat interface.

Voice uses the existing LiveKit WSS session contract. A connected room does not establish that a voice agent is present. Video, interruption, real attachments, device permissions and background job notifications require further work. No live Android/server/cloud integration test was performed for this update.
