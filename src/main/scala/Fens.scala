package lila.ws

import akka.actor.typed.ActorRef
import shogi.format.{ FEN, Uci }
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

  private val MoveRegex = """uci":"([^"]+)".+fen":"([^"]+)".+pockets":(\[[^\[]+\])""".r.unanchored

  def size = games.size
}
