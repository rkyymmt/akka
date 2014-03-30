/**
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.stream.impl

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import org.reactivestreams.api.{ Consumer, Producer }
import org.reactivestreams.spi.{ Publisher, Subscriber }
import akka.actor.ActorRef
import akka.stream.GeneratorSettings
import akka.actor.ActorLogging
import akka.actor.Actor
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import akka.actor.Props
import scala.util.control.NoStackTrace
import akka.stream.Stream

trait ActorProducerLike[T] extends Producer[T] {
  def impl: ActorRef
  override val getPublisher: Publisher[T] = {
    val a = new ActorPublisher[T](impl)
    // Resolve cyclic dependency with actor. This MUST be the first message no matter what.
    impl ! ExposedPublisher(a.asInstanceOf[ActorPublisher[Any]])
    a
  }

  def produceTo(consumer: Consumer[T]): Unit =
    getPublisher.subscribe(consumer.getSubscriber)
}

class ActorProducer[T]( final val impl: ActorRef) extends ActorProducerLike[T]

object ActorProducer {
  def props[T](settings: GeneratorSettings, f: () ⇒ T): Props =
    Props(new ActorProducerImpl(f, settings))
}

final class ActorPublisher[T](val impl: ActorRef) extends Publisher[T] {

  // The subscriber of an subscription attempt is first placed in this list of pending subscribers.
  // The actor will call takePendingSubscribers to remove it from the list when it has received the 
  // SubscribePending message. The AtomicReference is set to null by the shutdown method, which is
  // called by the actor from postStop. Pending (unregistered) subscription attempts are denied by
  // the shutdown method. Subscription attempts after shutdown can be denied immediately.
  private val pendingSubscribers = new AtomicReference[List[Subscriber[T]]](Nil)

  override def subscribe(subscriber: Subscriber[T]): Unit = {
    @tailrec def doSubscribe(subscriber: Subscriber[T]): Unit = {
      val current = pendingSubscribers.get
      if (current eq null)
        reportShutdownError(subscriber)
      else {
        if (pendingSubscribers.compareAndSet(current, subscriber :: current))
          impl ! SubscribePending
        else
          doSubscribe(subscriber) // CAS retry
      }
    }

    doSubscribe(subscriber)
  }

  def takePendingSubscribers(): List[Subscriber[T]] =
    pendingSubscribers.getAndSet(Nil)

  def shutdown(completed: Boolean): Unit = {
    this.completed = completed
    pendingSubscribers.getAndSet(null) foreach reportShutdownError
  }

  @volatile private var completed: Boolean = false

  private def reportShutdownError(subscriber: Subscriber[T]): Unit =
    if (completed) subscriber.onComplete()
    else subscriber.onError(new IllegalStateException("Cannot subscribe to shut-down spi.Publisher"))

}

class ActorSubscription[T]( final val impl: ActorRef, final val subscriber: Subscriber[T]) extends SubscriptionWithCursor[T] {
  override def requestMore(elements: Int): Unit =
    if (elements <= 0) throw new IllegalArgumentException("The number of requested elements must be > 0")
    else impl ! RequestMore(this, elements)
  override def cancel(): Unit = impl ! Cancel(this)
}

object ActorProducerImpl {
  case object Generate
}

class ActorProducerImpl[T](f: () ⇒ T, settings: GeneratorSettings) extends Actor with ActorLogging with SubscriberManagement[T] {
  import Stream._
  import ActorProducerImpl._

  type S = ActorSubscription[T]
  var pub: ActorPublisher[T] = _
  var completed = false

  context.setReceiveTimeout(settings.downstreamSubscriptionTimeout)

  def receive = {
    case ExposedPublisher(pub) ⇒
      this.pub = pub.asInstanceOf[ActorPublisher[T]]
      context.become(waitingForSubscribers)
  }

  def waitingForSubscribers: Receive = {
    case SubscribePending ⇒
      pub.takePendingSubscribers() foreach registerSubscriber
      context.setReceiveTimeout(Duration.Undefined)
      context.become(active)
  }

  def active: Receive = {
    case SubscribePending ⇒
      pub.takePendingSubscribers() foreach registerSubscriber
    case RequestMore(sub, elements) ⇒
      moreRequested(sub.asInstanceOf[S], elements)
      generate()
    case Cancel(sub) ⇒
      unregisterSubscription(sub.asInstanceOf[S])
      generate()
    case Generate ⇒
      generate()
  }

  override def postStop(): Unit = {
    pub.shutdown(completed)
  }

  private var demand = 0
  private def generate(): Unit = {
    val continue =
      try {
        pushToDownstream(f())
        true
      } catch {
        case Stop        ⇒ { completeDownstream(); completed = true; false }
        case NonFatal(e) ⇒ { abortDownstream(e); false }
      }
    demand -= 1
    if (continue && demand > 0) self ! Generate
  }

  override def initialBufferSize = settings.initialFanOutBufferSize
  override def maxBufferSize = settings.maxFanOutBufferSize

  override def createSubscription(subscriber: Subscriber[T]): ActorSubscription[T] =
    new ActorSubscription(self, subscriber)

  override def requestFromUpstream(elements: Int): Unit = demand += elements

  override def cancelUpstream(): Unit = context.stop(self)
  override def shutdown(completed: Boolean): Unit = context.stop(self)

}