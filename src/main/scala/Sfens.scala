package lila.ws

import java.util.concurrent.ConcurrentHashMap

import akka.actor.typed.ActorRef

import shogi.format.forsyth.Sfen
import shogi.format.usi.UciToUsi
import shogi.format.usi.Usi

import lila.ws.ipc._

/* Manages subscriptions to Sfen updates */
object Sfens {

  case class Position(lastUsi: Usi, sfen: Sfen)
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
          },
        )
        .position foreach { case Position(lastUsi, sfen) =>
        client ! ClientIn.Sfen(gameId, lastUsi, sfen)
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
        },
      )
    }

  // move coming from the server
  def move(gameId: Game.Id, json: JsonString): Unit = {
    games.computeIfPresent(
      gameId,
      (_, watched) => {
        json.value match {
          case MoveRegex(usiS, sfenS) =>
            Usi(usiS).orElse(UciToUsi(usiS)).fold(watched) { lastUsi =>
              val sfen = Sfen(sfenS)
              val msg  = ClientIn.Sfen(gameId, lastUsi, sfen)
              watched.clients foreach { _ ! msg }
              watched.copy(position = Some(Position(lastUsi, sfen)))
            }
          case _ => watched
        }
      },
    )
  }

  private val MoveRegex = """usi":"([^"]+)".+sfen":"([^"]+)""".r.unanchored

  def size = games.size
}
