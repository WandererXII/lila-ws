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
    util.SafeSchedule.guard("keepAlive.study")(study.getAndClear foreach lila.emit.study)
    util.SafeSchedule.guard("keepAlive.tour")(tour.getAndClear foreach lila.emit.tour)
    util.SafeSchedule.guard("keepAlive.simul")(simul.getAndClear foreach lila.emit.simul)
    util.SafeSchedule.guard("keepAlive.challenge")(
      challenge.getAndClear foreach lila.emit.challenge,
    )
    util.SafeSchedule.guard("keepAlive.team")(team.getAndClear foreach lila.emit.team)
    util.SafeSchedule.guard("keepAlive.chatroom")(chatroom.getAndClear foreach lila.emit.chatroom)
  }
}

object KeepAlive {

  type Seconds = Int

  final class AliveRooms {

    private val rooms = java.util.concurrent.ConcurrentHashMap.newKeySet[RoomId]()

    def apply(roomId: RoomId): Unit = rooms.add(roomId)

    // ids are either included in this batch or kept for the next one
    // None when there is nothing to send: an empty batch would be written as "-"
    def getAndClear: Option[LilaIn.KeepAlives] = {
      val drained = Set.newBuilder[RoomId]
      val it      = rooms.iterator()
      while (it.hasNext) {
        drained += it.next()
        it.remove()
      }
      val ids = drained.result()
      if (ids.isEmpty) None else Some(LilaIn.KeepAlives(ids))
    }
  }
}
