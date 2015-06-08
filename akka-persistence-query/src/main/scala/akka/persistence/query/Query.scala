/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.persistence.query

import akka.stream.scaladsl.Source
import scala.concurrent.duration.FiniteDuration

/**
 * Plugin API
 *
 * {{{
 * val queries = Persistence(system).queries(journalPluginConfigPath)
 * val source = queries.query(AllPersistenceIds)
 * }}}
 */
abstract class EventQueries {

  def query[T, M](q: Query[T, M], hints: Hint*): Source[T, M]

}

trait Query[T, M]

case object AllPersistenceIds extends Query[String, Unit]

case class EventsByTag(tag: String, timestamp: Long) extends Query[Any, Unit]

case class EventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)
  extends Query[Any, Unit]

trait Hint

case class RefreshInterval(interval: FiniteDuration) extends Hint

case object NoRefresh extends Hint
