package lishogi.ws

import play.api.libs.json._

import shogi.format.{ FEN, Uci, UciCharPair }
import shogi.opening.{ FullOpening, FullOpeningDB }
import shogi.Pos
import shogi.Role
import shogi.{Hand, Hands}
import shogi.variant.{ Standard, Variant }
import com.typesafe.scalalogging.Logger

import ipc._

object Shogi {

  private val logger = Logger(getClass)

  def apply(req: ClientOut.AnaMove): ClientIn = {
    Monitor.time(_.shogiMoveTime) {
      try {
        shogi
          .Game(req.variant.some, Some(req.fen.value))(req.orig, req.dest, req.promotion)
          .toOption flatMap {
          case (game, move) => {
            game.pgnMoves.lastOption map { san =>
              makeNode(game, Uci.WithSan(Uci(move), san), req.path, req.chapterId)
            }
          }
        } getOrElse ClientIn.StepFailure
      } catch {
        case e: java.lang.ArrayIndexOutOfBoundsException =>
          logger.warn(s"${req.fen} ${req.variant} ${req.orig}${req.dest}", e)
          ClientIn.StepFailure
      }
    }
  }

  def apply(req: ClientOut.AnaDrop): ClientIn =
    Monitor.time(_.shogiMoveTime) {
      try {
        shogi.Game(req.variant.some, Some(req.fen.value)).drop(req.role, req.pos).toOption flatMap {
          case (game, drop) =>
            game.pgnMoves.lastOption map { san =>
              makeNode(game, Uci.WithSan(Uci(drop), san), req.path, req.chapterId)
            }
        } getOrElse ClientIn.StepFailure
      } catch {
        case e: java.lang.ArrayIndexOutOfBoundsException =>
          logger.warn(s"${req.fen} ${req.variant} ${req.role}*${req.pos}", e)
          ClientIn.StepFailure
      }
    }

  def apply(req: ClientOut.AnaDests): ClientIn.Dests =
    Monitor.time(_.shogiDestTime) {
      ClientIn.Dests(
        path = req.path,
        dests = {
          if (req.variant.standard && req.fen.value == shogi.format.Forsyth.initial && req.path.value.isEmpty)
            initialDests
          else {
            val sit = shogi.Game(req.variant.some, Some(req.fen.value)).situation
            if (sit.playable(false)) json.destString(sit.destinations) else ""
          }
        },
        opening = {
          if (Variant.openingSensibleVariants(req.variant)) FullOpeningDB findByFen req.fen
          else None
        },
        chapterId = req.chapterId
      )
    }

  def apply(req: ClientOut.Opening): Option[ClientIn.Opening] =
    if (Variant.openingSensibleVariants(req.variant))
      FullOpeningDB findByFen req.fen map {
        ClientIn.Opening(req.path, _)
      }
    else None

  private def makeNode(
      game: shogi.Game,
      move: Uci.WithSan,
      path: Path,
      chapterId: Option[ChapterId]
  ): ClientIn.Node = {
    val movable = game.situation playable false
    val fen     = FEN(shogi.format.Forsyth >> game)
    ClientIn.Node(
      path = path,
      id = UciCharPair(move.uci),
      ply = game.turns,
      move = move,
      fen = fen,
      check = game.situation.check,
      dests = if (movable) game.situation.destinations else Map.empty,
      opening =
        if (game.turns <= 30 && Variant.openingSensibleVariants(game.board.variant))
          FullOpeningDB findByFen fen
        else None,
      drops = if (movable) game.situation.drops else Some(Nil),
      crazyData = game.situation.board.crazyData,
      chapterId = chapterId
    )
  }

  private val initialDests = "aj fonp uD gpo vE clm wF dmln sB qponmlr ir tC enmo yH zI AJ xG"

  object json {
    implicit val fenWrite         = Writes[FEN] { fen => JsString(fen.value) }
    implicit val pathWrite        = Writes[Path] { path => JsString(path.value) }
    implicit val uciWrite         = Writes[Uci] { uci => JsString(uci.uci) }
    implicit val uciCharPairWrite = Writes[UciCharPair] { ucp => JsString(ucp.toString) }
    implicit val posWrite         = Writes[Pos] { pos => JsString(pos.key) }
    implicit val chapterIdWrite   = Writes[ChapterId] { ch => JsString(ch.value) }
    implicit val openingWrite = Writes[FullOpening] { o =>
      Json.obj(
        "eco"  -> o.eco,
        "name" -> o.name
      )
    }
    implicit val destsJsonWriter: Writes[Map[Pos, List[Pos]]] = Writes { dests =>
      JsString(destString(dests))
    }
    def destString(dests: Map[Pos, List[Pos]]): String = {
      val sb    = new java.lang.StringBuilder(80)
      var first = true
      dests foreach {
        case (orig, dests) =>
          if (first) first = false
          else sb append " "
          sb append orig.piotr
          dests foreach { sb append _.piotr }
      }
      sb.toString
    }

  implicit val crazyhousePocketWriter: OWrites[Hand] = OWrites { h =>
    JsObject(
      h.roleMap.filter(kv => 0 < kv._2).map { kv =>
        kv._1.name -> JsNumber(kv._2)
      }
    )
  }

  implicit val crazyhouseDataWriter: OWrites[Hands] = OWrites { v => 
    Json.obj("pockets" -> List(v.sente, v.gote))
    }
  }
}
