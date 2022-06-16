import cats.effect.IO
import model._

/** STEP 2
  * DATA ACCESS (just an interface providing pure functions, implementation completely separated)
  */
trait DataAccess {
  def findAttractions(name: String, limit: Int): IO[List[Attraction]]
  def findMoviesAboutLocation(locationId: LocationId, limit: Int): IO[List[Movie]]
}
