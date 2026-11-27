-- Durable cloud work sessions. Mobile is only the conversation/approval surface.
CREATE TABLE IF NOT EXISTS cloud_work_sessions (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id),
  client_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('research','browser','phone','admin')),
  title TEXT NOT NULL,
  request TEXT NOT NULL,
  metadata TEXT NOT NULL DEFAULT '{}',
  status TEXT NOT NULL CHECK(status IN ('queued','running','waiting','done','failed','cancelled')),
  waiting_for TEXT,
  result TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(account_id,client_id)
);
CREATE INDEX IF NOT EXISTS cloud_work_account ON cloud_work_sessions(account_id,updated_at);
CREATE INDEX IF NOT EXISTS cloud_work_queue ON cloud_work_sessions(status,updated_at);
CREATE TABLE IF NOT EXISTS cloud_work_events (
  id TEXT PRIMARY KEY,
  work_id TEXT NOT NULL REFERENCES cloud_work_sessions(id),
  role TEXT NOT NULL CHECK(role IN ('system','assistant','user')),
  text TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS cloud_work_events_work ON cloud_work_events(work_id,created_at);
