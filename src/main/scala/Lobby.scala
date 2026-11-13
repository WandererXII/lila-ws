package lila.ws

import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

import lila.ws.ipc.ClientIn.LobbyPong
import lila.ws.ipc.LilaIn

final class Lobby(
    lila: Lila,
    groupedWithin: util.GroupedWithin,
) {

  private val presence = new Lobby.Presence(lila.emit.lobby, groupedWithin)

  def connect(sri: Sri, userId: Option[User.ID]): Unit = presence.connect(sri, userId)

  def disconnect(sri: Sri): Unit = presence.disconnect(sri)

  object pong {

    @volatile private var value = LobbyPong(0, 0)

    def get = value

    def update(members: Int, rounds: Int): Unit = {
      value = LobbyPong(members, rounds)
    }
  }
}

object Lobby {

  sealed private[ws] trait Event
  final private[ws] case class Connect(sri: Sri, userId: Option[User.ID]) extends Event
  final private[ws] case class Disconnect(sri: Sri)                       extends Event

  // A client may reconnect with the same SRI before its previous socket is closed,
  // so lila must only see a disconnect once the last socket of that SRI is gone.
  // Connects and disconnects share one batch so lila receives them in the order they happened.
  final private[ws] class Presence(
      lilaIn: Emit[LilaIn.Lobby],
      groupedWithin: util.GroupedWithin,
  ) {

    private val sockets = new ConcurrentHashMap[Sri, Integer](8192)

    private val logger = Logger(getClass)

    private val batch = groupedWithin[Event](50, 479.millis)(emit)

    // Enqueue inside compute so the count transition and its event are ordered together.
    def connect(sri: Sri, userId: Option[User.ID]): Unit =
      sockets.compute(
        sri,
        (_, count) => {
          batch(Connect(sri, userId))
          if (count == null) 1 else count + 1
        },
      )

    def disconnect(sri: Sri): Unit =
      sockets.computeIfPresent(
        sri,
        (_, count) =>
          if (count <= 1) {
            batch(Disconnect(sri))
            null
          } else count - 1,
      )

    def connections(sri: Sri): Int = Option(sockets get sri).fold(0)(_.intValue)

    // Never throw: a flush can run inside compute, where an exception would roll back the
    // count after the event was already buffered, and would stop the batch timer.
    private def emit(events: Vector[Event]): Unit =
      try emitRuns(events)
      catch { case NonFatal(e) => logger.error(s"Failed to emit ${events.size} lobby events", e) }

    @tailrec private def emitRuns(events: Vector[Event]): Unit =
      events.headOption match {
        case None             => ()
        case Some(_: Connect) =>
          val (run, rest) = events.span(_.isInstanceOf[Connect])
          lilaIn(LilaIn.ConnectSris(run.collect { case Connect(sri, userId) => sri -> userId }))
          emitRuns(rest)
        case Some(_: Disconnect) =>
          val (run, rest) = events.span(_.isInstanceOf[Disconnect])
          lilaIn(LilaIn.DisconnectSris(run.collect { case Disconnect(sri) => sri }))
          emitRuns(rest)
      }
  }
}
