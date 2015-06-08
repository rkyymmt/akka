/*
 * Copyright (C) 2015 Typesafe Inc. <http://www.typesafe.com>
 */

package docs.persistence.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.AkkaSpec

import scala.collection.immutable

// TODO make sure a journal with NOT taking EAS can work
class NoopReadJournal(sys: ExtendedActorSystem) extends ReadJournal {
  override def query[T, M](q: Query[T, M], hints: Hint*): Source[T, M] =
    Source.empty.mapMaterializedValue(_ => null.asInstanceOf[M])
}

class PersistenceQueryDocSpec(s: String) extends AkkaSpec(s) {

  def this() {
    this(
      """
        akka.persistence.query.noop-read-journal {
          class = "docs.persistence.query.NoopReadJournal"
        }
      """.stripMargin)
  }

  //#basic-usage
  // obtain read journal by plugin id
  val readJournal = PersistenceQuery(system).readJournalFor("akka.persistence.query.noop-read-journal")

  // issue query to journal
  val source: Source[Any, Unit] = readJournal.query(EventsByPersistenceId("user-1337", 0, Long.MaxValue))

  // materialize stream, consuming events
  implicit val mat = ActorFlowMaterializer()
  source.runForeach { event => println("Event: " + event) }
  //#basic-usage

  //#advanced-journal-query-definition
  final case class RichEvent(tags: immutable.Set[String], payload: Any)
  case class QueryStats(totalEvents: Long)

  case class ByTagsWithStats(tags: immutable.Set[String])
    extends Query[RichEvent, QueryStats]
  //#advanced-journal-query-definition

  //#advanced-journal-query-hints
  import scala.concurrent.duration._

  readJournal.query(EventsByTag("blue"), hints = RefreshInterval(1.second))
  //#advanced-journal-query-hints

  //#advanced-journal-query-usage
  val query: Source[RichEvent, QueryStats] =
    readJournal.query(ByTagsWithStats(Set("red", "blue")))

  query
    .mapMaterializedValue { stats => println(s"Stats: $stats") }
    .map { event => println(s"Event payload: ${event.payload}") }
    .runWith(Sink.ignore)
  //#advanced-journal-query-usage
}
