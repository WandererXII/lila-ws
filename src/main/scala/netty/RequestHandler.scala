package lila.ws
package netty

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http._

final private class RequestHandler(route: util.RequestHeader => Controller.Response)(implicit
    ec: ExecutionContext,
) extends SimpleChannelInboundHandler[FullHttpRequest](false) {

  private val logger                           = Logger(getClass)
  private var pending: Option[FullHttpRequest] = None
  private var requested                        = false

  override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    // A connection can perform only one upgrade. Do not let pipelined requests race
    // to replace the endpoint while authentication is still pending.
    if (requested) {
      req.release()
      ctx.close()
    } else {
      requested = true
      pending = Some(req)
      val response =
        try {
          if (req.decoderResult.isSuccess) route(new util.RequestHeader(req.uri, req.headers))
          else Future.successful(Left(HttpResponseStatus.BAD_REQUEST))
        } catch {
          case NonFatal(cause) => Future.failed(cause)
        }
      response.onComplete { result =>
        // Authentication completes on the application executor. Resume the Netty
        // pipeline on its event loop, including all access to the pending request.
        ctx.executor.execute(new Runnable {
          def run(): Unit = pending.foreach { request =>
            pending = None
            result match {
              case Success(Right(endpoint)) if request.method == HttpMethod.GET =>
                ctx.channel.attr(ProtocolHandler.key.endpoint).set(endpoint)
                ctx.fireChannelRead(request)
              case other =>
                try {
                  val status = other match {
                    case Success(Left(status)) => status
                    case Success(Right(_))     => HttpResponseStatus.METHOD_NOT_ALLOWED
                    case Failure(cause)        =>
                      logger.error("Cannot route WebSocket request", cause)
                      HttpResponseStatus.INTERNAL_SERVER_ERROR
                  }
                  RequestHandler.sendErrorResponse(ctx, request.protocolVersion, status)
                } finally request.release()
            }
          }
        })
      }
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    pending.foreach(_.release())
    pending = None
    super.channelInactive(ctx)
  }
}

private object RequestHandler {
  def sendErrorResponse(
      ctx: ChannelHandlerContext,
      version: HttpVersion,
      status: HttpResponseStatus,
  ): Unit = {
    val error = new DefaultFullHttpResponse(version, status)
    error.headers.set(HttpHeaderNames.CONTENT_LENGTH, "0")
    error.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
    if (status == HttpResponseStatus.METHOD_NOT_ALLOWED)
      error.headers.set(HttpHeaderNames.ALLOW, "GET")
    ctx
      .writeAndFlush(error)
      .addListener(new ChannelFutureListener {
        // Close from this context, bypassing the WebSocket handler's close frame
        // logic: this connection has not completed an upgrade.
        def operationComplete(f: ChannelFuture): Unit = { ctx.close(); () }
      })
  }
}
