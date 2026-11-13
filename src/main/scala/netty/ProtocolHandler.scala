package lila.ws
package netty

import java.io.IOException
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

import akka.actor.typed.ActorRef
import io.netty.channel._
import io.netty.handler.codec.TooLongFrameException
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.websocketx._
import io.netty.util.AttributeKey
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.{ Future => NettyFuture }

final private class ProtocolHandler(
    clients: ActorRef[Clients.Control],
)(implicit ec: ExecutionContext)
    extends WebSocketServerProtocolHandler(ProtocolHandler.protocolConfig) {

  import Controller.Endpoint
  import ProtocolHandler._

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {
    if (!ctx.channel.isWritable) ctx.close()
    super.channelWritabilityChanged(ctx)
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: java.lang.Object): Unit = {
    evt match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete =>
        connectActorToChannel(ctx.channel.attr(key.endpoint).get, ctx.channel)
      case _ =>
    }
    super.userEventTriggered(ctx, evt)
  }

  private def connectActorToChannel(
      endpoint: Endpoint,
      channel: Channel,
  ): Unit = {
    val promise = Promise[Client]()
    channel.attr(key.client).set(promise.future)
    clients ! Clients.Start(endpoint.behavior(emitToChannel(channel)), promise)
    channel.closeFuture.addListener(new GenericFutureListener[NettyFuture[Void]] {
      def operationComplete(f: NettyFuture[Void]): Unit =
        promise.future foreach { client => clients ! Clients.Stop(client) }
    })
  }

  private def emitToChannel(channel: Channel): ClientEmit =
    in => {
      if (in == ipc.ClientIn.Disconnect) terminateConnection(channel)
      else channel.writeAndFlush(new TextWebSocketFrame(in.write))
    }

  // nicely terminate an ongoing connection
  private def terminateConnection(channel: Channel): ChannelFuture =
    channel.writeAndFlush(new CloseWebSocketFrame).addListener(ChannelFutureListener.CLOSE)

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    cause match {
      // IO exceptions happen all the time, it usually just means that the client has closed the connection before fully
      // sending/receiving the response.
      case _: IOException =>
        Monitor.websocketError("io")
        ctx.close()
      case _: WebSocketHandshakeException =>
        Monitor.websocketError("handshake")
        RequestHandler.sendErrorResponse(ctx, HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
      case e: CorruptedWebSocketFrameException
          if Option(e.getMessage).exists(_ startsWith "Max frame length") =>
        Monitor.websocketError("frameLength")
        ctx.close()
      case _: CorruptedWebSocketFrameException =>
        Monitor.websocketError("corrupted")
        ctx.close()
      case _: TooLongFrameException =>
        Monitor.websocketError("uriTooLong")
        ctx.close()
      case e: IllegalArgumentException
          if Option(e.getMessage).exists(
            _ contains "Header value contains a prohibited character",
          ) =>
        Monitor.websocketError("headerIllegalChar")
        ctx.close()
      case _ =>
        Monitor.websocketError("other")
        super.exceptionCaught(ctx, cause)
    }
}

private object ProtocolHandler {

  val writeBufferWaterMark = new WriteBufferWaterMark(512 * 1024, 1024 * 1024)

  val forceCloseTimeoutMillis = 10_000L

  val protocolConfig = WebSocketServerProtocolConfig
    .newBuilder()
    .websocketPath("/")
    .subprotocols(null)
    .checkStartsWith(true)
    .dropPongFrames(true)
    .handshakeTimeoutMillis(10_000L)
    .forceCloseTimeoutMillis(forceCloseTimeoutMillis)
    .decoderConfig(
      WebSocketDecoderConfig
        .newBuilder()
        .maxFramePayloadLength(8192) // /inbox allows sending 8000 chars
        .allowMaskMismatch(false)
        .allowExtensions(false)
        .build(),
    )
    .build()

  object key {
    val client   = AttributeKey.valueOf[Future[Client]]("client")
    val endpoint = AttributeKey.valueOf[Controller.Endpoint]("endpoint")
  }
}
