package lila.ws
package util

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import com.typesafe.scalalogging.Logger

// Akka cancels a fixed-delay task as soon as one run throws.
// These helpers log non-fatal exceptions instead, so the task keeps running.
object SafeSchedule {

  private val logger = Logger(getClass)

  def guard(name: String)(f: => Unit): Unit =
    try f
    catch {
      case NonFatal(e) => logger.error(s"Scheduled task $name failed", e)
    }

  def withFixedDelay(
      scheduler: Scheduler,
      name: String,
      initialDelay: FiniteDuration,
      delay: FiniteDuration,
  )(f: => Unit)(implicit ec: ExecutionContext): Cancellable =
    scheduler.scheduleWithFixedDelay(initialDelay, delay) { () => guard(name)(f) }
}
