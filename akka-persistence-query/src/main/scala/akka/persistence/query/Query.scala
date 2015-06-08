/*
 * Copyright (C) 2015 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.persistence.query

import akka.stream.scaladsl.Source
import scala.concurrent.duration.FiniteDuration

/**
 * API for reading persistent events and information derived
 * from stored persistent events.
 *
 * The purpose of the API is not to enforce compatibility between different
 * journal implementations, because the technical capabilities may be very different.
 * The interface is very open so that different journals may implement specific queries.
 *
 * Usage from Scala:
 * {{{
 * val journal = PersistenceQuery(system).readJournalFor(queryPluginConfigPath)
 * val events = journal.query(EventsByTag("mytag", 0L))
 * }}}
 *
 * Usage from Java:
 * {{{
 * final ReadJournal journal =
 *   PersistenceQuery.get(system).readJournalFor(queryPluginConfigPath);
 * final Source&lt;Object, ?&gt; events = journal.query(
 *   new EventsByTag("mytag", 0L));
 * }}}
 */
abstract class ReadJournal {

  /**
   * A query that returns a `Source` with output type `T` and materialized
   * value `M`.
   *
   * The `hints` are optional parameters that defines how to execute the
   * query, typically specific to the journal implementation.
   *
   */
  def query[T, M](q: Query[T, M], hints: Hint*): Source[T, M]

}

/**
 * General interface for all queries. There are a few pre-defined queries,
 * such as [[EventsByPersistenceId]], [[AllPersistenceIds]] and [[EventsByTag]]
 * but implementation of these queries are optional. Query (journal) plugins
 * may define their own specialized queries.
 *
 * If a query plugin does not support a query it will return a stream that
 * will be completed with failure `UnsupportedOperationException`.
 */
trait Query[T, M]

/**
 * Query all `PersistentActor` identifiers, i.e. as defined by the
 * `persistenceId` of the `PersistentActor`.
 *
 * A plugin may optionally support this [[Query]].
 */
case object AllPersistenceIds extends Query[String, Unit]

/**
 * Query events for a specific `PersistentActor` identified by `persistenceId`.
 *
 * You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr`
 * or use `0L` and `Long.MaxValue` respectively to retrieve all events.
 *
 * The returned event stream should be ordered by sequence number.
 *
 * A plugin may optionally support this [[Query]].
 */
case class EventsByPersistenceId(persistenceId: String, fromSequenceNr: Long = 0L, toSequenceNr: Long = Long.MaxValue)
  extends Query[Any, Unit]

/**
 * Query events that have a specific tag. A tag can for example correspond to an
 * aggregate root type (in DDD terminology). The query can be limited to to all events
 * created at or after the given `timestamp`, which is specified as number of milliseconds
 * since the standard base time known as "the epoch", namely January 1, 1970, 00:00:00 GMT.
 *
 * The exact meaning of the timestamp may be slightly different for different journal
 * implementations and some journals may not even support a timestamp, and should
 * then fail the stream with `UnsupportedOperationException` if `timestamp` was
 * specified as anything else than `0L`.
 *
 * The returned event stream should be ordered by timestamp, if the journal
 * supports timestamps.
 *
 * A plugin may optionally support this [[Query]].
 */
case class EventsByTag(tag: String, timestamp: Long = 0L) extends Query[Any, Unit]

/**
 * Query hint that defines how to execute the query,
 * typically specific to the journal implementation.
 */
trait Hint

/**
 * If the journal only supports queries that are closed when they reach the
 * end of the "result set" it has to submit new queries after a while in order
 * to support "infinite" event streams that include events stored in the future.
 *
 * A plugin may optionally support this [[Hint]] for defining such a refresh interval.
 */
case class RefreshInterval(interval: FiniteDuration) extends Hint

/**
 * If the event stream is supposed to be completed immediately when it
 * reaches the end of the "result set", as described in [[RefreshInterval]].
 *
 * A plugin may optionally support this [[Hint]].
 */
case object NoRefresh extends Hint

