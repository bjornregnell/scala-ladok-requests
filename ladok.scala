//> using scala 3.8.3
//> using toolkit 0.9.2
//> using dep com.lihaoyi::requests:0.9.3

/** Query Ladok for student info or export course participant lists.
  *
  * Search by name or personnummer:
  *   `scala-cli ladok.scala -- "Firstnam*" "Lastnam*"`
  *   `scala-cli ladok.scala -- YYYYMMDDCCCC`
  *
  * Export participant list to CSV:
  *   `scala-cli ladok.scala -- --kurskod EDAB05 --adresslista deltagare.csv`
  */
package ladok

val LadokProxy = "https://start.ladok.se/gui/proxy"
val StudentBase = s"$LadokProxy/studentinformation/internal/student"
lazy val outDir = 
  if !os.exists(os.pwd / "out") then os.makeDir(os.pwd / "out")
  os.pwd / "out"

/** Read the full cookie string and extract XSRF token from ~/.ladok-cookie */
def readCookieFile(): (String, String) =
  val cookieFile = os.home / ".ladok-cookie"
  if !os.exists(cookieFile) then
    sys.error(
      "~/.ladok-cookie not found.\n" +
      "In Firefox DevTools → Network → click an XHR request → Headers tab\n" +
      "Copy the full Cookie: header value into ~/.ladok-cookie"
    )
  val cookies = os.read(cookieFile).replaceAll("\\s+", " ").trim
  val xsrf = cookies.split(";").map(_.trim)
    .find(_.startsWith("XSRF-TOKEN="))
    .map(_.stripPrefix("XSRF-TOKEN="))
    .getOrElse(sys.error("XSRF-TOKEN not found in cookie string"))
  (cookies, xsrf)

def authHeaders(cookies: String, xsrf: String) = Map(
  "Cookie" -> cookies,
  "X-XSRF-TOKEN" -> xsrf,
  "X-Requested-With" -> "XMLHttpRequest",
  "Accept" -> "application/json",
  "Content-Type" -> "application/json",
)

def get(cookies: String, xsrf: String, url: String): ujson.Value =
  val resp = requests.get(url, headers = authHeaders(cookies, xsrf), check = false)
  if resp.statusCode != 200 then
    sys.error(s"HTTP ${resp.statusCode}: ${resp.text().take(500)}")
  val body = resp.text()
  if !body.trim.startsWith("{") && !body.trim.startsWith("[") then
    sys.error(s"Expected JSON but got (first 500 chars):\n${body.take(500)}")
  ujson.read(body)

def put(cookies: String, xsrf: String, url: String, body: ujson.Value): requests.Response =
  val resp = requests.put(
    url,
    headers = authHeaders(cookies, xsrf),
    data = body.render(),
    check = false,
  )
  if resp.statusCode != 200 then
    sys.error(s"HTTP ${resp.statusCode}: ${resp.text().take(500)}")
  resp

def searchByPnr(cookies: String, xsrf: String, pnr: String): ujson.Value =
  get(cookies, xsrf, s"$StudentBase/filtrera?personnummer=$pnr&page=1&limit=25")

def searchByName(cookies: String, xsrf: String, fornamn: String, efternamn: String): ujson.Value =
  get(cookies, xsrf, s"$StudentBase/filtrera?fornamn=$fornamn&efternamn=$efternamn&page=1&limit=25&orderby=EFTERNAMN_ASC&orderby=FORNAMN_ASC&orderby=PERSONNUMMER_ASC")

def getKontakt(cookies: String, xsrf: String, uid: String): ujson.Value =
  get(cookies, xsrf, s"$StudentBase/$uid/kontaktuppgifter")

def searchKurstillfalle(cookies: String, xsrf: String, kurskod: String): ujson.Value =
  get(cookies, xsrf, s"$LadokProxy/resultat/internal/kurstillfalle/filtrera?kurskod=$kurskod&page=1&limit=100&orderby=KURSBENAMNING_ASC&orderby=KURSKOD_ASC&orderby=START_DATUM_DESC&orderby=KURSTILLFALLESKOD_ASC")

def exportDeltagare(cookies: String, xsrf: String, uid: String): String =
  val body = ujson.Obj(
    "utbildningstillfalleUID" -> ujson.Arr(uid),
    "deltagaretillstand" -> ujson.Arr("EJ_PABORJAD", "REGISTRERAD", "AVKLARAD"),
    "orderby" -> ujson.Arr("EFTERNAMN_ASC", "FORNAMN_ASC", "PERSONNUMMER_ASC"),
  )
  val resp = put(cookies, xsrf, s"$LadokProxy/studiedeltagande/internal/deltagare/kurstillfalle/export", body)
  val text = resp.text()
  // Ladok returns CSV wrapped in a JSON string
  try ujson.read(text).str catch case _: Exception => text

/** Try to extract email from the kontaktuppgifter JSON. */
def extractEmail(kontakt: ujson.Value): String =
  val json = kontakt.obj
  val candidates = Seq("Epostadress", "Email", "Epost", "epostadress", "email")
  candidates.find(json.contains).map(json(_).str).getOrElse:
    s"Email field not found. Full response:\n${kontakt.render(indent = 2)}"

@main def Main(args: String*): Unit =
  if args.isEmpty then
    println("Usage:")
    println("  scala-cli ladok.scala -- <personnummer>")
    println("  scala-cli ladok.scala -- <fornamn> <efternamn>")
    println("  scala-cli ladok.scala -- --kurskod <kod> --adresslista <fil.csv>")
    println()
    println("Wildcards like Regn* are supported for name search.")
    println("Put the full Cookie header value from Firefox DevTools into ~/.ladok-cookie")
    sys.exit(1)

  val (cookies, xsrf) = readCookieFile()
  val a = args.toIndexedSeq

  val kurskodIdx = a.indexOf("--kurskod")
  val adresslistaIdx = a.indexOf("--adresslista")

  if kurskodIdx >= 0 || adresslistaIdx >= 0 then
    if kurskodIdx < 0 || adresslistaIdx < 0 then
      sys.error("Both --kurskod and --adresslista must be specified.")
    val kurskod = a(kurskodIdx + 1)
    val filnamn = a(adresslistaIdx + 1)

    println(s"Searching for course $kurskod...")
    val result = searchKurstillfalle(cookies, xsrf, kurskod)
    val tillfallen = result("Resultat").arr
    if tillfallen.isEmpty then
      println(s"No course instance found for $kurskod.")
      sys.exit(1)

    val tillfalle = tillfallen(0)
    val uid = tillfalle("Uid").str
    val kod = tillfalle("TillfallesKod").str
    val start = tillfalle("Startdatum").str
    val slut = tillfalle("Slutdatum").str
    val namn = tillfalle("Utbildningsinstans")("Benamning").arr
      .find(_("Sprakkod").str == "sv").map(_("Text").str).getOrElse("?")
    println(s"Found: $kurskod $namn ($kod, $start -- $slut)")
    if tillfallen.length > 1 then
      println(s"Note: ${tillfallen.length} instances found, using most recent.")

    println(s"Exporting participant list...")
    val csv = exportDeltagare(cookies, xsrf, uid)
    os.write.over(outDir / filnamn, csv)
    println(s"Saved to $outDir/$filnamn")
  else
    println("Searching for student...")
    val searchResult =
      if a.length == 1 then searchByPnr(cookies, xsrf, a(0))
      else searchByName(cookies, xsrf, a(0), a(1))

    val results = searchResult("Resultat").arr
    if results.isEmpty then
      println("No student found.")
      sys.exit(1)

    for student <- results do
      val uid = student("Uid").str
      val fornamn = student("Fornamn").str
      val efternamn = student("Efternamn").str
      val pnr = student.obj.get("Personnummer").map(_.str).getOrElse("?")
      println(s"\n$fornamn $efternamn ($pnr)")
      println(s"  UID: $uid")

      val kontakt = getKontakt(cookies, xsrf, uid)
      val email = extractEmail(kontakt)
      println(s"  Email: $email")
