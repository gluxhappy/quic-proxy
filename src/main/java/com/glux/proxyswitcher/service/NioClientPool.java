package com.glux.proxyswitcher.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用共享线程池的NIO客户端示例
 */
public class NioClientPool {

    private final Bootstrap bootstrap;

    public NioClientPool() {

        // 创建工作线程组，线程数为处理器核心数的2倍
        int workerThreads = Runtime.getRuntime().availableProcessors() * 2;
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(workerThreads);
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new FutureHandler());
                    }
                });
    }

    public SocketChannel connect(String host, int port, ChannelInboundHandlerAdapter adapter) throws Exception {
        ChannelFuture future = bootstrap.connect(host, port);
        if (!future.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Connection timeout to " + host +":" + port);
        }
        if (!future.isSuccess()) {
            throw new RuntimeException("Failed to connect to " + host +":" + port, future.cause());
        }
        SocketChannel channel = (SocketChannel) future.channel();
        FutureHandler handler = channel.pipeline().get(FutureHandler.class);
        if (handler != null) {
            handler.adapterCompletableFuture.complete(adapter);
        }
        return channel;
    }

    public void start() throws Exception {

    }

    public void stop() {
    }


    public static class FutureHandler extends ChannelInboundHandlerAdapter {

        private CompletableFuture<ChannelInboundHandlerAdapter> adapterCompletableFuture = new CompletableFuture<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                ChannelInboundHandlerAdapter adapter = getAdapter();
                if (adapter != null) {
                    adapter.channelRead(ctx, msg);
                } else {
                    // Release the message if no adapter is available
                    if (msg instanceof ByteBuf) {
                        ((ByteBuf) msg).release();
                    }
                }
            } catch (Exception e) {
                // Release the message on error
                if (msg instanceof ByteBuf) {
                    ((ByteBuf) msg).release();
                }
                throw e;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            try {
                ChannelInboundHandlerAdapter adapter = getAdapter();
                if (adapter != null) {
                    adapter.exceptionCaught(ctx, cause);
                }
            } catch (Exception e) {
                // Log the exception but don't rethrow to avoid infinite loops
                System.err.println("Error in adapter exception handling: " + e.getMessage());
            } finally {
                ctx.close();
            }
        }

        private ChannelInboundHandlerAdapter getAdapter() {
            try {
                return adapterCompletableFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                return null;
            }
        }
    }
}