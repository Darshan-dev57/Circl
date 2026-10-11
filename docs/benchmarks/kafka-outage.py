"""Stops the Kafka broker, books seats while it is down, starts it again and times how long
until every event left the outbox and became a notification. Uses only the Python standard library.

Needs the stack from `docker compose --profile app up -d --build`.
Usage: python3 docs/benchmarks/kafka-outage.py > docs/benchmarks/results/kafka-outage.txt
"""
import json
import subprocess
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

API = "http://localhost:8080/api/v1"
GUESTS = 20


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


def sql(query):
    out = subprocess.run(["docker", "exec", "circl-postgres", "psql", "-U", "circl", "-d", "circl", "-tA", "-c", query],
                         check=True, capture_output=True, text=True).stdout
    return int(out.strip())


host = signup("HOST")
starts = (datetime.now(timezone.utc) + timedelta(days=2)).isoformat()
activity = call("POST", "/activities", {"title": "Kafka outage check", "category": "COFFEE", "startsAt": starts,
                                        "durationMinutes": 60, "capacity": 50, "lat": 12.9352, "lng": 77.6245}, host)["id"]
guests = [signup("PARTICIPANT") for _ in range(GUESTS)]

subprocess.run(["docker", "pause", "circl-kafka"], check=True, capture_output=True)
print(f"broker paused; {GUESTS} joins while it is down")
for g in guests:
    call("POST", f"/activities/{activity}/join", None, g, {"Idempotency-Key": str(uuid.uuid4())})
time.sleep(5)
waiting = sql(f"SELECT count(*) FROM outbox_events WHERE aggregate_id = '{activity}' "
              f"AND type = 'ParticipantJoined' AND published_at IS NULL")
print(f"after 5 s with the broker down: {waiting} of {GUESTS} join events waiting in the outbox")

subprocess.run(["docker", "unpause", "circl-kafka"], check=True, capture_output=True)
back = time.monotonic()
print("broker back")
while True:
    left = sql(f"SELECT count(*) FROM outbox_events WHERE aggregate_id = '{activity}' AND published_at IS NULL")
    if left == 0:
        break
    if time.monotonic() - back > 120:
        print(f"FAILED: {left} events still unpublished after 120 s")
        raise SystemExit(1)
    time.sleep(0.5)
published = time.monotonic() - back
print(f"outbox empty {published:.1f} s after the broker came back")

notes = 0
deadline = time.monotonic() + 60
while time.monotonic() < deadline:
    notes = len([n for n in call("GET", "/me/notifications", None, host)
                 if activity in json.dumps(n)])
    if notes >= GUESTS:
        break
    time.sleep(0.5)
parked = sql(f"SELECT count(*) FROM outbox_events WHERE aggregate_id = '{activity}' AND failed_at IS NOT NULL")
print(f"host notifications for this activity: {notes} of {GUESTS}; parked events: {parked}")
print("RESULT:", "no events lost" if notes == GUESTS and parked == 0 else "EVENTS MISSING")
