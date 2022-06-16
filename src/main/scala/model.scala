object model {

  /** STEP 1
    * MODEL: just immutable values (reused in the whole application).
    *
    * Note that we have a single model for the whole application, but sometimes it may be beneficial
    * to have separate ones for data access and business domain (see the book).
    */
  case class LocationId(value: String)
  case class Location(id: LocationId, name: String)
  case class Attraction(name: String, location: Location)
  case class Movie(name: String, boxOffice: Long)
  case class TravelGuide(attraction: Attraction, movies: List[Movie])
}
