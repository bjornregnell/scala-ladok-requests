# AGENTS.md

## Purpose

This is a single-file Scala 3 CLI tool (package `ladok`) that queries the Swedish university student administration system **Ladok** via its internal JSON API. It lets teachers look up student contact information (email, phone) and attested course results by name or Swedish personal identity number (personnummer), list course instances, and export course participant lists to CSV.

## How it works

1. The user copies their full `Cookie` header from an authenticated Firefox session on `start.ladok.se` pasted into `~/.ladok-cookie`.
2. The `Cookie` case class reads that file, extracts the `XSRF-TOKEN`, and provides auth headers for all requests via a Scala 3 `given`/`using` context parameter.
3. Results are printed to stdout (student search) or saved to CSV files in the `out/` directory (participant export).

## Tech stack

- **Scala 3.8.3** with **scala-cli** (no sbt/build tool project — directives are in the file header).
- **requests-scala** (`com.lihaoyi::requests:0.9.3`) for HTTP requests (GET and PUT).
- **os-lib** for filesystem access.
- **ujson** for JSON parsing.
- os-lib and ujson come from the `//> using toolkit 0.9.2` directive; requests-scala is an explicit `//> using dep`.

## Code structure

Everything lives in `ladok.scala` under `package ladok`:

- **`Cookie`** — case class holding cookie string + XSRF token, with `authHeaders`. A `given defaultCookie` reads from `~/.ladok-cookie`.
- **`Student`** — case class wrapping a Ladok JSON student record. Lazily fetches contact info (`Kontakt`, `Epost`, `Telefonnummer`) and attested course results (`Kursöversikt`). Has `showContact`/`showResultat`/`showKeys`/`showAll` formatting.
- **`Kurs`** — case class for a course instance (uid, kod, tillfälle, start, slut, namn).
- **`Abort`** — object with `ifNotOK`, `ifNotJSON`, and `apply` for error handling and exit.
- **`get`/`put`** — top-level functions for authenticated HTTP requests, using `Cookie` as a context parameter.
- **Extension methods** on `String` — `isKurskod`, `isEfternamnKommaFörnamn`, `toPersonnummer`.
- **Extension method** on `JSON` — `extractStudents`.

Publish directives live in a separate `publish.scala`.

## Running

```
Usage:

scala run ladok.scala -- --help                    # show usage
scala run ladok.scala -- --tillfälle <kurskod>     # list course instances 
scala run ladok.scala -- --deltagare <kurskod>     # export CSV for latest instance
scala run ladok.scala -- --deltagare <kurskod> <tillfälle1> <tillfälle2> ... # export CSV for specific instance(s)
scala run ladok.scala -- --resultat <search input> # print overview of result of all courses for each student 
scala run ladok.scala -- --kontakt <search input>  # print contact details for each student

<search input> can be one or more of these items
  <perssonnummer>
  '<förnamn> <efternamn>'
  '<efternamn>, <förnamn>' 
Names must be inside quotes. 
If name contains comma then efternamn comes before förnamn.
Wildcards like Svens* are supported for name search.

You can use personnummer in any below form:
  20101201-1234 101201-1234 1012011234 201012011234

Examples:
scala ladok.scala -- --kontakt "F* Regn*"             # name search (wildcards ok, surname last)
scala ladok.scala -- --kontakt "Regn*, F*"            # name with comma (surname first)
scala ladok.scala -- --kontakt YYYYMMDDCCCC           # personnummer search (several formats accepted)
scala ladok.scala -- --resultat "F* Regn*"            # name search (wildcards ok, surname last)
scala ladok.scala -- --resultat "Regn*, F*"           # name with comma (surname first)
scala ladok.scala -- --resultat YYYYMMDDCCCC          # personnummer search (several formats accepted)
scala ladok.scala -- --tillfälle EDAB05               # list course instances
scala ladok.scala -- --deltagare EDAB05               # export CSV with participants for latest instance
scala ladok.scala -- --deltagare EDAB05 P65BJ P55BG   # export CSV with participants for specific instances

Put the full Cookie header value from browser dev tools when you run and it will be saved into `~/.ladok-cookie`

More usage info and how to copy the cookie header is available in README here and when you run:
https://github.com/bjornregnell/scala-ladok-requests

```

Personnummer accepts formats like `20101201-1234`, `101201-1234`, `1012011234`, `201012011234`.

## Ladok API endpoints used

- **Student search:** `GET /gui/proxy/studentinformation/internal/student/filtrera`
- **Contact info:** `GET /gui/proxy/studentinformation/internal/student/{uid}/kontaktuppgifter`
- **Attested course results:** `GET /gui/proxy/resultat/internal/studentresultat/attesterade/student/{uid}`
- **Course lookup:** `GET /gui/proxy/resultat/internal/kurstillfalle/filtrera`
- **Participant CSV export:** `PUT /gui/proxy/studiedeltagande/internal/deltagare/kurstillfalle/export`

All under `https://start.ladok.se`.

## Key constraints

- **Authentication is cookie-based.** There is no OAuth flow; the cookie must be manually copied from a browser. Cookies expire, so the user refreshes the file periodically.
- **The API is internal/undocumented.** The endpoints are reverse-engineered from browser network traffic. Field names are in Swedish.
- **Single file.** Keep everything in `ladok.scala` unless there is a strong reason to split. Publish directives live in a separate `publish.scala`.
- **No secrets in the repo.** The cookie file lives at `~/.ladok-cookie` and must never be committed. CSV exports go to `out/` which is gitignored.
- **Published to Maven Central** as `se.bjornregnell::scala-ladok-requests`.

## Extending

When adding new Ladok queries, follow the existing pattern:
- For JSON endpoints: add a function that calls `get(url)` (with `Cookie` available as a `using` parameter) and parse the returned `ujson.Value`.
- For export/mutation endpoints: use `put(url, body)` with a `ujson.Obj` body.
- New API endpoints can be discovered via Firefox DevTools Network tab on `start.ladok.se`. The Ladok API uses Swedish field names throughout.
