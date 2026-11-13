package lila.ws

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.typed.Scheduler

import lila.ws.ipc._

final class KeepAlive(lila: Lila, scheduler: Scheduler)(implicit ec: ExecutionContext) {

  import KeepAlive._

  val study     = new AliveRooms
  val tour      = new AliveRooms
  val simul     = new AliveRooms
  val challenge = new AliveRooms
  val team      = new AliveRooms
  val chatroom  = new AliveRooms

  util.SafeSchedule.withFixedDelay(scheduler, "keepAlive", 15.seconds, 15.seconds) {
    // guard each emit so one failure doesn't skip the remaining room types
    util.SafeSchedule.guard("keepAlive.study")(lila.emit.study(study.getAndClear))
    util.SafeSchedule.guard("keepAlive.tour")(lila.emit.tour(tour.getAndClear))
    util.SafeSchedule.guard("keepAlive.simul")(lila.emit.simul(simul.getAndClear))
    util.SafeSchedule.guard("keepAlive.challenge")(lila.emit.challenge(challenge.getAndClear))
    util.SafeSchedule.guard("keepAlive.team")(lila.emit.team(team.getAndClear))
    util.SafeSchedule.guard("keepAlive.chatroom")(lila.emit.chatroom(chatroom.getAndClear))
  }
}

object KeepAlive {

  type Seconds = Int

  final class AliveRooms {

    private val rooms = java.util.concurrent.ConcurrentHashMap.newKeySet[RoomId]()

    def apply(roomId: RoomId): Unit = rooms.add(roomId)

    // ids are either included in this batch or kept for the next one
    def getAndClear: LilaIn.KeepAlives = {
      val drained = Set.newBuilder[RoomId]
      val it      = rooms.iterator()
      while (it.hasNext) {
        drained += it.next()
        it.remove()
      }
      LilaIn.KeepAlives(drained.result())
    }
  }
}
