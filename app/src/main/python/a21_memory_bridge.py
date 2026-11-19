"""Thin Android bridge to the upstream memoryaicm engine.

The engine and its SQLite files run inside A21 Assistant's private app storage.
No HTTP server, cloud account, or external LLM is required for memory operations.
"""
import json
from pathlib import Path

from memoryaicm.agent import MemoryAgent
from memoryaicm.config import Settings
from memoryaicm.llm.stub import StubBackend

_agent = None
_session = None


def initialize(home):
    global _agent, _session
    if _agent is not None:
        return status()
    settings = Settings(
        root=Path(home),
        backend="stub",
        embedder="hash",
        autonomy=2,
        selfcheck_on_start=True,
    )
    _agent = MemoryAgent(settings=settings, backend=StubBackend())
    _session = _agent.start_session("a21-android")
    return status()


def remember(user_text):
    if _agent is None:
        raise RuntimeError("La mémoire A21 n'est pas initialisée.")
    report = _agent.remember(str(user_text), _session)
    return json.dumps({
        "summary": report.summary(),
        "written": len(report.written),
        "updated": len(report.merged),
        "reinforced": len(report.reinforced),
        "pending": len(report.queued),
        "forgotten": len(report.forgotten),
    }, ensure_ascii=False)


def recall(query):
    if _agent is None:
        raise RuntimeError("La mémoire A21 n'est pas initialisée.")
    notes, prefs = _agent.recall_notes(str(query), k=8, session=_session)
    def item(f):
        return {
            "id": f.id,
            "text": f.txt,
            "subject": f.subject,
            "kind": str(f.kind),
            "version": f.ver,
        }
    return json.dumps({
        "notes": [item(f) for f in notes],
        "preferences": [item(f) for f in prefs],
    }, ensure_ascii=False)


def forget(query):
    if _agent is None:
        raise RuntimeError("La mémoire A21 n'est pas initialisée.")
    report = _agent.forget(str(query), session=_session)
    return json.dumps({
        "summary": report.summary(),
        "forgotten": report.ids,
        "cascade": report.cascade,
    }, ensure_ascii=False)


def status():
    if _agent is None:
        return json.dumps({"ready": False}, ensure_ascii=False)
    result = _agent.status()
    result["ready"] = True
    return json.dumps(result, ensure_ascii=False, default=str)


def close():
    global _agent, _session
    if _agent is not None:
        _agent.close()
    _agent = None
    _session = None
    return "closed"
