package lila.ws

import scala.concurrent.Promise

import akka.actor.typed.scaladsl.Behaviors

object Clients {

  sealed trait Control
  final case class Start(behavior: ClientBehavior, promise: Promise[Client]) extends Control
  final case class Stop(client: Client)                                      extends Control

  def behavior =
    Behaviors.receive[Control] { (ctx, msg) =>
      msg match {
        case Start(behavior, promise) =>
          promise success ctx.spawnAnonymous(behavior)
          Behaviors.same
        case Stop(client) =>
          ctx.stop(client)
          Behaviors.same
      }
    }
}
