package com.glux.proxyswitcher.service.server;

import com.glux.proxyswitcher.service.client.AutoConnectQuicConnection;
import com.glux.proxyswitcher.service.cert.CertificateUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class ProxyServer {
    private final String quicHost;
    private final int quicPort;
    private final String targetHost;
    private final int targetPort;
    private Bootstrap quicServerBootstrap;
    private EventLoopGroup group = new NioEventLoopGroup(4);
    private Bootstrap tcpClientBootstrap = new Bootstrap();
    private EventLoopGroup tcpClientEventGroup = new NioEventLoopGroup(4);

    public ProxyServer(String quicHost, int quicPort, String targetHost, int targetPort) {
        this.quicHost = quicHost;
        this.quicPort = quicPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public void start() throws Exception {
        tcpClientBootstrap.group(tcpClientEventGroup);
        quicServerBootstrap = new Bootstrap();
        QuicSslContext sslContext = CertificateUtil.createServerSslContext();

        ChannelHandler codec = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(AutoConnectQuicConnection.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
                        ch.pipeline().addLast(new QuicToTcpHandler(targetHost, targetPort, tcpClientEventGroup));
                    }
                })
                .build();

        quicServerBootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(quicHost, quicPort))
                .sync();

        System.out.println("服务端启动，监听QUIC端口: " + quicPort);
    }

    public void stop() {
        if (group != null) group.shutdownGracefully();
    }
}