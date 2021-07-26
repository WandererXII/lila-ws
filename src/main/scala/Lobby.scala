package lishogi.ws

import scala.concurrent.duration._

import ipc.ClientIn.LobbyPong
import ipc.LishogiIn

final class Lobby(
    lishogi: Lishogi,
    groupedWithin: util.GroupedWithin
) {

  private val lishogiIn = lishogi.emit.lobby

  val connect = groupedWithin[(Sri, Option[User.ID])](6, 479.millis) { connects =>
    lishogiIn(LishogiIn.ConnectSris(connects))
  }

  val disconnect = groupedWithin[Sri](50, 487.millis) { sris => lishogiIn(LishogiIn.DisconnectSris(sris)) }

  object pong {

    private var value = LobbyPong(0, 0)

    def get = value

    def update(members: Int, rounds: Int): Unit = {
      value = LobbyPong(members, rounds)
    }
  }
}
