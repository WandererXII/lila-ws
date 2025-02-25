package lila.ws

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.Behaviors

import lila.ws.ipc._

object SiteClientActor {

  import ClientActor._

  def start(deps: Deps): Behavior[ClientMsg] =
    Behaviors.setup { ctx =>
      import deps._
      onStart(deps, ctx)
      req.user foreach { users.connect(_, ctx.self) }
      apply(State(), deps)
    }

  private def apply(state: State, deps: Deps): Behavior[ClientMsg] =
    Behaviors
      .receive[ClientMsg] { (ctx, msg) =>
        msg match {

          case ctrl: ClientCtrl => socketControl(state, deps, ctrl)

          case in: ClientIn =>
            clientInReceive(state, deps, in) match {
              case None    => Behaviors.same
              case Some(s) => apply(s, deps)
            }

          case msg: ClientOutSite =>
            val newState = globalReceive(state, deps, ctx, msg)
            if (newState == state) Behaviors.same
            else apply(newState, deps)

          case _ =>
            Monitor.clientOutUnhandled("site").increment()
            Behaviors.same
        }

      }
      .receiveSignal { case (ctx, PostStop) =>
        onStop(state, deps, ctx)
        Behaviors.same
      }
}
