//> using scala 3.8.3
//> using toolkit 0.9.2
//> using dep com.lihaoyi::requests:0.9.3

/** Query Ladok for student info or export course participant lists.
  *
  * Search by name or personnummer:
  *   `scala ladok.scala -- "Firstnam*" "Lastnam*"`
  *   `scala ladok.scala -- YYYYMMDDCCCC`
  *
  * Export participant list to CSV:
  *   `scala ladok.scala -- --kurskod EDAB05`
  */
package ladok

import requests.Response as HTTP
import ujson.Value       as JSON

val Help = 
  s"""|Usage:
      |  scala ladok.scala -- --help
      |  scala ladok.scala -- --tillfälle <kurskod> 
      |  scala ladok.scala -- --deltagare <kurskod> 
      |  scala ladok.scala -- --deltagare <kurskod> <tillfälle1> <tillfälle2> ...
      |  scala ladok.scala -- --resultat <search input>
      |  scala ladok.scala -- --kontakt <search input>
      |      
      |<search input> can be one or more of these items
      |  <perssonnummer>
      |  '<förnamn> <efternamn>'
      |  '<efternamn>, <förnamn>' 
      |Names must be inside quotes. 
      |If name contains comma then efternamn comes before förnamn.
      |Wildcards like Svens* are supported for name search.
      |
      |You can use personnummer in any below form:
      |  20101201-1234 101201-1234 1012011234 201012011234
      |
      |Put the full Cookie header value from browser dev tools into ~/.ladok-cookie
      |
      |More usage info and how to copy the cookie header is available in README here:
      |https://github.com/bjornregnell/scala-ladok-requests
      |""".stripMargin

val Home = "https://github.com/bjornregnell/scala-ladok-requests"

val Welcome = s"*** Välkommen till scala-ladok-requests!\nSe README här: $Home\n"

val HttpResponseOK = 200 

val NotLoggedInHintInResponse = "saknar behörighet"

val LadokProxy = "https://start.ladok.se/gui/proxy"

val StudentBase = s"$LadokProxy/studentinformation/internal/student"

val HelpToFindCookie = 
  s"""|Open Firefox and press F12 to access developer tools.
      |After you have logged in to Ladok:
      |  Goto the Network Tab and find a "File" row with "inloggadanvandare"
      |  Goto the XHR Tab and then to Headers and scroll down to Request Headers 
      |  Find the Cookie entry and select all the text after "Cookie:"
      |    It starts with something similar to LADOK_LANG=sv; XSRF-TOKEN=9f6685a9....
      |  Copy the whole cookie text""".stripMargin


val HelpToPasteCookie = 
  s"""|Paste the cookie text into this file:
      |${Cookie.cookieFile}
      |More information: $Home""".stripMargin

case class Cookie(cookies: String, xsrf: String):
  def authHeaders = Map(
    "Cookie" -> cookies,
    "X-XSRF-TOKEN" -> xsrf,
    "X-Requested-With" -> "XMLHttpRequest",
    "Accept" -> "application/json",
    "Content-Type" -> "application/json",
  )
object Cookie:
  val cookieFile = os.home / ".ladok-cookie"
  given defaultCookie: Cookie = readCookieFile()

  /** Read the full cookie string and extract XSRF token from ~/.ladok-cookie */
  def readCookieFile(): Cookie =
    if !os.exists(cookieFile) then savePastedCookieToFileOrExit(missingFile = true)
    val cookies = os.read(cookieFile).replaceAll("\\s+", " ").trim
    val xsrf = cookies.split(";").map(_.trim)
      .find(_.startsWith("XSRF-TOKEN="))
      .map(_.stripPrefix("XSRF-TOKEN="))
      .getOrElse(sys.error("XSRF-TOKEN not found in cookie string"))
    Cookie(cookies, xsrf)

  def savePastedCookieToFileOrExit(missingFile: Boolean): Unit =
    if missingFile then log(s"Kaka behöver sparas i denna fil: $cookieFile")
    warn("\nDu måste logga in och kopiera kakan!\n")
    log(s"$HelpToFindCookie") 
    val pasted = scala.io.StdIn.readLine("Klistra in kaka här + Enter eller tryck bara Enter för Exit.\n")
    if Option(pasted).map(_.nonEmpty) == Some(true) then
      log(s"Sparar kaka här: $cookieFile")
      os.write.over(target = cookieFile, data = pasted)
    else 
      warn("Kaka saknas. Avslutar nu. Kör om igen med sparad kaka. ")
      log(s"$HelpToPasteCookie")
      System.exit(1)

end Cookie

case class Student(student: JSON)(using cookie: Cookie):
  def get(key: String): String = student(key).str  
  lazy val Kontakt: JSON = getStudentKontakt(Uid)
  lazy val Epost: String = util.Try(Kontakt("Epostadress").str).getOrElse("") 
  lazy val Telefonnummer: String = util.Try(Kontakt("Telefonnummer").str).getOrElse("") 
  lazy val Uid = get("Uid")
  lazy val Fornamn = get("Fornamn")
  lazy val Efternamn = get("Efternamn")
  lazy val Personnummer = get("Personnummer")
  lazy val Kursöversikt: JSON = getStudentÖversiktResultat(Uid)
  
  def showContact: String = s"$Personnummer;$Efternamn, $Fornamn;$Epost;$Telefonnummer"

  def showResultat: String = 
    // traverse the JSON of Kursöversikt and yield a nice formattedResult string with one course per row with these headings: 
    // Utb.kod;Omf. i hp;Resultat på kurs;Benämning;Period;Tillstånd
    val formattedResult: String = ??? 
    s"Resultat för $Personnummer: $Efternamn, $Fornamn; $Epost; $Telefonnummer\n---\n$formattedResult\n---\n" 

  def showKeys: String = 
    s"""|         Uid: $Uid
        |     Fornamn: $Fornamn
        |   Efternamn: $Efternamn
        |Personnummer: $Personnummer
        |""".stripMargin

  def showAll: String = student.obj.mkString("\n")
object Student:
  def showHeadings = s"Personnummer;Namn;Epost;Telefonnummer"
end Student

case class Kurs(
  uid: String, 
  kod: String, 
  tillfalle: String, 
  start: String, 
  slut: String, 
  namn: String
)

def log(msg: String): Unit = System.err.println(Console.GREEN + msg + Console.RESET)

def err(msg: String): Unit = System.err.println(Console.RED + msg + Console.RESET)

def warn(msg: String): Unit = 
  System.err.println(Console.YELLOW + Console.REVERSED + msg + Console.RESET)

lazy val outDir = 
  if !os.exists(os.pwd / "out") then os.makeDir(os.pwd / "out")
  os.pwd / "out"

val outFilePrefix = "Deltagare"

object Abort:

  def ifNotOK(response: HTTP): Unit = {
    val body = response.text()

    val isNotAuthorized = body.contains(NotLoggedInHintInResponse)

    if response.statusCode != HttpResponseOK || isNotAuthorized then 
      err(body)
    
      if body.contains(NotLoggedInHintInResponse) then
        warn("\nDu måste logga in och kopiera kakan!\n")
        log(s"$HelpToFindCookie") 
        println(s"  cat >~/.ladok-cookie\n")

      err(s"HTTP ${response.statusCode}")
      System.exit(1)
  }

  def ifNotJSON(body: String, truncateErr: Int = 800): Unit = 
    if !body.trim.startsWith("{") && !body.trim.startsWith("[") then
      val n = 800
      sys.error(s"Expected JSON but got:\n${body.take(truncateErr)}...") 

  def apply(msg: String): Unit = 
    err(msg)
    System.exit(1)

end Abort 

def get(url: String)(using cookie: Cookie): JSON =
  val response = requests.get(url, headers = cookie.authHeaders, check = false)
  Abort.ifNotOK(response)
  val body = response.text()
  Abort.ifNotJSON(body)
  ujson.read(body)

def put(url: String, body: JSON)(using cookie: Cookie): requests.Response =
  val response = requests.put(
    url,
    headers = cookie.authHeaders,
    data = body.render(),
    check = false,
  )
  Abort.ifNotOK(response)
  response

def searchByPnr(pnr: String)(using cookie: Cookie): JSON =
  get(s"$StudentBase/filtrera?personnummer=$pnr&page=1&limit=25")

def searchByName(fornamn: String, efternamn: String)(using cookie: Cookie): JSON =
  get(s"$StudentBase/filtrera?fornamn=$fornamn&efternamn=$efternamn&page=1&limit=25&orderby=EFTERNAMN_ASC&orderby=FORNAMN_ASC&orderby=PERSONNUMMER_ASC")

def findAllStudents(pnrOrName: String): Seq[Student] = {
  val maybePersonnummer = pnrOrName.toPersonnummer
  val json = 
    if maybePersonnummer.isDefined then 
      val pnr = maybePersonnummer.get
      searchByPnr(pnr)
    else 
      val (fornamn, efternamn) = 
        if pnrOrName.isEfternamnKommaFörnamn then
          val parts = pnrOrName.split(',').map(_.trim).filter(_.nonEmpty)
          (parts.lift(1).getOrElse("*"), parts.lift(0).getOrElse("*"))
        else
          val parts = pnrOrName.split(' ').map(_.trim).filter(_.nonEmpty)
          (parts.lift(0).getOrElse("*"), parts.lift(1).getOrElse("*"))

      searchByName(fornamn, efternamn)
    end if

  extractStudents(json)
}

def getStudentKontakt(uid: String)(using cookie: Cookie): JSON =
  get(s"$StudentBase/$uid/kontaktuppgifter")

def getStudentÖversiktResultat(uid: String)(using cookie: Cookie): JSON = 
  // should build a JSON with an overview of all courses (kurser) and for each course its credits (hp) and grades (resultat) 
  // perhaps it is s"$StudentBase/$uid/oversikt" that is the starting point?
  ???

def searchKurstillfalle(kurskod: String)(using cookie: Cookie): JSON =
  get(s"$LadokProxy/resultat/internal/kurstillfalle/filtrera?kurskod=$kurskod&page=1&limit=100&orderby=KURSBENAMNING_ASC&orderby=KURSKOD_ASC&orderby=START_DATUM_DESC&orderby=KURSTILLFALLESKOD_ASC")

def getKurs(kurskod: String)(using cookie: Cookie): Seq[Kurs] = {
  val json = searchKurstillfalle(kurskod)
  val tillfallen = json("Resultat").arr
  if tillfallen.isEmpty then
    println(s"No course instance found for $kurskod.")
    sys.exit(1)

  val kurser = for tillfalle <- tillfallen yield {
    val uid = tillfalle("Uid").str
    val tillfallesKod = tillfalle("TillfallesKod").str
    val start = tillfalle("Startdatum").str
    val slut = tillfalle("Slutdatum").str
    val namn = tillfalle("Utbildningsinstans")("Benamning").arr
      .find(_("Sprakkod").str == "sv").map(_("Text").str).getOrElse("?")

    Kurs(uid=uid, kod=kurskod, tillfalle=tillfallesKod, start=start, slut=slut, namn=namn)
  }
  kurser.toSeq
}

/** Download csv of all participant of uid of utbildningstillfalle */
def exportDeltagare(uid: String)(using cookie: Cookie): (info: String, csv: String) = {
  val body = ujson.Obj(
    "utbildningstillfalleUID" -> ujson.Arr(uid),
    "deltagaretillstand" -> ujson.Arr("EJ_PABORJAD", "REGISTRERAD", "AVKLARAD"),
    "orderby" -> ujson.Arr("EFTERNAMN_ASC", "FORNAMN_ASC", "PERSONNUMMER_ASC"),
  )
  val response = put(s"$LadokProxy/studiedeltagande/internal/deltagare/kurstillfalle/export", body)
  val text = response.text()
  // Ladok returns CSV wrapped in a JSON string
  val csv = try ujson.read(text).str catch case _: Exception => text
  val lines = csv.split("\n")
  val skip = 6 // lines includes a 6 lines + 1 empty line preamble with info about the export
  (info = lines.take(skip).mkString("\n"), csv = lines.drop(skip + 1).mkString("\n"))
}

extension (searchResult: JSON) 
  def extractStudents: Seq[Student] = 
    val results = searchResult("Resultat").arr
    if results.isEmpty then Seq() 
    else results.map(s => Student(s)).toSeq

extension (s: String) 
  def isKurskod: Boolean = 
    val t = s.trim.toUpperCase()
    t.length == 6 && t.forall(_.isLetterOrDigit)

  def isEfternamnKommaFörnamn: Boolean = s.count(_ == ',') == 1 

  def toPersonnummer: Option[String] =
    val currentYear = java.time.Year.now().getValue().toString.drop(2).toInt
    val t = s.trim.filterNot(c => c == '-')
    if !t.forall(_.isDigit) || s.count(_ == '-') > 1 then None
    else if t.length == 12 then Some(t)
    else if t.take(2).toIntOption.map(_ > currentYear).getOrElse(false) then Some(s"19$t")
    else Some(s"20$t")

@main def Main(args: String*): Unit =
  def errMissingKurskod(opt: String) = err(s"ange kurskod, tex såhär: --deltagare EDAB05")

  args.toSeq match
    case Seq("--help") | Seq("-h") | Seq("--hjälp") => println(Help)

    case Seq("--deltagare") => errMissingKurskod("--deltagare")  

    case Seq("--tillfälle") => errMissingKurskod("--tillfälle")

    case Seq("--tillfälle", kurskod) => 
      val tillfallen = getKurs(kurskod)
      for k <- tillfallen do
        import k.*
        println(s"$kurskod;$start--$slut;$namn;$tillfalle")

      log(s"Totalt ${tillfallen.length} tillfällen:\n${tillfallen.map{_.tillfalle}.mkString(", ")}")

    case xs if xs.headOption == Some("--deltagare") => 
      val kurskod = xs.lift(1).getOrElse("").trim
      if kurskod.isEmpty then Abort("--deltagare ska följas av kurskod sedan ev. tillfälleskoder")
      
      val kurser = getKurs(kurskod)
      if kurser.isEmpty then Abort(s"Hittar inga kurstillfällen för $kurskod")

      val tillfallenArgs = xs.drop(2)
      
      val utvaldaKurser: Seq[Kurs] = 
        if tillfallenArgs.isEmpty then
          if kurser.length > 1 then 
            log(s"Hittade ${kurser.length} tillfällen:\n${kurser.map(_.tillfalle).mkString(",")}")
            log(s"Väljer den senaste: ${kurser(0)}") 
          kurser.take(1)
        else tillfallenArgs.flatMap: t =>
          val kOpt = kurser.find(_.tillfalle == t)
          if kOpt.isEmpty then 
            err(s"Hittar inte tillfälle $t")
            Seq()
          else 
            Seq(kOpt.get)

      for kurs <- utvaldaKurser do
        val data = exportDeltagare(kurs.uid)
        val date = java.time.LocalDate.now().toString
        val tk = kurs.tillfalle 
        val f1 = s"$outFilePrefix-$kurskod-$tk-$date.csv"
        val f2 = s"$outFilePrefix-$kurskod-$tk-info-$date.csv"
        os.write.over(outDir / f1, data.csv)
        log(s"Saved to $outDir/$f1")
        os.write.over(outDir / f2, data.info)
        log(s"Saved to $outDir/$f2")
    
    case xs if xs.headOption == Some("--kontakt") =>
      log(s"Searching for: ${xs.mkString(" ")}")
      println(Student.showHeadings)
      xs.foreach: arg =>
        val ss = findAllStudents(pnrOrName = arg)
        println(ss.map(_.showContact).mkString("","\n",""))

    case xs if xs.headOption == Some("--resultat") =>
      log(s"Searching for: ${xs.mkString(" ")}")
      println(Student.showHeadings)
      xs.foreach: arg =>
        val ss = findAllStudents(pnrOrName = arg)
        println(ss.map(_.showResultat).mkString("","\n",""))

    case xs => err(s"Unknown argument: ${xs.mkString(" ")}")


