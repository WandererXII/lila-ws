package lila.ws

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

import lila.ws.ipc._

object ApiActor {

  def start(deps: Deps): Behavior[ClientMsg] =
    Behaviors.setup { ctx =>
      deps.services.users.connect(deps.user, ctx.self)
      LilaWsServer.connections.incrementAndGet
      apply(deps)
    }

  def onStop(deps: Deps, ctx: ActorContext[ClientMsg]): Unit = {
    import deps._
    LilaWsServer.connections.decrementAndGet
    services.users.disconnect(user, ctx.self)
    services.friends.onClientStop(user.id)
  }

  private def apply(deps: Deps): Behavior[ClientMsg] =
    Behaviors
      .receive[ClientMsg] { (_, msg) =>
        msg match {

          case ClientCtrl.ApiDisconnect => Behaviors.stopped

          case _ =>
            Monitor.clientOutUnhandled("api").increment()
            Behaviors.same
        }

      }
      .receiveSignal { case (ctx, PostStop) =>
        onStop(deps, ctx)
        Behaviors.same
      }

  case class Deps(user: User, services: Services)
}
