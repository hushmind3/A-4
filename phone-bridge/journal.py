"""Private, single-account phone adapter journal. Never replay a dispatch."""
import os
import sqlite3
import time
from contextlib import contextmanager

os.umask(0o077)
PATH = os.environ.get("DOMINION_PHONE_DB", "./phone-calls.sqlite3")
TERMINAL = {"reported", "ended", "failed", "cancelled", "unknown", "expired"}
ACTIVE = ("queued", "dialing", "connected", "cancel_requested")

@contextmanager
def connection():
    c = sqlite3.connect(PATH, timeout=10)
    c.row_factory = sqlite3.Row
    try:
        c.execute("PRAGMA journal_mode=WAL")
        c.execute("""CREATE TABLE IF NOT EXISTS instructions(
            call_id TEXT NOT NULL, seq INTEGER NOT NULL, text TEXT NOT NULL,
            created INTEGER NOT NULL, consumed INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(call_id,seq))""")
        c.execute("""CREATE TABLE IF NOT EXISTS utterances(
            call_id TEXT NOT NULL, seq INTEGER NOT NULL, speaker TEXT NOT NULL,
            text TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(call_id,seq))""")
        c.execute("""CREATE TABLE IF NOT EXISTS calls(
            id TEXT PRIMARY KEY, number TEXT NOT NULL, goal TEXT NOT NULL,
            status TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL,
            answered INTEGER NOT NULL DEFAULT 0, consent INTEGER NOT NULL DEFAULT 0,
            summary TEXT, evidence TEXT, reason TEXT)""")
        yield c
        c.commit()
    except BaseException:
        c.rollback()
        raise
    finally:
        c.close()

def add_utterance(call_id, speaker, text):
    if speaker not in ("ai", "callee"):
        raise ValueError("invalid_speaker")
    clean = (text or "").strip()
    if not (1 <= len(clean) <= 800):
        return False
    with connection() as c:
        seq = c.execute("SELECT COALESCE(MAX(seq),0)+1 FROM utterances WHERE call_id=?",
                        (call_id,)).fetchone()[0]
        try:
            c.execute("INSERT INTO utterances(call_id,seq,speaker,text,created) VALUES(?,?,?,?,?)",
                      (call_id, seq, speaker, clean, int(time.time())))
            return True
        except sqlite3.IntegrityError:
            return False

def add_instruction(call_id, text):
    clean = (text or "").strip()
    if not (1 <= len(clean) <= 500):
        return False
    with connection() as c:
        seq = c.execute("SELECT COALESCE(MAX(seq),0)+1 FROM instructions WHERE call_id=?",
                        (call_id,)).fetchone()[0]
        if seq > 50:
            return False
        c.execute("INSERT INTO instructions(call_id,seq,text,created) VALUES(?,?,?,?)",
                  (call_id, seq, clean, int(time.time())))
        return True

def pending_instructions(call_id):
    with connection() as c:
        rows = c.execute("SELECT seq,text FROM instructions WHERE call_id=? AND consumed=0 ORDER BY seq LIMIT 10",
                         (call_id,)).fetchall()
        return [dict(row) for row in rows]

def consume_instruction(call_id, seq):
    with connection() as c:
        return c.execute("UPDATE instructions SET consumed=1 WHERE call_id=? AND seq=? AND consumed=0",
                         (call_id, seq)).rowcount == 1

def utterances(call_id):
    with connection() as c:
        rows = c.execute("SELECT seq,speaker,text,created FROM utterances WHERE call_id=? ORDER BY seq",
                         (call_id,)).fetchall()
        return [dict(row) for row in rows]

def get(call_id):
    with connection() as c:
        row = c.execute("SELECT * FROM calls WHERE id=?", (call_id,)).fetchone()
        if not row:
            return None
        result = dict(row)
        result["utterances"] = [dict(x) for x in c.execute(
            "SELECT seq,speaker,text,created FROM utterances WHERE call_id=? ORDER BY seq",
            (call_id,)).fetchall()]
        return result

def create(call_id, number, goal):
    now = int(time.time())
    with connection() as c:
        c.execute("BEGIN IMMEDIATE")
        row = c.execute("SELECT * FROM calls WHERE id=?", (call_id,)).fetchone()
        if row:
            if (row["number"], row["goal"]) != (number, goal):
                raise ValueError("request_id_conflict")
            return False
        c.execute("UPDATE calls SET status='expired', updated=?, reason='worker_not_available' "
                  "WHERE status='queued' AND created<?", (now, now-60))
        # A crashed worker cannot be safely retried. Carrier max_call_duration is
        # enforced independently. Unknown outcomes require operator review.
        c.execute("UPDATE calls SET status='unknown',updated=?,reason='worker_lost_no_replay' "
                  "WHERE status IN ('dialing','connected','cancel_requested') AND created<?", (now,now-300))
        if c.execute("SELECT 1 FROM calls WHERE status IN ('queued','dialing','connected','cancel_requested')").fetchone():
            raise ValueError("active_call_exists")
        if c.execute("SELECT count(*) FROM calls WHERE created>?", (now-86400,)).fetchone()[0] >= 10:
            raise ValueError("daily_call_limit")
        if c.execute("SELECT 1 FROM calls WHERE created>?", (now-60,)).fetchone():
            raise ValueError("call_cooldown")
        c.execute("INSERT INTO calls(id,number,goal,status,created,updated) VALUES(?,?,?,'queued',?,?)",
                  (call_id,number,goal,now,now))
        return True

def transition(call_id, expected, status, **fields):
    assert expected and set(fields) <= {"answered","consent","summary","evidence","reason"}
    with connection() as c:
        columns = ["status=?", "updated=?"] + [key+"=?" for key in fields]
        args = [status,int(time.time()),*fields.values(),call_id,*expected]
        marks = ",".join("?" for _ in expected)
        return c.execute("UPDATE calls SET "+",".join(columns)+" WHERE id=? AND status IN ("+marks+")",args).rowcount == 1

def claim(call_id):
    with connection() as c:
        now=int(time.time())
        return c.execute("UPDATE calls SET status='dialing',updated=? WHERE id=? AND status='queued' AND created>=?",
                         (now,call_id,now-60)).rowcount == 1

def room(call_id):
    return "dominion-phone-"+call_id
