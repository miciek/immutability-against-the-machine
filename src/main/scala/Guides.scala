import cats.effect.{IO, Ref, Resource}
import cats.implicits.*
import cats.effect.unsafe.implicits.global
import WikidataAccess.getSparqlDataAccess
import model.*
import org.apache.jena.query.{QueryExecution, QueryFactory, QuerySolution}
import org.apache.jena.rdfconnection.{RDFConnection, RDFConnectionRemote}

import scala.concurrent.duration.*
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.util.Random

object Guides {

  /** focus on data transformations function input output
    *
    * what I will show:
    * - case class
    * - trait
    * - List.map, List.flatten, List.flatMap, List.fill
    * - Option.map, Option.flatMap, List.headOption
    * - reverse, sortBy
    * - Map.get, Map.updated
    * - IO, IO.delay, IO.pure, IO.map, IO.flatMap
    * - IO.sequence, IO.parSequence
    * - Resource, Resource.use
    * - Ref, Ref.get, Ref.update, Ref.of
    *
    * what I won't show:
    * - newtype
    * - ADTs
    * - pattern matching
    * - for comprehension
    * - no underscore notation
    *
    * - additional references: Loom
    *
    * TODO: potential expansions/additions
    * some locations for "Bridge" return Avengers which exceeds the integer and throws. we could use it to introduce IO, IO.orElse (based on Option.orElse)
    * List(( ?subject = <http://www.wikidata.org/entity/Q23781155> ) ( ?subjectLabel = "Avengers: Endgame"@en ) ( ?boxOffice = "2797501328"^^xsd:decimal ))
    */

  /** STEP 1: immutable values, case classes, TravelGuide model */

  /** STEP 2: List & Option ???? */

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
    // move your business logic to non-IO pure functions
    def guideWithManyMovies(travelGuides: List[TravelGuide]): Option[TravelGuide] = {
      travelGuides
        .sortBy(guide => guide.movies.size)
        .reverse
        .headOption
    }

    // TODO: different function that uses the internals of movies, not just size

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
    import Version2.guideWithManyMovies

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
    // Some(TravelGuide(Attraction(Galata Bridge,Location(LocationId(Q406),Istanbul)),List(Movie(Skyfall,1108561013))))
    // or: Some(TravelGuide(Attraction(Galata Bridge,Location(LocationId(Q2706262),Karaköy)),List()))

    // println(Version2.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())
    // Some(TravelGuide(Attraction(Third Mainland Bridge,Location(LocationId(Q8673),Lagos)),List(Movie(Captain America: Civil War,1153296293))))

    // PROBLEM: it will take a long time because we are querying in sequence
    // val start = System.currentTimeMillis()
    // println(Version2.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())
    // Some(TravelGuide(Attraction(Third Mainland Bridge,Location(LocationId(Q8673),Lagos)),List(Movie(Captain America: Civil War,1153296293))))
    // println(s"Took ${System.currentTimeMillis() - start}ms") // will be more for Version2 than Version1 (remember to run a random query first)

    // println(Version3.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())
    // Some(TravelGuide(Attraction(Third Mainland Bridge,Location(LocationId(Q8673),Lagos)),List(Movie(Captain America: Civil War,1153296293))))
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

    // println(program.unsafeRunSync())

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
