package lila.ws

import com.typesafe.scalalogging.Logger

final class RateLimit(
    maxCredits: Int,
    intervalMillis: Int,
    name: String,
) {
  import RateLimit._

  private var credits: Int    = maxCredits
  private var clearAt: Long   = 0L
  private var logged: Boolean = false

  def apply(msg: => String = ""): Boolean = {
    val now = nowMillis
    if (clearAt <= now) {
      credits = maxCredits
      clearAt = now + intervalMillis
    }
    if (credits > 0) {
      credits -= 1
      true
    } else {
      if (!logged) {
        logged = true
        logger.info(s"$name MSG: $msg")
      }
      Monitor rateLimit name
      false
    }
  }
}

object RateLimit {

  type Charge = Cost => Unit
  type Cost   = Int

  private def nowMillis = System.currentTimeMillis()

  private val logger = Logger(getClass)
}
