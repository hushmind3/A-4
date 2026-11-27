-- Multi-user encrypted durable server work; apply this migration to Cloud D1.
CREATE TABLE IF NOT EXISTS cloud_agent_jobs(
 id TEXT PRIMARY KEY,account_id TEXT NOT NULL,client_id TEXT NOT NULL,
 prompt_enc TEXT NOT NULL,checkpoint_enc TEXT,result_enc TEXT,
 status TEXT NOT NULL CHECK(status IN ('queued','running','done','failed','cancelled')),
 steps INTEGER NOT NULL DEFAULT 0,failures INTEGER NOT NULL DEFAULT 0,error_code TEXT,
 lease_until INTEGER NOT NULL DEFAULT 0,next_run INTEGER NOT NULL,
 created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
 UNIQUE(account_id,client_id)
);
CREATE INDEX IF NOT EXISTS cloud_agent_queue ON cloud_agent_jobs(status,next_run,lease_until);
CREATE INDEX IF NOT EXISTS cloud_agent_owner ON cloud_agent_jobs(account_id,created_at);
