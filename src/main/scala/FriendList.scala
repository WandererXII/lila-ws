package lila.ws

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import com.github.blemale.scaffeine.AsyncLoadingCache
import com.github.blemale.scaffeine.Scaffeine

import lila.ws.SocialGraph.UserMeta
import lila.ws.ipc.ClientIn.following._

final class FriendList(
    graph: SocialGraph,
    mongo: Mongo,
)(implicit ec: ExecutionContext) {

  import FriendList._

  private val userDatas: AsyncLoadingCache[User.ID, Option[UserData]] = Scaffeine()
    .expireAfterAccess(20.minutes)
    .maximumSize(32_768)
    .buildAsyncFuture(mongo.userData)

  def start(userId: User.ID, client: Client, emit: Emit[ipc.ClientIn]): Future[Unit] = {
    graph.subscribe(userId, client)
    graph.followed(userId) flatMap { entries =>
      Future.sequence {
        entries.collect {
          case u if u.meta.online =>
            userDatas
              .get(u.id)
              .map {
                _ map { UserView(u.id, _, u.meta) }
              }(ExecutionContext.parasitic)
        }
      } map { views =>
        emit(Onlines(views.flatten))
      }
    }
  }

  def follow(left: User.ID, right: User.ID): Unit =
    graph.follow(left, right)

  def unFollow(left: User.ID, right: User.ID) = graph.unfollow(left, right)

  def startPlaying(userId: User.ID) =
    update(userId, Playing.apply)(_.withPlaying(true))

  def stopPlaying(userId: User.ID) =
    update(userId, StoppedPlaying.apply)(_.withPlaying(false))

  def onClientStop(userId: User.ID, client: Client): Unit =
    graph.unsubscribe(userId, client)

  def subscribedClients: Int = graph.subscribedClients

  // user logs in
  private def onConnect(userId: User.ID): Unit =
    updateView(userId, Enters.apply)(_.withOnline(true))

  // user logs off
  private def onDisconnect(userId: User.ID) =
    update(userId, Leaves.apply)(_.withOnline(false))

  private def update(userId: User.ID, msg: User.ID => ipc.ClientIn)(update: UserMeta => UserMeta) =
    graph.tell(userId, update) foreach { case (subject, subs) =>
      if (subs.nonEmpty) {
        val event = msg(subject.id)
        subs foreach { _ ! event }
      }
    }

  private def updateView(userId: User.ID, msg: UserView => ipc.ClientIn)(
      update: UserMeta => UserMeta,
  ) =
    graph.tell(userId, update) foreach { case (subject, subs) =>
      if (subs.nonEmpty) userDatas.get(subject.id) foreach {
        _ foreach { data =>
          val event = msg(UserView(subject.id, data, subject.meta))
          subs foreach { _ ! event }
        }
      }
    }

  Bus.internal.subscribe(
    "users",
    {
      case ipc.LilaIn.ConnectUser(user, _)   => onConnect(user.id)
      case ipc.LilaIn.DisconnectUsers(users) => users foreach onDisconnect
    },
  )
}

object FriendList {

  case class UserData(name: String, patron: Boolean)

  case class UserView(id: User.ID, data: UserData, meta: SocialGraph.UserMeta)
}
