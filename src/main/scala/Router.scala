package lila.ws

import scala.concurrent.Future

import io.netty.handler.codec.http.HttpResponseStatus

import lila.ws.util.RequestHeader

final class Router(controller: Controller) {

  def apply(req: RequestHeader, emit: ClientEmit): Controller.Response = {
    val path  = req.path drop 1 split '/'
    val rpath = if (path.head == "lila-ws") path.tail else path
    rpath match {
      case Array("socket") | Array("socket", _) => controller.site(req, emit)
      case Array("analysis", "socket")          => controller.site(req, emit)
      case Array("analysis", "socket", _)       => controller.site(req, emit)
      case Array("api", "socket")               => controller.api(req, emit)
      case Array("lobby", "socket")             => controller.lobby(req, emit)
      case Array("lobby", "socket", _)          => controller.lobby(req, emit)
      case Array("simul", id, "socket", _)      => controller.simul(id, req, emit)
      case Array("tournament", id, "socket", _) => controller.tournament(id, req, emit)
      case Array("study", id, "socket", _)      => controller.study(id, req, emit)
      case Array("watch", id, _, _)             => controller.roundWatch(Game.Id(id), req, emit)
      case Array("play", id, _)                 => controller.roundPlay(Game.FullId(id), req, emit)
      case Array("challenge", id, "socket", _)  => controller.challenge(Challenge.Id(id), req, emit)
      case Array("team", id)                    => controller.team(id, req, emit)
      case Array("chatroom", "socket")          => controller.chatroom(req, emit)
      case Array("chatroom", "socket", _)       => controller.chatroom(req, emit)
      case _ => Future successful Left(HttpResponseStatus.NOT_FOUND)
    }
  }
}
