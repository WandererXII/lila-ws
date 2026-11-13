package lila.ws

import java.util.concurrent.atomic.AtomicReference

object Impersonations {

  private type ModId = User.ID

  private val all = new AtomicReference(Map.empty[ModId, User.ID])

  def apply(user: User.ID, by: Option[User.ID]): Unit =
    by match {
      case Some(modId) => all.updateAndGet(_ + (modId -> user))
      case None        =>
        all.updateAndGet { m =>
          (m collectFirst { case (modId, u) if u == user => modId }).fold(m)(m - _)
        }
    }

  def get(modId: ModId): Option[User.ID] = all.get get modId

  def reset(): Unit = all.set(Map.empty)
}
