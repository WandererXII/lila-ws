package lila.ws

import cats.syntax.option._

import play.api.libs.json._

import shogi.format.forsyth.Sfen
import shogi.format.usi.{ Usi, UsiCharPair }
import shogi.opening.{ FullOpening, FullOpeningDB }
import shogi.Pos
import shogi.variant.Variant
import com.typesafe.scalalogging.Logger

import ipc._

object Shogi {

  private val logger = Logger(getClass)

  def apply(req: ClientOut.AnaUsi): ClientIn = {
    Monitor.time(_.shogiMoveTime) {
      try {
        shogi
          .Game(req.variant.some, Some(req.sfen))(req.usi)
          .toOption map { game => {

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

  def apply(req: ClientOut.Opening): Option[ClientIn.Opening] =
    if (Variant.openingSensibleVariants(req.variant))
      FullOpeningDB findBySfen req.sfen map {
        ClientIn.Opening(req.path, _)
      }
    else None

  private def makeNode(
      game: shogi.Game,
      usi: Usi,
      path: Path,
      chapterId: Option[ChapterId]
  ): ClientIn.Node = {
    val sfen     = game.toSfen
    ClientIn.Node(
      path = path,
      id = UsiCharPair(usi, game.variant),
      ply = game.plies,
      usi = usi,
      sfen = sfen,
      check = game.situation.check,
      opening =
        if (game.plies <= 30 && Variant.openingSensibleVariants(game.variant))
          FullOpeningDB findBySfen sfen
        else None,
      chapterId = chapterId
    )
  }

  object json {
    implicit val sfenWrite        = Writes[Sfen] { sfen => JsString(sfen.value) }
    implicit val pathWrite        = Writes[Path] { path => JsString(path.value) }
    implicit val usiWrite         = Writes[Usi] { usi => JsString(usi.usi) }
    implicit val usiCharPairWrite = Writes[UsiCharPair] { ucp => JsString(ucp.toString) }
    implicit val posWrite         = Writes[Pos] { pos => JsString(pos.usiKey) }
    implicit val chapterIdWrite   = Writes[ChapterId] { ch => JsString(ch.value) }
    implicit val openingWrite = Writes[FullOpening] { o =>
      Json.obj(
        "japanese"  -> o.japanese,
        "english" -> o.english
      )
    }
  }
}
