package lishogi.ws

import shogi.Color
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import ipc._

final class RoundCrowd(
    lishogi: Lishogi,
    json: CrowdJson,
    groupedWithin: util.GroupedWithin
)(implicit ec: ExecutionContext) {

  import RoundCrowd._

  private val rounds = new ConcurrentHashMap[RoomId, RoundState](32768)

  def connect(roomId: RoomId, user: Option[User], player: Option[Color]): Unit =
    publish(
      roomId,
      rounds.compute(roomId, (_, cur) => Option(cur).getOrElse(RoundState()).connect(user, player))
    )

  def disconnect(roomId: RoomId, user: Option[User], player: Option[Color]): Unit = {
    rounds.computeIfPresent(
      roomId,
      (_, round) => {
        val newRound = round.disconnect(user, player)
        publish(roomId, newRound)
        if (newRound.isEmpty) null else newRound
      }
    )
  }

  def botOnline(roomId: RoomId, color: Color, online: Boolean): Unit =
    rounds.compute(
      roomId,
      (_, cur) => {
        Option(cur).getOrElse(RoundState()).botOnline(color, online) match {
          case None => cur
          case Some(round) =>
            publish(roomId, round)
            if (round.isEmpty) null else round
        }
      }
    )

  def getUsers(roomId: RoomId): Set[User.ID] =
    Option(rounds get roomId).fold(Set.empty[User.ID])(_.room.users.keySet)

  def isPresent(roomId: RoomId, userId: User.ID): Boolean =
    Option(rounds get roomId).exists(_.room.users contains userId)

  private def publish(roomId: RoomId, round: RoundState): Unit =
    outputBatch(outputOf(roomId, round))

  private val outputBatch = groupedWithin[Output](256, 500.millis) { outputs =>
    val aggregated = outputs
      .foldLeft(Map.empty[RoomId, Output]) {
        case (crowds, crowd) => crowds.updated(crowd.room.roomId, crowd)
      }
      .values
    lishogi.emit.round(LishogiIn.RoundOnlines(aggregated))
    aggregated foreach { output =>
      json round output foreach {
        Bus.publish(_ room output.room.roomId, _)
      }
    }
  }

  def size = rounds.size
}

object RoundCrowd {

  case class Output(room: RoomCrowd.Output, players: Color.Map[Int]) {
    def isEmpty = room.members == 0 && players.sente == 0 && players.gote == 0
  }

  def outputOf(roomId: RoomId, round: RoundState) =
    Output(
      room = RoomCrowd.outputOf(roomId, round.room),
      players = round.players
    )

  case class RoundState(
      room: RoomCrowd.RoomState = RoomCrowd.RoomState(),
      players: Color.Map[Int] = Color.Map(0, 0)
  ) {
    def connect(user: Option[User], player: Option[Color]) =
      copy(
        room = if (player.isDefined) room else room connect user,
        players = player.fold(players)(c => players.update(c, _ + 1))
      )
    def disconnect(user: Option[User], player: Option[Color]) =
      copy(
        room = if (player.isDefined) room else room disconnect user,
        players = player.fold(players)(c => players.update(c, _ - 1))
      )
    def botOnline(color: Color, online: Boolean): Option[RoundState] =
      if (online == players(color) > 0) None
      else if (online) Some(connect(None, Some(color)))
      else Some(disconnect(None, Some(color)))
    def isEmpty = room.isEmpty && players.forall(1 > _)
  }
}
