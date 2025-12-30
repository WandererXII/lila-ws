package lila.ws

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import com.github.blemale.scaffeine.AsyncLoadingCache
import com.github.blemale.scaffeine.Scaffeine
import reactivemongo.api.bson._

final class LightUserApi(mongo: Mongo)(implicit executionContext: ExecutionContext) {

  def get(id: User.ID): Future[String] = cache get id

  private val cache: AsyncLoadingCache[User.ID, String] =
    Scaffeine()
      .initialCapacity(32768)
      .expireAfterWrite(15.minutes)
      .buildAsyncFuture(fetch)

  private def fetch(id: User.ID): Future[String] =
    mongo.user {
      _.find(
        BSONDocument("_id" -> id),
        Some(BSONDocument("username" -> true)),
      ).one[BSONDocument] map { docOpt =>
        docOpt.flatMap(_.getAsOpt[String]("username")).getOrElse(id)
      }
    }
}
