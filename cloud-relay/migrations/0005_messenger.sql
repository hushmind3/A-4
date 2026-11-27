-- DOMINION Cloud consumer messenger. Account identity comes from the
-- authenticated app session; no client-supplied account ID is trusted.
CREATE TABLE IF NOT EXISTS messenger_profiles (
  account_id TEXT PRIMARY KEY REFERENCES accounts(id),
  handle TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS messenger_friends (
  owner_id TEXT NOT NULL REFERENCES accounts(id),
  peer_id TEXT NOT NULL REFERENCES accounts(id),
  status TEXT NOT NULL CHECK(status IN ('pending','accepted','blocked')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id,peer_id),
  CHECK(owner_id<>peer_id)
);
CREATE INDEX IF NOT EXISTS messenger_friends_peer ON messenger_friends(peer_id,status);
CREATE TABLE IF NOT EXISTS messenger_threads (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK(kind IN ('direct','group')),
  title TEXT NOT NULL,
  created_by TEXT NOT NULL REFERENCES accounts(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS messenger_members (
  thread_id TEXT NOT NULL REFERENCES messenger_threads(id),
  account_id TEXT NOT NULL REFERENCES accounts(id),
  joined_at INTEGER NOT NULL,
  last_read_at INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(thread_id,account_id)
);
CREATE INDEX IF NOT EXISTS messenger_members_account ON messenger_members(account_id);
CREATE TABLE IF NOT EXISTS messenger_messages (
  id TEXT PRIMARY KEY,
  thread_id TEXT NOT NULL REFERENCES messenger_threads(id),
  sender_id TEXT NOT NULL REFERENCES accounts(id),
  client_id TEXT NOT NULL,
  text TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  UNIQUE(sender_id,client_id)
);
CREATE INDEX IF NOT EXISTS messenger_messages_thread ON messenger_messages(thread_id,created_at,id);
CREATE INDEX IF NOT EXISTS messenger_messages_account ON messenger_messages(sender_id,created_at);
