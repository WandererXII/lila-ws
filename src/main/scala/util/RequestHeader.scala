package lila.ws
package util

import scala.jdk.CollectionConverters._

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.cookie.ServerCookieDecoder

final class RequestHeader(uri: String, req: HttpHeaders) {

  private val query = new QueryStringDecoder(uri)

  def path = query.path

  def header(name: CharSequence): Option[String] =
    Option(req get name).filter(_.nonEmpty)

  def cookie(name: String): Option[String] =
    for {
      encoded <- header(HttpHeaderNames.COOKIE)
      cookies = ServerCookieDecoder.LAX decode encoded
      cookie <- cookies.asScala.find(_.name contains name)
      value  <- Some(cookie.value).filter(_.nonEmpty)
    } yield value

  def queryParameter(name: String): Option[String] =
    Option(query.parameters.get(name)).map(_ get 0).filter(_.nonEmpty)

  def queryParameterInt(name: String): Option[Int] =
    queryParameter(name) flatMap (_.toIntOption)

  def userAgent: String = header(HttpHeaderNames.USER_AGENT) getOrElse ""

  def origin: Option[String] = header(HttpHeaderNames.ORIGIN)

  def flag: Option[Flag] = queryParameter("flag") flatMap Flag.make

  // nginx may append the real ip to the X-Forwarded-For header, so we take the last one
  def ip: Option[IpAddress] =
    header("X-Forwarded-For")
      .flatMap(_.split(',').lastOption)
      .map(_.trim)
      .filter(ip => ip.nonEmpty && !ip.exists(_.isWhitespace))
      .map(IpAddress.apply)

  def name: String = s"${uri} UA: ${userAgent}"

  def sri = queryParameter("sri") flatMap Sri.from
}

object RequestHeader {

  // Force decoding up front so that a malformed URI is a client error rather than a server error
  def decode(uri: String, req: HttpHeaders): Option[RequestHeader] = {
    val header = new RequestHeader(uri, req)
    try {
      header.path
      header.query.parameters
      Some(header)
    } catch {
      case _: IllegalArgumentException => None
    }
  }
}
