# Security

Please report a vulnerability privately through GitHub's "Report a vulnerability" (Security tab) instead of opening a public issue.

## Threat model

| Asset | Threat | Defence |
|---|---|---|
| Seats | Overbooking through concurrent joins | Conditional `UPDATE ... WHERE seats_taken + n <= capacity`, `CHECK (seats_taken <= capacity)`, race tests |
| Seats | Duplicate join from a retried request | `Idempotency-Key` stored with the response in the same transaction, `UNIQUE(activity_id, user_id)` |
| Accounts | Password theft from a DB leak | BCrypt hashes, nothing reversible stored |
| Accounts | Stolen refresh token | Stored as SHA-256, rotated on every use, reuse revokes all of the user's tokens |
| Accounts | Stolen access token | 15 minute lifetime |
| Accounts | Email enumeration on login | Same message and one BCrypt check whether or not the email exists |
| Other users' data | IDOR (editing or checking in for someone else) | Ownership checked in the service for every `{id}`; tests for 403 |
| Check-in | Sharing a screenshot of the host's code | HMAC-signed code valid 60 s, only joined participants can use it, one check-in per participant |
| Location privacy | Finding someone's exact spot from "free now" | Coordinates rounded to ~1 km before storing, distance returned only as buckets, search rate limit and teleport check |
| Stored XSS | Script in a description | Jsoup strips all HTML before saving |
| SQL injection | Crafted filters | Only bound parameters, no string-built SQL |
| Secrets | Keys committed to Git | `CIRCL_JWT_SECRET` and `CIRCL_CHECKIN_SECRET` come from the environment; the app refuses to start with a key under 32 bytes |
