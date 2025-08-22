package com.glux.proxyswitcher.service.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class QuicToTcpHandler extends ChannelInboundHandlerAdapter {
    public static final Logger logger = LoggerFactory.getLogger(QuicToTcpHandler.class);
    private final String targetHost;
    private final int targetPort;
    private final EventLoopGroup group;
    private final CompletableFuture<Channel> tcpChannel = new CompletableFuture<>();
    private ChannelFuture channelFuture;

    public QuicToTcpHandler(String targetHost, int targetPort, EventLoopGroup group) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.group = group;
    }

    @Override
    public void channelActive(ChannelHandlerContext quicCtx) throws Exception {
        channelFuture = new Bootstrap().group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                logger.info("Server: stream sent client.");
                                quicCtx.writeAndFlush(msg);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                logger.info("Server: tcp to target closed.");
                                quicCtx.close();
                            }
                        });
                    }
                })
                .connect(targetHost, targetPort);

        channelFuture
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        logger.info("Server: tcp to target open success.");
                        tcpChannel.complete(future.channel());
                    } else {
                        logger.info("Server: tcp to target open failed.");
                        tcpChannel.completeExceptionally(new IllegalArgumentException("TCP channel failed."));
                        quicCtx.close();
                    }
                }).await();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logger.info("Server: stream received from client.");
        try {
            if (tcpChannel.get().isActive()) {
                tcpChannel.get().writeAndFlush(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        logger.info("Server: stream channelUnregistered.");
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("Server: stream channelReadComplete.");
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("Server: stream closed.");
        try {
            if (tcpChannel.get().isActive()) {
                tcpChannel.get().close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}