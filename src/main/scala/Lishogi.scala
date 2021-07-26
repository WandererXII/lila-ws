package lishogi.ws

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.lettuce.core._
import io.lettuce.core.pubsub._
import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }

import ipc._

final class Lishogi(config: Config)(implicit ec: ExecutionContext) {

  import Lishogi._

  object status {
    private var value: Status = Online
    def setOffline() = { value = Offline }
    def setOnline(init: () => Unit) = {
      value = Online
      init()
      buffer.flush()
    }
    def isOnline: Boolean = value == Online
  }

  private object buffer {
    case class Buffered(chan: String, msg: String)
    private val queue = new ConcurrentLinkedQueue[Buffered]()

    def enqueue(chan: String, msg: String) = {
      queue offer Buffered(chan, msg)
    }
    @tailrec def flush(): Unit = {
      val next = queue.poll()
      if (next != null) {
        connIn.async.publish(next.chan, next.msg)
        flush()
      }
    }
  }

  private val logger  = Logger(getClass)
  private val redis   = RedisClient create RedisURI.create(config.getString("redis.uri"))
  private val connIn  = redis.connectPubSub
  private val connOut = redis.connectPubSub

  private val handlersPromise                  = Promise[Handlers]
  private val futureHandlers: Future[Handlers] = handlersPromise.future
  private var handlers: Handlers               = chan => out => futureHandlers foreach { _(chan)(out) }
  def setHandlers(hs: Handlers) = {
    handlers = hs
    handlersPromise success hs
  }

  val emit: Emits = Await.result(
    util.Chronometer(connectAll).lap.map { lap =>
      logger.info(s"Redis connection took ${lap.showDuration}")
      lap.result
    },
    3.seconds
  )

  private def connectAll: Future[Emits] =
    connect[LishogiIn.Site](chans.site) zip
      connect[LishogiIn.Tour](chans.tour) zip
      connect[LishogiIn.Lobby](chans.lobby) zip
      connect[LishogIn.Simul](chans.simul) zip
      connect[LishogiIn.Team](chans.team) zip
      connect[LishogiIn.Swiss](chans.swiss) zip
      connect[LishogiIn.Study](chans.study) zip
      connect[LishogiIn.Round](chans.round) zip
      connect[LishogiIn.Challenge](chans.challenge) map {
      case site ~ tour ~ lobby ~ simul ~ team ~ swiss ~ study ~ round ~ challenge =>
        new Emits(
          site,
          tour,
          lobby,
          simul,
          team,
          swiss,
          study,
          round,
          challenge
        )
    }

  private def connect[In <: LishogiIn](chan: Chan): Future[Emit[In]] = {

    val emit: Emit[In] = in => {
      val msg  = in.write
      val path = msg.takeWhile(' '.!=)
      if (status.isOnline) {
        connIn.async.publish(chan.in, msg)
        Monitor.redis.in(chan.in, path)
      } else if (in.critical) {
        buffer.enqueue(chan.in, msg)
        Monitor.redis.queue(chan.in, path)
      } else {
        Monitor.redis.drop(chan.in, path)
      }
    }

    val promise = Promise[Emit[In]]

    connOut.async.subscribe(chan.out) thenRun { () =>
      connIn.async.publish(chan.in, LishogiIn.WsBoot.write)
      promise success emit
    }

    promise.future
  }

  connOut.addListener(new RedisPubSubAdapter[String, String] {
    override def message(chan: String, msg: String): Unit = {
      Monitor.redis.out(chan, msg.takeWhile(' '.!=))
      LishogiOut read msg match {
        case Some(out) => handlers(chan)(out)
        case None      => logger.warn(s"Can't parse $msg on $chan")
      }
    }
  })

  def close(): Unit = {
    connIn.close()
    connOut.close()
  }
}

object Lishog {

  sealed trait Status
  case object Online  extends Status
  case object Offline extends Status

  type Handlers = String => Emit[LishogiOut]

  sealed abstract class Chan(value: String) {
    val in  = s"$value-in"
    val out = s"$value-out"
  }

  object chans {
    object site      extends Chan("site")
    object tour      extends Chan("tour")
    object lobby     extends Chan("lobby")
    object simul     extends Chan("simul")
    object team      extends Chan("team")
    object swiss     extends Chan("swiss")
    object study     extends Chan("study")
    object round     extends Chan("r")
    object challenge extends Chan("chal")
  }

  final class Emits(
      val site: Emit[LishogiIn.Site],
      val tour: Emit[LishogiIn.Tour],
      val lobby: Emit[LishogiIn.Lobby],
      val simul: Emit[LishogiIn.Simul],
      val team: Emit[LishogiIn.Team],
      val swiss: Emit[LishogiIn.Swiss],
      val study: Emit[LishogiIn.Study],
      val round: Emit[LishogiIn.Round],
      val challenge: Emit[LishogiIn.Challenge]
  ) {

    def apply[In](select: Emits => Emit[In], in: In) = select(this)(in)
  }
}
