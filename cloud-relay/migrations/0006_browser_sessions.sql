-- Per-account browser login state. Cookie values are encrypted at rest and
-- are never returned by the metadata API or placed in model prompts.
CREATE TABLE IF NOT EXISTS browser_sessions (
  account_id TEXT NOT NULL REFERENCES accounts(id),
  host TEXT NOT NULL,
  cookie_enc TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(account_id,host)
);
CREATE INDEX IF NOT EXISTS browser_sessions_account ON browser_sessions(account_id,updated_at);
