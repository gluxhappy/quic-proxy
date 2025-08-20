package com.glux.proxyswitcher.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AutoConnectQuicConnection {

    public QuicChannel quicChannel;

    private String quicHost;

    private int quicPort;

    public AutoConnectQuicConnection(String quicHost, int quicPort) {
        this.quicHost = quicHost;
        this.quicPort = quicPort;
    }

    public QuicChannel getQuicChannel() {
        if (null == quicChannel || !quicChannel.isActive()) {
            reconnect();
        }
        return quicChannel;
    }

    public void reconnect() {
        try {
            QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols("http")
                    .build();

            Bootstrap bootstrap = new Bootstrap();
            Channel channel = bootstrap.group(new NioEventLoopGroup(4))
                    .channel(NioDatagramChannel.class)
                    .handler(new QuicClientCodecBuilder()
                            .sslContext(sslContext)
                            .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
                            .initialMaxData(10000000)
                            .initialMaxStreamDataBidirectionalLocal(1000000)
                            .initialMaxStreamsBidirectional(1000)
                            .maxIdleTimeout(10, TimeUnit.SECONDS)
                            .build())
                    .bind(0)
                    .sync()
                    .channel();
            channel.closeFuture().addListener(future -> {
                System.out.println("quic connection closed");
            });

            quicChannel = QuicChannel.newBootstrap(channel)
                    .streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ctx.close();
                        }
                    })
                    .remoteAddress(new InetSocketAddress(quicHost, quicPort))
                    .connect()
                    .await()
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reconnect quic,", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to reconnect quic,", e);
        }
    }


    public QuicStreamChannel createStream(ChannelInboundHandlerAdapter adapter) {
        try {
            return getQuicChannel().createStream(QuicStreamType.BIDIRECTIONAL, adapter)
                    .sync().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to create quic stream,", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to create quic stream", e);
        }
    }
}
