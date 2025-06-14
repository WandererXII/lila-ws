package lila.ws

import com.typesafe.scalalogging.Logger
import play.api.libs.json._

import shogi.Pos
import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.format.usi.UsiCharPair

import lila.ws.ipc._

object Shogi {

  private val logger = Logger(getClass)

  def apply(req: ClientOut.AnaUsi): ClientIn = {
    Monitor.time(_.shogiMoveTime) {
      try {
        shogi
          .Game(Some(req.sfen), req.variant)(req.usi)
          .toOption map { game =>
          {

            makeNode(game, req.usi, req.path, req.chapterId)
          }
        } getOrElse ClientIn.StepFailure
      } catch {
        case e: java.lang.ArrayIndexOutOfBoundsException =>
          logger.warn(s"${req.sfen} ${req.variant} ${req.usi}", e)
          ClientIn.StepFailure
      }
    }
  }

  private def makeNode(
      game: shogi.Game,
      usi: Usi,
      path: Path,
      chapterId: Option[ChapterId],
  ): ClientIn.Node = {
    val sfen = game.toSfen
    ClientIn.Node(
      path = path,
      id = UsiCharPair(usi, game.variant),
      ply = game.plies,
      usi = usi,
      sfen = sfen,
      check = game.situation.check,
      chapterId = chapterId,
    )
  }

  object json {
    implicit val sfenWrite: Writes[Sfen] = Writes[Sfen] { sfen => JsString(sfen.value) }
    implicit val pathWrite: Writes[Path] = Writes[Path] { path => JsString(path.value) }
    implicit val usiWrite: Writes[Usi]   = Writes[Usi] { usi => JsString(usi.usi) }
    implicit val usiCharPairWrite: Writes[UsiCharPair] = Writes[UsiCharPair] { ucp =>
      JsString(ucp.toString)
    }
    implicit val posWrite: Writes[Pos]             = Writes[Pos] { pos => JsString(pos.key) }
    implicit val chapterIdWrite: Writes[ChapterId] = Writes[ChapterId] { ch => JsString(ch.value) }
  }
}
