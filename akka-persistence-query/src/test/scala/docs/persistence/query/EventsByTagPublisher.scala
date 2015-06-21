/**
 * Copyright (C) 2015 Typesafe Inc. <http://www.typesafe.com>
 */
package docs.persistence.query

import scala.annotation.tailrec
import scala.concurrent.duration._
import akka.actor.Props
import akka.persistence.PersistentRepr
import akka.serialization.SerializationExtension
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.actor.ActorPublisherMessage.Request
import com.typesafe.config._
import akka.persistence.PersistentRepr
import akka.persistence.query.EventEnvelope

object EventsByTagPublisher {
  def props(tag: String, offset: Long, refreshInterval: FiniteDuration): Props =
    Props(new EventsByTagPublisher(tag, offset, refreshInterval))

  private case object Continue
}

class EventsByTagPublisher(tag: String, offset: Long, refreshInterval: FiniteDuration)
  extends ActorPublisher[EventEnvelope] {
  import EventsByTagPublisher._

  private val limit = 1000

  private var currentId = 0L
  var buf = Vector.empty[EventEnvelope]

  import context.dispatcher
  val continueTask = context.system.scheduler.schedule(
    refreshInterval, refreshInterval, self, Continue)

  override def postStop(): Unit = {
    continueTask.cancel()
  }

  def receive = {
    case _: Request | Continue ⇒
      query()
      deliverBuf()

    case Cancel ⇒
      context.stop(self)
  }

  def query(): Unit =
    if (buf.isEmpty) {
      try {
        // Could be an SQL query, for example:
        //      "SELECT id, persistent_repr FROM journal WHERE tag = like ? and " +
        //        "id >= ? ORDER BY id limit ?"
        val result: Vector[(Long, Array[Byte])] = ???
        currentId = if (result.nonEmpty) result.last._1 else currentId
        val serialization = SerializationExtension(context.system)

        buf = result.map {
          case (id, bytes) ⇒
            val p = serialization.deserialize(bytes, classOf[PersistentRepr]).get
            EventEnvelope(offset = id, p.persistenceId, p.sequenceNr, p.payload)
        }
      } catch {
        case e: Exception ⇒
          onErrorThenStop(e)
      }
    }

  @tailrec final def deliverBuf(): Unit =
    if (totalDemand > 0 && buf.nonEmpty) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliverBuf()
      }
    }
}
