package lila.ws
package ipc
 
import shogi.format.usi.{ Usi, UsiCharPair }
import shogi.opening.FullOpening
import play.api.libs.json._

import lila.ws.util.LilaJsObject.augment

sealed trait ClientIn extends ClientMsg {
  def write: String
}

object ClientIn {

  import Shogi.json._

  case object Pong extends ClientIn {
    val write = "0"
  }

  case object Resync extends ClientIn {
    val write = cliMsg("resync")
  }

  // triggers actual disconnection
  case object Disconnect extends ClientIn {
    val write = cliMsg("bye") // not actually sent
  }

  case class LobbyPong(members: Int, rounds: Int) extends ClientIn {
    val write = Json stringify Json.obj(
      "t" -> "n",
      "d" -> members,
      "r" -> rounds
    )
  }

  case class Sfen(gameId: Game.Id, lastUsi: Usi, sfen: shogi.format.forsyth.Sfen) extends ClientIn {
    def write =
      cliMsg(
        "sfen",
        Json.obj(
          "id"   -> gameId.value,
          "lm"   -> lastUsi,
          "sfen" -> sfen
        )
      )
  }

  case class Mlat(millis: Double) extends ClientIn {
    lazy val write = cliMsg("mlat", millis)
  }

  case class NbMembers(value: Int) extends ClientIn {
    lazy val write = cliMsg("member/nb", value)
  }

  case class NbRounds(value: Int) extends ClientIn {
    lazy val write = cliMsg("round/nb", value)
  }

  sealed trait HasVersion extends ClientMsg {
    val version: SocketVersion
  }

  case class Versioned(json: JsonString, version: SocketVersion, troll: IsTroll) extends HasVersion {
    lazy val full = Payload(JsonString(s"""{"v":$version,${json.value drop 1}"""))
    lazy val skip = Payload(JsonString(s"""{"v":$version}"""))
  }

  case class Payload(json: JsonString) extends ClientIn {
    def write = json.value
  }
  def payload(js: JsValue)                 = Payload(JsonString(Json stringify js))
  def payload(tpe: String, js: JsonString) = Payload(JsonString(cliMsg(tpe, js)))

  case class Crowd(doc: JsObject) extends ClientIn {
    lazy val write = cliMsg("crowd", doc)
  }
  val emptyCrowd = Crowd(Json.obj())

  case class LobbyPairing(fullId: Game.FullId) extends ClientIn {
    def write = {
      cliMsg(
        "redirect",
        Json.obj(
          "id"  -> fullId.value,
          "url" -> s"/$fullId"
        )
      )
    }
  }

  case class LobbyNonIdle(payload: Payload) extends ClientIn {
    def write = payload.write
  }

  case class OnlyFor(endpoint: OnlyFor.Endpoint, payload: Payload) extends ClientMsg {
    def write = payload.write
  }
  object OnlyFor {
    sealed trait Endpoint
    case object Api             extends Endpoint
    case object Lobby           extends Endpoint
    case class Room(id: RoomId) extends Endpoint
  }
  def onlyFor(select: OnlyFor.type => OnlyFor.Endpoint, payload: Payload) =
    OnlyFor(select(OnlyFor), payload)

  case class TourReminder(tourId: Tour.ID, tourName: String) extends ClientIn {
    lazy val write = cliMsg(
      "tournamentReminder",
      Json.obj(
        "id"   -> tourId,
        "name" -> tourName
      )
    )
  }

  def tvSelect(data: JsonString) = payload("tvSelect", data)

  case class Opening(path: Path, opening: FullOpening) extends ClientIn {
    def write =
      cliMsg(
        "opening",
        Json.obj(
          "path"    -> path,
          "opening" -> opening
        )
      )
  }

  case object StepFailure extends ClientIn {
    def write = cliMsg("stepFailure")
  }

  case class Node(
      path: Path,
      id: UsiCharPair,
      ply: Int,
      usi: shogi.format.usi.Usi,
      sfen: shogi.format.forsyth.Sfen,
      check: Boolean,
      opening: Option[shogi.opening.FullOpening],
      chapterId: Option[ChapterId]
  ) extends ClientIn {
    def write =
      cliMsg(
        "node",
        Json
          .obj(
            "path" -> path,
            "node" -> Json
              .obj(
                "ply"      -> ply,
                "sfen"     -> sfen,
                "id"       -> id,
                "usi"      -> usi,
                "children" -> JsArray()
              )
              .add("opening" -> opening)
              .add("check" -> check)
          )
          .add("ch" -> chapterId)
      )
  }

  case class Ack(id: Option[Int]) extends ClientIn {
    def write = id.fold(cliMsg("ack")) { cliMsg("ack", _) }
  }

  case class RoundResyncPlayer(playerId: Game.PlayerId) extends ClientIn {
    def write = cliMsg("resync")
  }
  case class RoundGone(playerId: Game.PlayerId, v: Boolean) extends ClientIn {
    def write = cliMsg("gone", v)
  }
  case class RoundGoneIn(playerId: Game.PlayerId, seconds: Int) extends ClientIn {
    def write = cliMsg("goneIn", seconds)
  }
  case class RoundVersioned(
      version: SocketVersion,
      flags: RoundEventFlags,
      tpe: String,
      data: JsonString
  ) extends HasVersion {
    val full         = Payload(JsonString(cliMsg(tpe, data, version)))
    lazy val skip    = Payload(JsonString(s"""{"v":$version}"""))
  }
  case object RoundPingFrameNoFlush extends ClientIn {
    val write = "" // not actually sent
  }
  def roundTourStanding(data: JsonString) = payload("tourStanding", data)

  case class Palantir(userIds: Iterable[User.ID]) extends ClientIn {
    def write = cliMsg("palantir", userIds)
  }

  case class MsgType(orig: User.ID) extends ClientIn {
    def write = cliMsg("msgType", orig)
  }

  object following {

    case class Onlines(users: List[FriendList.UserView]) extends ClientIn {
      def write =
        Json stringify Json.obj(
          "t"       -> "following_onlines",
          "d"       -> users.map(_.data.titleName),
          "playing" -> users.collect { case u if u.meta.playing => u.id },
          "patrons" -> users.collect { case u if u.data.patron => u.id }
        )
    }
    case class Enters(user: FriendList.UserView) extends ClientIn {
      // We use 'd' for backward compatibility with the mobile client
      def write =
        Json stringify Json.obj(
          "t" -> "following_enters",
          "d" -> user.data.titleName
        ) ++ {
          if (user.data.patron) Json.obj("patron" -> true)
          else Json.obj()
        }

    }
    abstract class Event(key: String) extends ClientIn {
      def user: User.ID
      def write = cliMsg(s"following_$key", user)
    }
    case class Leaves(user: User.ID)         extends Event("leaves")
    case class Playing(user: User.ID)        extends Event("playing")
    case class StoppedPlaying(user: User.ID) extends Event("stopped_playing")
  }

  case class StormKey(signed: String) extends ClientIn {
    def write = cliMsg("sk1", signed)
  }

  private def cliMsg[A: Writes](t: String, data: A): String =
    Json stringify Json.obj(
      "t" -> t,
      "d" -> data
    )
  private def cliMsg(t: String, data: JsonString): String = s"""{"t":"$t","d":${data.value}}"""
  private def cliMsg(t: String, data: JsonString, version: SocketVersion): String =
    s"""{"t":"$t","v":$version,"d":${data.value}}"""
  private def cliMsg(t: String, int: Int): String      = s"""{"t":"$t","d":$int}"""
  private def cliMsg(t: String, bool: Boolean): String = s"""{"t":"$t","d":$bool}"""
  private def cliMsg(t: String): String                = s"""{"t":"$t"}"""
}
