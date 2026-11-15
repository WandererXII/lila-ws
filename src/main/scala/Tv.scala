package lila.ws

import scala.concurrent.duration._

import com.github.blemale.scaffeine.Cache
import com.github.blemale.scaffeine.Scaffeine

import lila.ws.ipc._

object Tv {

  private val cache: Cache[String, Boolean] = Scaffeine()
    .expireAfterWrite(2.hours)
    .maximumSize(2_048)
    .build[String, Boolean]()

  def select(out: LilaOut.TvSelect): Unit = {
    val cliMsg = ClientIn.tvSelect(out.json)
    cache.asMap().keys foreach { gameId =>
      Bus.publish(_ room RoomId(gameId), cliMsg)
    }
    cache.put(out.gameId.value, true)
  }
}
