package com.glux.proxyswitcher.service.client;

import com.glux.proxyswitcher.service.cert.CertificateUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.*;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class AutoConnectQuicConnection {

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String host;
    private final int port;
    private final String sniHostname;
    private QuicChannel quicChannel;
    private EventLoopGroup group;

    public AutoConnectQuicConnection(String host, int port) {
        this(host, port, host);
    }

    public AutoConnectQuicConnection(String host, int port, String sniHostname) {
        this.host = host;
        this.port = port;
        this.sniHostname = sniHostname;
        this.group = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
    }

    public QuicChannel getQuicChannel() {
        if (quicChannel == null || !quicChannel.isActive()) {
            connect();
        }
        return quicChannel;
    }

    private void connect() {
        try {
            QuicSslContext sslContext = CertificateUtil.createClientSslContext();
            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslContext(sslContext)
                    .maxIdleTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(1000)
                    .build();

            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0).sync().channel();

            quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(new InetSocketAddress(sniHostname, port))
                    .connect()
                    .get();
        } catch (Exception e) {
            throw new RuntimeException("QUIC连接失败", e);
        }
    }

    public QuicStreamChannel createStream(ChannelHandler handler) {
        try {
            return getQuicChannel().createStream(QuicStreamType.BIDIRECTIONAL, handler).get();
        } catch (Exception e) {
            throw new RuntimeException("创建QUIC流失败", e);
        }
    }
}