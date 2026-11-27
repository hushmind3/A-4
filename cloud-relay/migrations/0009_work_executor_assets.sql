PRAGMA foreign_keys=off;
ALTER TABLE cloud_work_sessions RENAME TO cloud_work_sessions_old;
ALTER TABLE cloud_work_events RENAME TO cloud_work_events_old;
CREATE TABLE cloud_work_sessions (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id),
  client_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('research','browser','phone','admin','document','image')),
  title TEXT NOT NULL,
  request TEXT NOT NULL,
  metadata TEXT NOT NULL DEFAULT '{}',
  status TEXT NOT NULL CHECK(status IN ('queued','running','waiting','done','failed','cancelled')),
  waiting_for TEXT,
  result TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  scheduled_at INTEGER NOT NULL DEFAULT 0,
  repeat_rule TEXT NOT NULL DEFAULT 'none',
  condition TEXT,
  deadline INTEGER,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_run INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  lease_until INTEGER NOT NULL DEFAULT 0,
  UNIQUE(account_id,client_id)
);
INSERT INTO cloud_work_sessions(id,account_id,client_id,kind,title,request,metadata,status,waiting_for,result,created_at,updated_at,scheduled_at,repeat_rule,condition,deadline,attempts,next_run,last_error)
SELECT id,account_id,client_id,kind,title,request,metadata,status,waiting_for,result,created_at,updated_at,scheduled_at,repeat_rule,condition,deadline,attempts,next_run,last_error FROM cloud_work_sessions_old;
CREATE TABLE cloud_work_events (
  id TEXT PRIMARY KEY,
  work_id TEXT NOT NULL REFERENCES cloud_work_sessions(id),
  role TEXT NOT NULL CHECK(role IN ('system','assistant','user')),
  text TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
INSERT INTO cloud_work_events(id,work_id,role,text,created_at)
SELECT id,work_id,role,text,created_at FROM cloud_work_events_old;
DROP TABLE cloud_work_events_old;
DROP TABLE cloud_work_sessions_old;
CREATE INDEX IF NOT EXISTS cloud_work_account ON cloud_work_sessions(account_id,updated_at);
CREATE INDEX IF NOT EXISTS cloud_work_queue ON cloud_work_sessions(status,next_run);
CREATE INDEX IF NOT EXISTS cloud_work_lease ON cloud_work_sessions(status,lease_until);
CREATE INDEX IF NOT EXISTS cloud_work_events_work ON cloud_work_events(work_id,created_at);
CREATE TABLE IF NOT EXISTS cloud_work_assets (
  id TEXT PRIMARY KEY,
  work_id TEXT NOT NULL REFERENCES cloud_work_sessions(id),
  account_id TEXT NOT NULL REFERENCES accounts(id),
  kind TEXT NOT NULL,
  mime TEXT NOT NULL,
  name TEXT NOT NULL,
  kv_key TEXT NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS cloud_work_assets_work ON cloud_work_assets(work_id,created_at);
PRAGMA foreign_keys=on;
