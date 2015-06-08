/**
 * Copyright (C) 2015 Typesafe Inc. <http://www.typesafe.com>
 */
package docs.persistence.query

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import akka.actor.ExtendedActorSystem
import akka.persistence.query.EventsByTag
import akka.persistence.query.Hint
import akka.persistence.query.Query
import akka.persistence.query.ReadJournal
import akka.persistence.query.RefreshInterval
import akka.stream.scaladsl.Source

class MyReadJournal(system: ExtendedActorSystem) extends ReadJournal {

  // TODO from config
  private val defaulRefreshInterval: FiniteDuration = 3.seconds

  override def query[T, M](q: Query[T, M], hints: Hint*): Source[T, M] = {
    q match {
      case EventsByTag(tag, timestamp) ⇒
        Source.actorPublisher[String](EventsByTagPublisher.props(tag, timestamp,
          refreshInterval(hints))).mapMaterializedValue(_ ⇒ ())

      case q ⇒
        Source.failed[T](
          new UnsupportedOperationException(s"Query $q not supported by ${getClass.getName}"))
          .mapMaterializedValue(_ ⇒ null.asInstanceOf[M])
    }
  }

  private def refreshInterval(hints: Seq[Hint]): FiniteDuration =
    hints.collectFirst { case RefreshInterval(interval) ⇒ interval }
      .getOrElse(defaulRefreshInterval)

}
