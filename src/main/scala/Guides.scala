import cats.effect.{IO, Ref, Resource}
import cats.implicits.*
import cats.effect.unsafe.implicits.global
import WikidataAccess.getSparqlDataAccess
import org.apache.jena.query.{QueryExecution, QueryFactory, QuerySolution}
import org.apache.jena.rdfconnection.{RDFConnection, RDFConnectionRemote}

import scala.concurrent.duration.*
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.util.Random

object Guides {

  /** STEP 1: modeling as immutable values (product types) */
  import model.*

  /** STEP 2: pure functions
    * If we have many travel guides, which one should we choose?
    **/
  def guideWithManyMovies(travelGuides: List[TravelGuide]): Option[TravelGuide] = {
    travelGuides
      .sortBy(guide => guide.movies.size)
      .reverse
      .headOption
  }

  /** STEP 3: IO - WikidataAccess & QueryingWikidata, SPARQL intro, n-triples */
  def execQuery(connection: RDFConnection): String => IO[List[QuerySolution]] = { query =>
    IO.delay(asScala(connection.query(QueryFactory.create(query)).execSelect()).toList)
  }

  /** STEP 4: first version of a TravelGuide finder
    */
  object Version1 {
    def travelGuide(data: DataAccess, attractionName: String): IO[Option[TravelGuide]] = {
      data
        .findAttractions(attractionName, 1)
        .map(attractions => attractions.headOption)
        .map(maybeAttraction =>
          maybeAttraction.map(attraction =>
            data
              .findMoviesAboutLocation(attraction.location.id, 1)
              .map(movies => TravelGuide(attraction, movies))
          )
        ).flatMap(maybeProgram => maybeProgram.sequence)
    }
  }

  /** STEP 5: second version takes more attractions into consideration and returns "the best" one
    */
  object Version2 {
    def travelGuide(data: DataAccess, attractionName: String): IO[Option[TravelGuide]] = {
      data
        .findAttractions(attractionName, 5)
        .flatMap(attractions =>
          attractions.map(attraction =>
            data
              .findMoviesAboutLocation(attraction.location.id, 1)
              .map(movies => TravelGuide(attraction, movies))
          ).sequence
        ).map(guideWithManyMovies)
    }
  }

  /** STEP 6: make it concurrent
    */
  object Version3 {
    def travelGuide(data: DataAccess, attractionName: String): IO[Option[TravelGuide]] = {
      data
        .findAttractions(attractionName, 50)
        .flatMap(attractions =>
          attractions.map(attraction =>
            data
              .findMoviesAboutLocation(attraction.location.id, 1)
              .map(movies => TravelGuide(attraction, movies))
          ).parSequence
        ).map(guideWithManyMovies)
    }
  }

  /** STEP 7: protect against leaks
    */
  def createExecution(connection: RDFConnection, query: String): IO[QueryExecution]    = IO.blocking(
    connection.query(QueryFactory.create(query))
  )
  def closeExecution(execution: QueryExecution): IO[Unit]                              = IO.blocking(
    execution.close()
  )
  def execQuerySafe(connection: RDFConnection)(query: String): IO[List[QuerySolution]] = {
    val executionResource: Resource[IO, QueryExecution] = Resource.make(createExecution(connection, query))(
      closeExecution
    ) // or Resource.fromAutoCloseable(createExecution)

    executionResource.use(execution => IO.blocking(asScala(execution.execSelect()).toList))
  }

  /** STEP 8: same queries will return same results, cache them!
    */

  def main(args: Array[String]): Unit = {
    val connection     = RDFConnectionRemote.create
      .destination("https://query.wikidata.org/")
      .queryEndpoint("sparql")
      .build
    val wikidataAccess = getSparqlDataAccess(execQuery(connection))
    // println(Version1.travelGuide(wikidataAccess, "Bridge of Sighs").unsafeRunSync()) // OK
    // Some(TravelGuide(Attraction(Bridge of Sighs,Location(LocationId(Q641),Venice)),List(Movie(Spider-Man: Far from Home,1131927996))))

    // PROBLEM: if we search generically, we'll just query for the first hit and it may not be the best one (or empty)
    // println(Version1.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())

    // PROBLEM: it will take a long time because we are querying in sequence
    // val start = System.currentTimeMillis()
    // println(Version2.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())
    // println(s"Took ${System.currentTimeMillis() - start}ms") // will be more for Version2 than Version1 (remember to run a random query first)

    // val start = System.currentTimeMillis()
    // println(Version3.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())
    // println(s"Took ${System.currentTimeMillis() - start}ms") // will be less for Version3 than Version2 (remember to run a random query first)

    // PROBLEM: we are not closing connections (and query executions)
    connection.close()

    val connectionResource: Resource[IO, RDFConnection] = Resource.make(
      IO.blocking(
        RDFConnectionRemote.create
          .destination("https://query.wikidata.org/")
          .queryEndpoint("sparql")
          .build
      )
    )(connection => IO.blocking(connection.close()))

    val program: IO[Option[TravelGuide]] = connectionResource.use(c => {
      val wikidata = getSparqlDataAccess(execQuerySafe(c))
      Version3.travelGuide(wikidata, "Bridge") // this will not leak, even if there are errors
    })

    // PROBLEM: we may be running the same queries against the API, let's cache them
    def runQueryAndUpdateCache(
        connection: RDFConnection,
        cache: Ref[IO, Map[String, List[QuerySolution]]],
        query: String
    ): IO[List[QuerySolution]] = {
      execQuery(connection)(query).flatMap(realSolutions =>
        cache.update(_.updated(query, realSolutions)).map(_ => realSolutions)
      )
    }

    def cachedExecQuery(
        connection: RDFConnection,
        cache: Ref[IO, Map[String, List[QuerySolution]]]
    ): String => IO[List[QuerySolution]] = { query =>
      cache.get.flatMap(cachedQueries => {
        cachedQueries.get(query).map(cachedSolutions => IO.pure(cachedSolutions)).getOrElse(runQueryAndUpdateCache(
          connection,
          cache,
          query
        ))
      })
    }

    val programWithCache: IO[List[Option[TravelGuide]]] = connectionResource.use(c => {
      Ref.of[IO, Map[String, List[QuerySolution]]](Map.empty).flatMap(cache => {
        val wikidata = getSparqlDataAccess(cachedExecQuery(c, cache))

        List.fill(100)(Version3.travelGuide(wikidata, "Bridge")).sequence
      })
    })

    println(programWithCache.unsafeRunSync()) // a hundred of programs run in sequence returns in seconds
  }
}
