package lishogi.ws

import akka.actor.typed.Scheduler
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import ipc._

final class KeepAlive(lishogi: Lishogi, scheduler: Scheduler)(implicit ec: ExecutionContext) {

  import KeepAlive._

  val study     = new AliveRooms
  val tour      = new AliveRooms
  val simul     = new AliveRooms
  val challenge = new AliveRooms
  val team      = new AliveRooms
  val swiss     = new AliveRooms

  scheduler.scheduleWithFixedDelay(15.seconds, 15.seconds) { () =>
    lishogi.emit.study(study.getAndClear)
    lishogi.emit.tour(tour.getAndClear)
    lishogi.emit.simul(simul.getAndClear)
    lishogi.emit.challenge(challenge.getAndClear)
    lishogi.emit.team(team.getAndClear)
    lishogi.emit.swiss(swiss.getAndClear)
  }
}

object KeepAlive {

  type Seconds = Int

  final class AliveRooms {

    private val rooms = collection.mutable.Set[RoomId]()

    def apply(roomId: RoomId) = rooms += roomId

    def getAndClear: LishogiIn.KeepAlives = {
      val ret = LishogiIn.KeepAlives(rooms.toSet)
      rooms.clear
      ret
    }
  }
}
