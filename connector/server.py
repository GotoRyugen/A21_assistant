"""A21 relay + MCP tools. No arbitrary shell/code execution is exposed."""
import hmac
import os
import sqlite3
import time
import uuid
from contextlib import closing
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field
from mcp.server.fastmcp import FastMCP

DB_PATH = os.getenv("A21_DB_PATH", "./a21.sqlite3")
MCP_TOKEN = os.getenv("A21_MCP_TOKEN", "")
DEVICE_TOKEN = os.getenv("A21_DEVICE_TOKEN", "")
MAX_PENDING = 20
ACTIONS = {"STATUS", "LIST_CAPABILITIES", "OPEN_WIFI", "OPEN_BLUETOOTH",
           "OPEN_BATTERY", "OPEN_APPLICATIONS", "OPEN_SETTINGS"}

def ready():
    if not MCP_TOKEN or not DEVICE_TOKEN:
        raise RuntimeError("A21_MCP_TOKEN and A21_DEVICE_TOKEN must be configured.")

def db():
    Path(DB_PATH).parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("""CREATE TABLE IF NOT EXISTS commands (
        id TEXT PRIMARY KEY, action TEXT NOT NULL, created REAL NOT NULL,
        expires REAL NOT NULL, status TEXT NOT NULL, result TEXT
    )""")
    conn.commit()
    return conn

def enqueue(action: str):
    if action not in ACTIONS:
        raise ValueError("Action not allowed")
    now = time.time()
    command_id = str(uuid.uuid4())
    with closing(db()) as conn:
        pending = conn.execute("SELECT COUNT(*) FROM commands WHERE status='pending'").fetchone()[0]
        if pending >= MAX_PENDING:
            raise RuntimeError("Queue is full; try again later.")
        conn.execute("INSERT INTO commands VALUES (?, ?, ?, ?, 'pending', NULL)",
                     (command_id, action, now, now + 300))
        conn.commit()
    return {"command_id": command_id, "action": action,
            "status": "pending", "expires_at": int(now + 300)}

def bearer(value: Optional[str], expected: str):
    if not value or not hmac.compare_digest(value, "Bearer " + expected):
        raise HTTPException(status_code=401, detail="Unauthorized")

mcp = FastMCP("A21 Assistant")

@mcp.tool()
def a21_status() -> dict:
    """Check whether A21 has recently contacted its relay."""
    with closing(db()) as conn:
        row = conn.execute("SELECT MAX(created) AS last_seen FROM commands WHERE status='completed'").fetchone()
    return {"relay": "online", "phone_last_result_at": row["last_seen"],
            "note": "This does not prove the phone is currently online."}

@mcp.tool()
def a21_list_capabilities() -> dict:
    """List the limited actions currently supported by the A21 relay contract."""
    return {"capabilities": ["status", "list_capabilities", "open_wifi",
        "open_bluetooth", "open_battery", "open_applications", "open_settings"],
        "local_confirmation_required": True,
        "screen_reading": False, "memory_sync": False}

@mcp.tool()
def a21_open_setting(setting: str) -> dict:
    """Ask A21 to open one approved Android settings page. A21 must confirm locally."""
    mapping = {"wifi": "OPEN_WIFI", "bluetooth": "OPEN_BLUETOOTH",
               "battery": "OPEN_BATTERY", "applications": "OPEN_APPLICATIONS",
               "settings": "OPEN_SETTINGS"}
    key = setting.strip().lower()
    if key not in mapping:
        return {"accepted": False, "error": "Allowed values: wifi, bluetooth, battery, applications, settings"}
    return enqueue(mapping[key])

@mcp.tool()
def a21_cancel_command(command_id: str) -> dict:
    """Cancel a command that has not yet been picked up by the phone."""
    with closing(db()) as conn:
        cur = conn.execute("UPDATE commands SET status='cancelled' WHERE id=? AND status='pending'",
                           (command_id,))
        conn.commit()
    return {"cancelled": cur.rowcount == 1}

app = FastAPI(title="A21 secure relay", version="0.1.0")

@app.middleware("http")
async def protect_mcp(request: Request, call_next):
    if request.url.path == "/mcp" or request.url.path.startswith("/mcp/"):
        if not MCP_TOKEN or not hmac.compare_digest(
                request.headers.get("authorization", ""), "Bearer " + MCP_TOKEN):
            return __import__("fastapi").responses.JSONResponse(
                status_code=401, content={"detail": "Unauthorized"})
    return await call_next(request)

class PollResponse(BaseModel):
    command: Optional[dict] = None

class ResultBody(BaseModel):
    command_id: str = Field(min_length=36, max_length=36)
    status: str = Field(pattern="^(completed|rejected|failed)$")
    result: str = Field(default="", max_length=1000)

@app.get("/health")
def health():
    return {"ok": True, "service": "A21 relay"}

@app.post("/device/poll", response_model=PollResponse)
def device_poll(authorization: Optional[str] = Header(default=None)):
    bearer(authorization, DEVICE_TOKEN)
    now = time.time()
    with closing(db()) as conn:
        conn.execute("UPDATE commands SET status='expired' WHERE status='pending' AND expires<=?", (now,))
        row = conn.execute("SELECT * FROM commands WHERE status='pending' ORDER BY created LIMIT 1").fetchone()
        if row is None:
            conn.commit()
            return {"command": None}
        conn.execute("UPDATE commands SET status='delivered' WHERE id=? AND status='pending'", (row["id"],))
        conn.commit()
    return {"command": {"version": 1, "id": row["id"], "action": row["action"],
        "issued_at": int(row["created"] * 1000), "expires_at": int(row["expires"] * 1000),
        "requires_confirmation": row["action"] not in {"STATUS", "LIST_CAPABILITIES"}}}

@app.post("/device/result")
def device_result(body: ResultBody, authorization: Optional[str] = Header(default=None)):
    bearer(authorization, DEVICE_TOKEN)
    with closing(db()) as conn:
        cur = conn.execute("UPDATE commands SET status=?, result=? WHERE id=? AND status='delivered'",
                           (body.status, body.result, body.command_id))
        conn.commit()
    if cur.rowcount != 1:
        raise HTTPException(status_code=409, detail="Unknown, expired, or already handled command.")
    return {"saved": True}

# Streamable HTTP MCP endpoint at /mcp. The host must provide HTTPS.
app.mount("/mcp", mcp.streamable_http_app())
