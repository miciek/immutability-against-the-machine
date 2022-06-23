import cats.effect.IO
import cats.implicits.*
import cats.effect.unsafe.implicits.global
import org.apache.jena.query.{QueryFactory, QuerySolution, ResultSet}
import org.apache.jena.rdf.model.{Literal, Resource}
import org.apache.jena.rdfconnection.{RDFConnection, RDFConnectionRemote}

import scala.jdk.javaapi.CollectionConverters.asScala
import scala.language.implicitConversions

object QueryingWikidata {
  import model._

  // simple Java-Scala interop, doesn't matter here, just better readability
  implicit def resultSetToList(rs: ResultSet): List[QuerySolution] = {
    asScala(rs).toList
  }

  def parseAttraction(s: QuerySolution): Option[Attraction] = {
    val attractionLabel: Option[String] = Option(s.getLiteral("attractionLabel")).map(l => l.getString)
    val locationId: Option[LocationId]  = Option(s.getResource("location"))
      .map(r => r.getLocalName)
      .map(LocationId.apply)
    val locationLabel: Option[String]   = Option(s.getLiteral("locationLabel")).map(l => l.getString)

    val location: Option[Location] = (locationId, locationLabel).mapN(Location.apply)
    (attractionLabel, location).mapN(Attraction.apply)
  }

  // IO intro
  def main(args: Array[String]): Unit = {
    val connection = RDFConnectionRemote.create
      .destination("https://query.wikidata.org/")
      .queryEndpoint("sparql")
      .build

    def execQuery(query: String): IO[List[QuerySolution]] = {
      IO.delay(connection.query(QueryFactory.create(query)).execSelect())
    }

    val query = s"""
                   |${WikidataAccess.prefixes}
                   |SELECT DISTINCT ?attraction ?attractionLabel ?location ?locationLabel WHERE {
                   |  ?attraction wdt:P31 wd:Q570116;
                   |              rdfs:label ?attractionLabel;
                   |              wdt:P131 ?location.
                   |  FILTER(LANG(?attractionLabel) = "en").
                   |
                   |  ?location rdfs:label ?locationLabel;
                   |  FILTER(LANG(?locationLabel) = "en").
                   |} LIMIT 3
                   |""".stripMargin

    val solutionsProgram: IO[List[QuerySolution]] = execQuery(query)

    println(solutionsProgram) // IO(...)

    val attractionsProgram: IO[List[Attraction]] = solutionsProgram.map(solutions => solutions.flatMap(parseAttraction))

    // run the program imperatively, done only once at the end of the program!
    val attractions: List[Attraction] = attractionsProgram.unsafeRunSync()
    println(attractions)

    // we can also operate on multiple programs
    val attractionProgram: IO[Option[Attraction]] = attractionsProgram.map(result => result.headOption)
    val programs: List[IO[Option[Attraction]]]    = List(attractionProgram, attractionProgram)
    val program: IO[List[Option[Attraction]]]     = programs.sequence
    println(program.unsafeRunSync())
  }

}
