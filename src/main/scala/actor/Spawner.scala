package lila.ws

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Props
import akka.actor.typed.Scheduler
import akka.actor.typed.SpawnProtocol
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

object Spawner {

  private val actor: Behavior[SpawnProtocol.Command] = SpawnProtocol()

  private val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(actor, "clients")
  implicit private val timeout: Timeout                  = Timeout(3.seconds)
  implicit private val scheduler: Scheduler              = system.scheduler

  def apply[B](behavior: Behavior[B]): Future[ActorRef[B]] =
    system.ask(SpawnProtocol.Spawn(behavior = behavior, name = "", props = Props.empty, _))
}
