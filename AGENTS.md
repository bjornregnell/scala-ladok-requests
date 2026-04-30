# AGENTS.md

## Purpose

This is a single-file Scala 3 CLI tool that queries the Swedish university student administration system **Ladok** via its internal JSON API. It lets teachers look up student contact information (primarily email) by name or Swedish personal identity number (personnummer).

## How it works

1. The user copies their full `Cookie` header from an authenticated Firefox session on `start.ladok.se` into `~/.ladok-cookie`.
2. The tool reads that cookie file, extracts the `XSRF-TOKEN`, and uses both to make authenticated GET requests against Ladok's internal student information API.
3. Results are printed to stdout.

## Tech stack

- **Scala 3** with **scala-cli** (no sbt/build tool project — directives are in the file header).
- **sttp** (`quick` backend) for HTTP requests.
- **os-lib** for filesystem access.
- **ujson** for JSON parsing.
- These come from the `//> using toolkit 0.9.2` directive.

## Running

```
scala-cli ladok.scala -- "First*" "Last*"     # name search (wildcards ok)
scala-cli ladok.scala -- YYYYMMDDCCCC          # personnummer search
```

## Key constraints

- **Authentication is cookie-based.** There is no OAuth flow; the cookie must be manually copied from a browser. Cookies expire, so the user refreshes the file periodically.
- **The API is internal/undocumented.** The base URL (`/gui/proxy/studentinformation/internal/student`) is reverse-engineered from browser network traffic. Field names are in Swedish.
- **Single file.** Keep everything in `ladok.scala` unless there is a strong reason to split. This is intentionally a small, self-contained script.
- **No secrets in the repo.** The cookie file lives at `~/.ladok-cookie` and must never be committed.

## Extending

When adding new Ladok queries, follow the existing pattern: add a function that calls `get(cookies, xsrf, url)` with the appropriate endpoint and query parameters, then parse the returned `ujson.Value`. The Ladok API uses Swedish field names throughout.
