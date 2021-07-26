package lishogi.ws

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, PostStop }
import play.api.libs.json.JsValue

import ipc._

object LobbyClientActor {

  import ClientActor._

  case class State(
      idle: Boolean = false,
      site: ClientActor.State = ClientActor.State()
  )

  def start(deps: Deps): Behavior[ClientMsg] =
    Behaviors.setup { ctx =>
      import deps._
      onStart(deps, ctx)
      req.user foreach { users.connect(_, ctx.self, silently = true) }
      services.lobby.connect(req.sri -> req.user.map(_.id))
      Bus.subscribe(Bus.channel.lobby, ctx.self)
      apply(State(), deps)
    }

  private def apply(state: State, deps: Deps): Behavior[ClientMsg] =
    Behaviors
      .receive[ClientMsg] { (ctx, msg) =>
        import deps._

        def forward(payload: JsValue): Unit =
          lishogiIn.lobby(LishogiIn.TellSri(req.sri, req.user.map(_.id), payload))

        msg match {

          case ctrl: ClientCtrl => socketControl(state.site, deps, ctrl)

          case ClientIn.LobbyNonIdle(payload) =>
            if (!state.idle) clientIn(payload)
            Behaviors.same

          case ClientIn.OnlyFor(endpoint, payload) =>
            if (endpoint == ClientIn.OnlyFor.Lobby) clientIn(payload)
            Behaviors.same

          case in: ClientIn =>
            clientInReceive(state.site, deps, in) match {
              case None    => Behaviors.same
              case Some(s) => apply(state.copy(site = s), deps)
            }

          case msg: ClientOut.Ping =>
            clientIn(services.lobby.pong.get)
            apply(state.copy(site = sitePing(state.site, deps, msg)), deps)

          case ClientOut.LobbyForward(payload) =>
            forward(payload)
            Behaviors.same

          case ClientOut.Idle(value, payload) =>
            forward(payload)
            apply(state.copy(idle = value), deps)

          // default receive (site)
          case msg: ClientOutSite =>
            val siteState = globalReceive(state.site, deps, ctx, msg)
            if (siteState == state.site) Behaviors.same
            else apply(state.copy(site = siteState), deps)

          case _ =>
            Monitor.clientOutUnhandled("lobby").increment()
            Behaviors.same
        }

      }
      .receiveSignal {
        case (ctx, PostStop) =>
          onStop(state.site, deps, ctx)
          Bus.unsubscribe(Bus.channel.lobby, ctx.self)
          deps.services.lobby.disconnect(deps.req.sri)
          Behaviors.same
      }
}
