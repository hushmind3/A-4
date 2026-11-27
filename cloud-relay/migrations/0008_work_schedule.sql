ALTER TABLE cloud_work_sessions ADD COLUMN scheduled_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cloud_work_sessions ADD COLUMN repeat_rule TEXT NOT NULL DEFAULT 'none';
ALTER TABLE cloud_work_sessions ADD COLUMN condition TEXT;
ALTER TABLE cloud_work_sessions ADD COLUMN deadline INTEGER;
ALTER TABLE cloud_work_sessions ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cloud_work_sessions ADD COLUMN next_run INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cloud_work_sessions ADD COLUMN last_error TEXT;
CREATE INDEX IF NOT EXISTS cloud_work_due ON cloud_work_sessions(status,next_run);
