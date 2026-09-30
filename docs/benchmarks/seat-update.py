"""Join to live seat update: opens the seat stream (SSE) of an activity, joins it, and times how long until
the new seat count arrives. The update goes join -> outbox -> Kafka -> seat listener -> SSE.
Needs a running backend on localhost:8080 with Postgres, Redis and Kafka. Standard library only.
Usage: python3 docs/benchmarks/seat-update.py > docs/benchmarks/results/seat-update.txt
"""
import json
import statistics
import threading
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

API = "http://localhost:8080/api/v1"
JOINS = 10


def call(method, path, body=None, token=None, headers=None):
    req = urllib.request.Request(API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=30) as res:
        text = res.read().decode()
        return json.loads(text) if text else None


def signup(role):
    email = f"bench-{uuid.uuid4().hex[:10]}@circl.dev"
    return call("POST", "/auth/signup", {"email": email, "password": "bench-pass-123",
                                         "name": "Bench User", "role": role})["accessToken"]


host = signup("HOST")
starts = (datetime.now(timezone.utc) + timedelta(days=2)).isoformat()
activity = call("POST", "/activities", {"title": "Seat update check", "category": "COFFEE", "startsAt": starts,
                                        "durationMinutes": 60, "capacity": 50, "lat": 12.9352, "lng": 77.6245}, host)["id"]

seen = {}  # seats taken -> time the event arrived


def listen():
    with urllib.request.urlopen(f"{API}/activities/{activity}/seats", timeout=120) as stream:
        for raw in stream:
            line = raw.decode().strip()
            if line.startswith("data:"):
                data = json.loads(line[5:])
                seen.setdefault(data.get("seatsTaken"), time.monotonic())


threading.Thread(target=listen, daemon=True).start()
time.sleep(2)

delays = []
for i in range(1, JOINS + 1):
    guest = signup("PARTICIPANT")
    sent = time.monotonic()
    call("POST", f"/activities/{activity}/join", None, guest, {"Idempotency-Key": str(uuid.uuid4())})
    while i not in seen and time.monotonic() - sent < 30:
        time.sleep(0.02)
    if i in seen:
        delays.append(seen[i] - sent)
        print(f"join {i}: seat update after {seen[i] - sent:.2f} s")
    else:
        print(f"join {i}: no update within 30 s")
    time.sleep(1)

if delays:
    print(f"min {min(delays):.2f} s, median {statistics.median(delays):.2f} s, max {max(delays):.2f} s "
          f"over {len(delays)} joins")
