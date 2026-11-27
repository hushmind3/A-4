# Mobile-first execution
A-4 now uses a local Kotlin harness when no explicit PC execution target is selected.
The existing UI, Korean services, character preferences, Room database, approvals and reminder scheduler are reused.
Cloudflare inference uses the existing scoped free-model session directly. PC tokens are not required or sent.
The bounded mobile core ports Korean bigram/BM25 retrieval ideas and round budgets from branch1 0ebc2d16.
It is NOT the full Rust SDK or a native Rust binary; versioned parity and mobile-native bindings are a later phase.
One model decision, at most one authorized tool, at most one final inference. Local reads use real state, and writes require explicit approval and persist real device records.
Available: text, current time, local persona memory search/save, local schedule list/create, official Korean portal catalog.
Not yet available in independent mode: live web search, arbitrary app navigation, full voice/video pipeline, large attachment processing, durable unattended AI tasks, shared memory sync, independent external provider selection. No fake tool results.
PC relay remains optional; automatic PC takeover removed. Explicit mode switch keeps PC binding separate. Existing cloud Worker unchanged by this change.
