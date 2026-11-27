PRAGMA foreign_keys=off;
DROP TABLE IF EXISTS cloud_work_assets;
CREATE TABLE cloud_work_assets (
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
