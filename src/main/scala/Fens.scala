package lila.ws

import akka.actor.typed.ActorRef
import chess.format.{ FEN, Uci }
import java.util.concurrent.ConcurrentHashMap

import ipc._

/* Manages subscriptions to FEN updates */
object Fens {

  case class Position(lastUci: Uci, fen: FEN, pocketJson: String)
  case class Watched(position: Option[Position], clients: Set[ActorRef[ClientMsg]])

  private val games = new ConcurrentHashMap[Game.Id, Watched](1024)

  // client starts watching
  def watch(gameIds: Iterable[Game.Id], client: Client): Unit =
    gameIds foreach { gameId =>
      games
        .compute(
          gameId,
          {
            case (_, null)                  => Watched(None, Set(client))
            case (_, Watched(pos, clients)) => Watched(pos, clients + client)
          }
        )
        .position foreach {
        case Position(lastUci, fen, pocketJson) => client ! ClientIn.Fen(gameId, lastUci, fen, pocketJson)
      }
    }

  // when a client disconnects
  def unwatch(gameIds: Iterable[Game.Id], client: Client): Unit =
    gameIds foreach { gameId =>
      games.computeIfPresent(
        gameId,
        (_, watched) => {
          val newClients = watched.clients - client
          if (newClients.isEmpty) null
          else watched.copy(clients = newClients)
        }
      )
    }

  // move coming from the server
  def move(gameId: Game.Id, json: JsonString): Unit = {
    games.computeIfPresent(
      gameId,
      (_, watched) => {
        println(json.value)
        json.value match {
          case MoveRegex(uciS, fenS, pocketJson) =>
            Uci(uciS).fold(watched) { lastUci =>
              val fen = FEN(fenS)
              val msg = ClientIn.Fen(gameId, lastUci, fen, pocketJson)
              watched.clients foreach { _ ! msg }
              watched.copy(position = Some(Position(lastUci, fen, pocketJson)))
            }
          case _ => watched
        }
      }
    )
  }

  // {"uci":"g9f8","san":"Sxf8","fen":"lnsgkg1nl/1r3s1b1/p1pppp2p/9/9/9/P1PPPPP1P/1t5R1/LNSGKGSNL","ply":12,"dests":{"a1":"a2","f1":"f2e2g2","g3":"g4","c3":"c4","g1":"g2f2","d3":"d4","c1":"c2b2d2","e3":"e4","d1":"d2c2e2","a3":"a4","h2":"h3h4h5h6h7h8g2f2e2d2c2b2i2h7h8","i1":"i2","e1":"e2d2f2","i3":"i4","f3":"f4"},"clock":{"white":10753,"black":10735.84,"wPer":0,"bPer":0,"lag":6},"crazyhouse":{"pockets":[{"pawn":2},{"pawn":2,"bishop":1}]}}
  private val MoveRegex = """uci":"([^"]+)".+fen":"([^"]+)".+pockets":(\[[^\[]+\])""".r.unanchored

  def size = games.size
}
