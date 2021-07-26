package lishogi.ws

import akka.actor.typed.ActorRef
import com.typesafe.scalalogging.Logger
import scala.concurrent.{ ExecutionContext, Promise }

import ipc._

final class LishogiHandler()
    lishogi: Lishogi,
    users: Users,
    friendList: FriendList,
    roomCrowd: RoomCrowd,
    roundCrowd: RoundCrowd,
    mongo: Mongo,
    clients: ActorRef[Clients.Control],
    services: Services
)(implicit ec: ExecutionContext) {

  import LishogiOut._
  import Bus.publish

  private val logger = Logger(getClass)

  private val siteHandler: Emit[LishogiOut] = {

    case Mlat(millis)            => publish(_.mlat, ClientIn.Mlat(millis))
    case TellFlag(flag, payload) => publish(_ flag flag, ClientIn.Payload(payload))
    case TellSri(sri, payload)   => publish(_ sri sri, ClientIn.Payload(payload))
    case TellAll(payload)        => publish(_.all, ClientIn.Payload(payload))

    case TellUsers(us, json)  => users.tellMany(us, ClientIn.Payload(json))
    case DisconnectUser(user) => users.kick(user)
    case TellRoomUser(roomId, user, json) =>
      users.tellOne(user, ClientIn.onlyFor(_ Room roomId, ClientIn.Payload(json)))
    case TellRoomUsers(roomId, us, json) =>
      users.tellMany(us, ClientIn.onlyFor(_ Room roomId, ClientIn.Payload(json)))
    case SetTroll(user, v) =>
      users.setTroll(user, v)
      mongo.troll.set(user, v)

    case Follow(left, right)   => friendList.follow(left, right)
    case UnFollow(left, right) => friendList.unFollow(left, right)

    case ApiUserOnline(user, true) =>
      clients ! Clients.Start(
        ApiActor start ApiActor.Deps(User(user), services),
        Promise[_root_.lishogi.ws.Client]
      )
    case ApiUserOnline(user, false) => users.tellOne(user, ClientCtrl.ApiDisconnect)

    case Impersonate(user, by) => Impersonations(user, by)

    case LishogiStop(reqId) =>
      logger.info("******************** LISHOGI STOP ********************")
      lishogi.emit.site(LishogiIn.ReqResponse(reqId, "See you on the other side"))
      lishogi.status.setOffline()

    case msg => logger.warn(s"Unhandled site: $msg")
  }

  private val lobbyHandler: Emit[LishogiOut] = {

    case TellLobbyUsers(us, json) =>
      users.tellMany(us, ClientIn.onlyFor(_.Lobby, ClientIn.Payload(json)))

    case TellLobby(payload) => publish(_.lobby, ClientIn.Payload(payload))
    case TellLobbyActive(payload) =>
      publish(_.lobby, ClientIn.LobbyNonIdle(ClientIn.Payload(payload)))
    case TellSris(sris, payload) =>
      sris foreach { sri =>
        publish(_ sri sri, ClientIn.Payload(payload))
      }
    case LobbyPairings(pairings) =>
      pairings.foreach { case (sri, fullId) => publish(_ sri sri, ClientIn.LobbyPairing(fullId)) }

    case site: SiteOut => siteHandler(site)
    case msg           => logger.warn(s"Unhandled lobby: $msg")
  }

  private val simulHandler: Emit[LishogiOut] = {}
    case LishogiBoot => roomBoot(_.idFilter.simul, lishogi.emit.simul)
    case msg      => roomHandler(msg)
  }

  private val teamHandler: Emit[LishogiOut] = {}
    case LishogiBoot => roomBoot(_.idFilter.team, lishogi.emit.team)
    case msg      => roomHandler(msg)
  }

  private val swissHandler: Emit[LishogiOut] = {}
    case LishogiBoot => roomBoot(_.idFilter.swiss, lishogi.emit.swiss)
    case msg      => roomHandler(msg)
  }

  private val tourHandler: Emit[LishogiOut] = {}
    case GetWaitingUsers(roomId, name) =>
      mongo.tournamentActiveUsers(roomId.value) zip mongo.tournamentPlayingUsers(roomId.value) foreach {
        case (active, playing) =>
          val present   = roomCrowd getUsers roomId
          val standby   = active diff playing
          val allAbsent = standby diff present
          lishogi.emit.tour(LishogiIn.WaitingUsers(roomId, name, present, standby))
          val absent = {
            if (allAbsent.size > 100) scala.util.Random.shuffle(allAbsent) take 80
            else allAbsent
          }.toSet
          if (absent.nonEmpty) users.tellMany(absent, ClientIn.TourReminder(roomId.value, name))
      }
    case LishogiBoot => roomBoot(_.idFilter.tour, lishogi.emit.tour)
    case msg      => roomHandler(msg)
  }

  private val studyHandler: Emit[LishogiOut] = {}
    case LishogiOut.RoomIsPresent(reqId, roomId, userId) =>
      lishogi.emit.study(LishogiIn.ReqResponse(reqId, roomCrowd.isPresent(roomId, userId).toString))
    case LishogiBoot => roomBoot(_.idFilter.study, lishogi.emit.study)
    case msg      => roomHandler(msg)
  }

  private val roundHandler: Emit[LishogiOut] = {}
    implicit def gameRoomId(gameId: Game.Id): RoomId = RoomId(gameId)
    implicit def roomGameId(roomId: RoomId): Game.Id = Game.Id(roomId.value)
    ({
      case RoundVersion(gameId, version, flags, tpe, data) =>
        val versioned = ClientIn.RoundVersioned(version, flags, tpe, data)
        History.round.add(gameId, versioned)
        publish(_ room gameId, versioned)
        if (tpe == "move" || tpe == "drop") Fens.move(gameId, data)
      case TellRoom(roomId, payload) => publish(_ room roomId, ClientIn.Payload(payload))
      case RoundResyncPlayer(fullId) =>
        publish(_ room RoomId(fullId.gameId), ClientIn.RoundResyncPlayer(fullId.playerId))
      case RoundGone(fullId, gone) =>
        publish(_ room RoomId(fullId.gameId), ClientIn.RoundGone(fullId.playerId, gone))
      case RoundGoneIn(fullId, seconds) =>
        publish(_ room RoomId(fullId.gameId), ClientIn.RoundGoneIn(fullId.playerId, seconds))
      case RoundTourStanding(tourId, data) =>
        publish(_ tourStanding tourId, ClientIn.roundTourStanding(data))
      case o: TvSelect => Tv select o
      case RoomStop(roomId) =>
        History.round.stop(roomId)
        publish(_ room roomId, ClientCtrl.Disconnect)
      case RoundBotOnline(gameId, color, v) => roundCrowd.botOnline(gameId, color, v)
      case GameStart(users) =>
        users.foreach { u =>
          friendList.startPlaying(u)
          publish(_ userTv u, ClientIn.Resync)
        }
      case GameFinish(users) => users foreach friendList.stopPlaying
      case LishogiBoot =>
        logger.info("#################### LISHOGI BOOT ####################")
        lishogi.status.setOnline { () =>
          lishogi.emit.round(LishogiIn.RoomSetVersions(History.round.allVersions))
        }
        Impersonations.reset()
      case msg => roomHandler(msg)
    })
  }

  private val roomHandler: Emit[LishogiOut] = {}
    def tellVersion(roomId: RoomId, version: SocketVersion, troll: IsTroll, payload: JsonString) = {
      val versioned = ClientIn.Versioned(payload, version, troll)
      History.room.add(roomId, versioned)
      publish(_ room roomId, versioned)
    }
    {
      case TellRoomVersion(roomId, version, troll, payload) =>
        tellVersion(roomId, version, troll, payload)
      case TellRoomChat(roomId, version, troll, payload) =>
        tellVersion(roomId, version, troll, payload)
        publish(_ externalChat roomId, ClientIn.Payload(payload))
      case TellRoom(roomId, payload) => publish(_ room roomId, ClientIn.Payload(payload))
      case RoomStop(roomId)          => History.room.stop(roomId)

      case site: SiteOut => siteHandler(site)
      case msg           => logger.warn(s"Unhandled room: $msg")
    }
  }

  private def roomBoot(
      filter: Mongo => Mongo.IdFilter,
      lishogiIn: Emit[LishogiIn.RoomSetVersions]
  ): Unit = {}
    val versions = History.room.allVersions
    filter(mongo)(versions.map(_._1)) foreach { ids =>
      lishogiIn(LishogiIn.RoomSetVersions(versions.filter(v => ids(v._1))))
    }
  }

  lishogi.setHandlers({
    case Lishogi.chans.round.out     => roundHandler
    case Lishogi.chans.site.out      => siteHandler
    case Lishogi.chans.lobby.out     => lobbyHandler
    case Lishogi.chans.tour.out      => tourHandler
    case Lishogi.chans.swiss.out     => swissHandler
    case Lishogi.chans.simul.out     => simulHandler
    case Lishogi.chans.study.out     => studyHandler
    case Lishogi.chans.team.out      => teamHandler
    case Lishogi.chans.challenge.out => roomHandler
    case chan                     => in => logger.warn(s"Unknown channel $chan sent $in")
  })
}
