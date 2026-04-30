# AGENTS.md

## Purpose

This is a single-file Scala 3 CLI tool that queries the Swedish university student administration system **Ladok** via its internal JSON API. It lets teachers look up student contact information (primarily email) by name or Swedish personal identity number (personnummer), and export course participant lists to CSV.

## How it works

1. The user copies their full `Cookie` header from an authenticated Firefox session on `start.ladok.se` into `~/.ladok-cookie`.
2. The tool reads that cookie file, extracts the `XSRF-TOKEN`, and uses both to make authenticated requests against Ladok's internal APIs.
3. Results are printed to stdout or saved to CSV files in the `out/` directory.

## Tech stack

- **Scala 3** with **scala-cli** (no sbt/build tool project — directives are in the file header).
- **requests-scala** (`com.lihaoyi::requests`) for HTTP requests (GET and PUT).
- **os-lib** for filesystem access.
- **ujson** for JSON parsing.
- os-lib and ujson come from the `//> using toolkit 0.9.2` directive; requests-scala is an explicit `//> using dep`.

## Running

```
scala-cli ladok.scala -- "First*" "Last*"     # name search (wildcards ok)
scala-cli ladok.scala -- YYYYMMDDCCCC          # personnummer search
scala-cli ladok.scala -- --kurskod EDAB05 --adresslista deltagare.csv  # export CSV
```

## Ladok API endpoints used

- **Student search:** `GET /gui/proxy/studentinformation/internal/student/filtrera`
- **Contact info:** `GET /gui/proxy/studentinformation/internal/student/{uid}/kontaktuppgifter`
- **Course lookup:** `GET /gui/proxy/resultat/internal/kurstillfalle/filtrera`
- **Participant CSV export:** `PUT /gui/proxy/studiedeltagande/internal/deltagare/kurstillfalle/export`

All under `https://start.ladok.se`.

## Key constraints

- **Authentication is cookie-based.** There is no OAuth flow; the cookie must be manually copied from a browser. Cookies expire, so the user refreshes the file periodically.
- **The API is internal/undocumented.** The endpoints are reverse-engineered from browser network traffic. Field names are in Swedish.
- **Single file.** Keep everything in `ladok.scala` unless there is a strong reason to split. Publish directives live in a separate `project.scala`.
- **No secrets in the repo.** The cookie file lives at `~/.ladok-cookie` and must never be committed. CSV exports go to `out/` which is gitignored.
- **Published to Maven Central** as `se.bjornregnell::scala-ladok-requests`.

## Extending

When adding new Ladok queries, follow the existing pattern:
- For JSON endpoints: add a function that calls `get(cookies, xsrf, url)` and parse the returned `ujson.Value`.
- For export/mutation endpoints: use `put(cookies, xsrf, url, body)` with a `ujson.Obj` body.
- New API endpoints can be discovered via Firefox DevTools Network tab on `start.ladok.se`. The Ladok API uses Swedish field names throughout.
