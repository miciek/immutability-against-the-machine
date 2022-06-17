import cats.effect.IO
import model._
import org.apache.jena.query.QuerySolution

object WikidataAccess {

  def getSparqlDataAccess(execQuery: String => IO[List[QuerySolution]]): DataAccess = new DataAccess {
    val prefixes = """
        |PREFIX wd: <http://www.wikidata.org/entity/>
        |PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        |PREFIX schema: <http://schema.org/>
        |""".stripMargin

    def findAttractions(name: String, limit: Int): IO[List[Attraction]] = {
      val query = s"""
        |$prefixes
        |SELECT DISTINCT ?attraction ?attractionLabel ?location ?locationLabel WHERE {
        |  ?attraction wdt:P31 wd:Q570116;
        |              rdfs:label ?attractionLabel;
        |              wdt:P131 ?location.
        |  FILTER(LANG(?attractionLabel) = "en").
        |
        |  ?location rdfs:label ?locationLabel;
        |  FILTER(LANG(?locationLabel) = "en").
        |
        |  FILTER(CONTAINS(?attractionLabel, "$name")).
        |} LIMIT $limit
        |""".stripMargin

      execQuery(query)
        .flatMap(solutions =>
          IO.delay(
            solutions.map(s =>
              Attraction(
                name = s.getLiteral("attractionLabel").getString,
                location = Location(
                  LocationId(s.getResource("location").getLocalName),
                  s.getLiteral("locationLabel").getString
                )
              )
            )
          )
        )
    }

    def findMoviesAboutLocation(locationId: LocationId, limit: Int): IO[List[Movie]] = {
      val query = s"""
        |$prefixes
        |SELECT DISTINCT ?subject ?subjectLabel ?boxOffice WHERE {
        |  ?subject wdt:P31 wd:Q11424;
        |           wdt:P2142 ?boxOffice;
        |           rdfs:label ?subjectLabel.
        |
        |  ?subject wdt:P840 wd:${locationId.value}
        |
        |  FILTER(LANG(?subjectLabel) = "en").
        |
        |} ORDER BY DESC(?boxOffice) LIMIT $limit
        |""".stripMargin

      execQuery(query)
        .flatMap(solutions =>
          IO.delay(
            solutions.map(s =>
              Movie(name = s.getLiteral("subjectLabel").getString, boxOffice = s.getLiteral("boxOffice").getLong)
            )
          )
        ).map(movies => movies.distinctBy(_.name))
    }
  }
}
