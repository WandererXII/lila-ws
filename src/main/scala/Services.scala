package lila.ws

import scala.concurrent.duration._

import lila.ws.ipc.LilaIn

final class Services(
    lilaRedis: Lila,
    groupedWithin: util.GroupedWithin,
    val users: Users,
    val roomCrowd: RoomCrowd,
    val roundCrowd: RoundCrowd,
    val keepAlive: KeepAlive,
    val lobby: Lobby,
    val friends: FriendList,
    val stormSign: StormSign,
) {

  def lila = lilaRedis.emit

  val lag = groupedWithin[(User.ID, Int)](128, 947.millis) { lags =>
    lila.site(LilaIn.Lags(lags.toMap))
  }
  val notified = groupedWithin[User.ID](40, 1001.millis) { userIds =>
    lila.site(LilaIn.NotifiedBatch(userIds))
  }
  val challengePing = groupedWithin[RoomId](20, 2.seconds) { ids =>
    lila.challenge(LilaIn.ChallengePings(ids.distinct))
  }
}
