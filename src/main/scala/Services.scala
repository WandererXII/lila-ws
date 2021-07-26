package lishogi.ws

import scala.concurrent.duration._

import ipc.LishogiIn

final class Services(
    lishogiRedis: Lishogi,
    groupedWithin: util.GroupedWithin,
    val users: Users,
    val roomCrowd: RoomCrowd,
    val roundCrowd: RoundCrowd,
    val keepAlive: KeepAlive,
    val lobby: Lobby,
    val friends: FriendList,
    val stormSign: StormSign
) {

  def lishogi = lishogiRedis.emit

  val lag = groupedWithin[(User.ID, Int)](128, 947.millis) { lags =>
    lishogi.site(LishogiIn.Lags(lags.toMap))
  }
  val notified = groupedWithin[User.ID](40, 1001.millis) { userIds =>
    lishogi.site(LishogiIn.NotifiedBatch(userIds))
  }
  val challengePing = groupedWithin[RoomId](20, 2.seconds) { ids =>
    lishogi.challenge(LishogiIn.ChallengePings(ids.distinct))
  }
}
