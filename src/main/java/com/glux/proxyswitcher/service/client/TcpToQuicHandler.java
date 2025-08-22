package com.glux.proxyswitcher.service.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class TcpToQuicHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TcpToQuicHandler.class);

    private static final AtomicLong tcpCounter = new AtomicLong(0);
    private static final AtomicLong quicStreamCounter = new AtomicLong(0);

    private final AutoConnectQuicConnection quicChannel;

    private QuicStreamChannel streamChannel;

    public TcpToQuicHandler(AutoConnectQuicConnection quicChannel) {
        this.quicChannel = quicChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext tcpCtx) throws Exception {
        System.out.println("TCP Connections:" + tcpCounter.incrementAndGet());
        logger.info("Client: tcp connection setup.");
        streamChannel = quicChannel.createStream(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                System.out.println("Stream Connections:" + quicStreamCounter.incrementAndGet());
                super.channelActive(ctx);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                logger.info("Client: stream received from server.");
                tcpCtx.writeAndFlush(msg);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                System.out.println("Stream Connections:" + quicStreamCounter.decrementAndGet());
                logger.info("Client: stream closed by remote.");
                tcpCtx.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logger.info("Client: send stream to server.");
        if (streamChannel.isActive()) {
            streamChannel.writeAndFlush(msg);
        } else {
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("TCP Connections:" + tcpCounter.decrementAndGet());
        logger.info("Client: stream closed.");
        if (streamChannel != null) {
            streamChannel.close();
        }
    }
}