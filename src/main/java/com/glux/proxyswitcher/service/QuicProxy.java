package com.glux.proxyswitcher.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class QuicProxy {
    private static final Logger logger = LoggerFactory.getLogger(QuicProxy.class);

    public static class ProxyClient {
        private final int tcpPort;
        private final String quicHost;
        private final int quicPort;
        private EventLoopGroup group;
        private AutoConnectQuicConnection autoConnectQuicConnection;

        public ProxyClient(int tcpPort, String quicHost, int quicPort) {
            this.tcpPort = tcpPort;
            this.quicHost = quicHost;
            this.quicPort = quicPort;
        }

        public void start() throws Exception {
            group = new NioEventLoopGroup();

            createSharedQuicConnection();

            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            autoConnectQuicConnection.getQuicChannel();
                            ch.pipeline().addLast(new TcpToQuicHandler(autoConnectQuicConnection));
                        }
                    });

            b.bind(tcpPort).sync();
            System.out.println("代理端启动，监听TCP端口: " + tcpPort);
        }

        private void createSharedQuicConnection() throws Exception {
            autoConnectQuicConnection = new AutoConnectQuicConnection(quicHost, quicPort);
        }

        public void stop() {
            if (group != null) group.shutdownGracefully();
        }
    }

    static class TcpToQuicHandler extends ChannelInboundHandlerAdapter {
        private final AutoConnectQuicConnection quicChannel;
        private QuicStreamChannel streamChannel;

        public TcpToQuicHandler(AutoConnectQuicConnection quicChannel) {
            this.quicChannel = quicChannel;
        }

        @Override
        public void channelActive(ChannelHandlerContext tcpCtx) throws Exception {
            logger.info("Client: tcp connection setup.");
            streamChannel = quicChannel.createStream(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    logger.info("Client: stream received from server.");
                    tcpCtx.writeAndFlush(msg);
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) {
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
            logger.info("Client: stream closed.");
            if (streamChannel != null) {
                streamChannel.close();
            }
        }
    }

    @ChannelHandler.Sharable
    public static class SharedServerQuicChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            logger.info("Server: quic connect channelRegistered.");
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            logger.info("Server: quic connect channelUnregistered.");
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            logger.info("Server: quic connect channelRead.");
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            logger.info("Server: quic connect channelReadComplete.");
            super.channelReadComplete(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            logger.info("Server: quic connect userEventTriggered.");
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            logger.info("Server: quic connect channelWritabilityChanged.");
            super.channelWritabilityChanged(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.info("Server: quic connect exceptionCaught.");
            super.exceptionCaught(ctx, cause);
        }

        @Override

        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            logger.info("Server: quic connect setup.");
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.info("Server: quic connect closed.");
            super.channelInactive(ctx);
        }
    }
    public static class ProxyServer {
        private final int quicPort;
        private final String targetHost;
        private final int targetPort;
        private Bootstrap quicServerBootstrap;
        private EventLoopGroup group = new NioEventLoopGroup(4);
        private Bootstrap tcpClientBootstrap = new Bootstrap();
        private EventLoopGroup tcpClientEventGroup = new NioEventLoopGroup(4);

        public ProxyServer(int quicPort, String targetHost, int targetPort) {
            this.quicPort = quicPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
        }

        public void start() throws Exception {
            tcpClientBootstrap.group(tcpClientEventGroup);
            quicServerBootstrap = new Bootstrap();
            SelfSignedCertificate cert = new SelfSignedCertificate();
            QuicSslContext sslContext = QuicSslContextBuilder.forServer(
                            cert.privateKey(), null, cert.certificate())
                    .applicationProtocols("http")
                    .build();

            ChannelHandler codec = new QuicServerCodecBuilder()
                    .sslContext(sslContext)
                    .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(1000)
                    .maxIdleTimeout(10, TimeUnit.SECONDS)
                    .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                    .handler(new SharedServerQuicChannelHandler())
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
                            ch.pipeline().addLast(new QuicToTcpHandler(targetHost, targetPort,tcpClientEventGroup));
                        }
                    })
                    .build();

            quicServerBootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(quicPort))
                    .sync();

            System.out.println("服务端启动，监听QUIC端口: " + quicPort);
        }

        public void stop() {
            if (group != null) group.shutdownGracefully();
        }
    }

    static class QuicToTcpHandler extends ChannelInboundHandlerAdapter {
        private final String targetHost;
        private final int targetPort;
        private final EventLoopGroup group;
        private final CompletableFuture<Channel> tcpChannel=new CompletableFuture<>();
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
}