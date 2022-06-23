import cats.effect.{IO, Ref, Resource}
import cats.implicits.*
import cats.effect.unsafe.implicits.global
import WikidataAccess._
import org.apache.jena.query.{QueryExecution, QueryFactory, QuerySolution}
import org.apache.jena.rdfconnection.{RDFConnection, RDFConnectionRemote}

import scala.concurrent.duration.*
import scala.util.Random
import QueryingWikidata._

object Guides {

  /** STEP 1: modeling as immutable values (product types) - [[model]] */
  import model.*

  /** STEP 2: IO - [[QueryingWikidata]], [[DataAccess]], WikidataAccess, SPARQL intro, n-triples */
  def execQuery(connection: RDFConnection)(query: String): IO[List[QuerySolution]] = {
    IO.delay(connection.query(QueryFactory.create(query)).execSelect())
  }

  /** STEP 3: first version of a TravelGuide finder
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

  /** STEP 4: second version takes more attractions into consideration and returns "the best" one
    */
  def guideWithManyMovies(travelGuides: List[TravelGuide]): Option[TravelGuide] = {
    travelGuides
      .sortBy(guide => guide.movies.size)
      .reverse
      .headOption
  }

  object Version2 {
    def travelGuide(data: DataAccess, attractionName: String): IO[Option[TravelGuide]] = {
      data
        .findAttractions(attractionName, 3)
        .flatMap(attractions =>
          attractions.map(attraction =>
            data
              .findMoviesAboutLocation(attraction.location.id, 3)
              .map(movies => TravelGuide(attraction, movies))
          ).sequence
        ).map(guideWithManyMovies)
    }
  }

  /** STEP 5: make it concurrent
    */
  object Version3 {
    def travelGuide(data: DataAccess, attractionName: String): IO[Option[TravelGuide]] = {
      data
        .findAttractions(attractionName, 3)
        .flatMap(attractions =>
          attractions.map(attraction =>
            data
              .findMoviesAboutLocation(attraction.location.id, 3)
              .map(movies => TravelGuide(attraction, movies))
          ).parSequence
        ).map(guideWithManyMovies)
    }
  }

  def main(args: Array[String]): Unit = {
    val connection     = createConnectionUnsafe()
    val wikidataAccess = getSparqlDataAccess(execQuery(connection))
    println(Version1.travelGuide(wikidataAccess, "Bridge of Sighs").unsafeRunSync())

    // PROBLEM: if we search generically, we'll just query for the first hit and it may not be the best one (or empty)
    // println(Version2.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())

    // PROBLEM: it will take a long time because we are querying in sequence
    // println(Version3.travelGuide(wikidataAccess, "Bridge").unsafeRunSync())

    // PROBLEM: we are not closing connections (and query executions, but we ignore it in this example)
    connection.close()

    /** STEP 6: protect against leaks
      */
    val connectionResource: Resource[IO, RDFConnection] = Resource.make(
      IO.delay(createConnectionUnsafe())
    )(connection => IO.delay(connection.close()))

    val program: IO[Option[TravelGuide]] = connectionResource.use(c => {
      val wikidata = getSparqlDataAccess(execQuery(c))
      Version3.travelGuide(wikidata, "Bridge") // this will not leak, even if there are errors
    })

    /** STEP 7: same queries will return same results, cache them!
      */
    def cachedExecQuery(
        connection: RDFConnection,
        cache: Ref[IO, Map[String, List[QuerySolution]]]
    )(query: String): IO[List[QuerySolution]] = {
      cache.get.flatMap(cachedQueries => {
        val maybeCachedSolutions: Option[List[QuerySolution]] = cachedQueries.get(query)
        maybeCachedSolutions match {
          case Some(cachedSolutions) => IO.pure(cachedSolutions)
          case None                  => execQuery(connection)(query).flatMap(realSolutions =>
              cache.update(_.updated(query, realSolutions)).map(_ => realSolutions)
            )
        }
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
