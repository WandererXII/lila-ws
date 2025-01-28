package lila.ws

import scala.concurrent.duration._

import com.github.blemale.scaffeine.Cache
import com.github.blemale.scaffeine.Scaffeine

import lila.ws.ipc._

object Tv {

  private val fast: Cache[String, Boolean] = Scaffeine()
    .expireAfterWrite(10.minutes)
    .build[String, Boolean]()

  private val slow: Cache[String, Boolean] = Scaffeine()
    .expireAfterWrite(2.hours)
    .build[String, Boolean]()

  def select(out: LilaOut.TvSelect): Unit = {
    val cliMsg = ClientIn.tvSelect(out.json)
    List(fast, slow) foreach { in =>
      in.asMap().keys foreach { gameId =>
        Bus.publish(_ room RoomId(gameId), cliMsg)
      }
    }
    (if (out.speed <= shogi.Speed.Bullet) fast else slow).put(out.gameId.value, true)
  }

  def get(gameId: Game.Id): Boolean = get(gameId, fast) || get(gameId, slow)

  private def get(gameId: Game.Id, in: Cache[String, Boolean]): Boolean =
    isNotNull(in.underlying.getIfPresent(gameId.value))

  @inline private def isNotNull[A](a: A) = a != null
}
