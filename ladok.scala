//> using scala 3.8.3
//> using toolkit 0.9.2

/** To search for a students email 
 * Run using either first name last name strings or personnummer 
  * `scala-cli ladok.scala -- "Firstnam*" "Lastnam*"`
  * `scala-cli ladok.scala -- YYYYMMDDCCCC`
  */
package ladok

import sttp.client4.quick.*

val LadokBase = "https://start.ladok.se/gui/proxy/studentinformation/internal/student"

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

def get(cookies: String, xsrf: String, url: String): ujson.Value =
  val resp = quickRequest
    .get(uri"$url")
    .header("Cookie", cookies)
    .header("X-XSRF-TOKEN", xsrf)
    .header("X-Requested-With", "XMLHttpRequest")
    .header("Accept", "application/vnd.ladok-studentinformation+json, application/json")
    .header("Content-Type", "application/vnd.ladok-studentinformation+json")
    .send()
  if resp.code.code != 200 then
    sys.error(s"HTTP ${resp.code}: ${resp.body.take(500)}")
  if !resp.body.trim.startsWith("{") && !resp.body.trim.startsWith("[") then
    sys.error(s"Expected JSON but got (first 500 chars):\n${resp.body.take(500)}")
  ujson.read(resp.body)

def searchByPnr(cookies: String, xsrf: String, pnr: String): ujson.Value =
  get(cookies, xsrf, s"$LadokBase/filtrera?personnummer=$pnr&page=1&limit=25")

def searchByName(cookies: String, xsrf: String, fornamn: String, efternamn: String): ujson.Value =
  get(cookies, xsrf, s"$LadokBase/filtrera?fornamn=$fornamn&efternamn=$efternamn&page=1&limit=25&orderby=EFTERNAMN_ASC&orderby=FORNAMN_ASC&orderby=PERSONNUMMER_ASC")

def getKontakt(cookies: String, xsrf: String, uid: String): ujson.Value =
  get(cookies, xsrf, s"$LadokBase/$uid/kontaktuppgifter")

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
    println("Wildcards like Regn* are supported for name search.")
    println()
    println("Put the full Cookie header value from Firefox DevTools into ~/.ladok-cookie")
    sys.exit(1)

  val (cookies, xsrf) = readCookieFile()

  println("Searching for student...")
  val searchResult =
    if args.length == 1 then searchByPnr(cookies, xsrf, args(0))
    else searchByName(cookies, xsrf, args(0), args(1))

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