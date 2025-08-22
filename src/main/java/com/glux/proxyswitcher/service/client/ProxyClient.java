package com.glux.proxyswitcher.service.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

public class ProxyClient {
    private final String tcpHost;
    private final int tcpPort;
    private final String quicHost;
    private final int quicPort;
    private EventLoopGroup group;
    private AutoConnectQuicConnection autoConnectQuicConnection;

    public ProxyClient(String tcpHost, int tcpPort, String quicHost, int quicPort) {
        this.tcpHost = tcpHost;
        this.tcpPort = tcpPort;
        this.quicHost = quicHost;
        this.quicPort = quicPort;
    }

    public void start() throws Exception {
        boolean useEpoll = Epoll.isAvailable();
        group = useEpoll ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        Class<? extends ServerChannel> channelClass = useEpoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class;

        createSharedQuicConnection();

        ServerBootstrap b = new ServerBootstrap();
        b.group(group)
                .channel(channelClass)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        autoConnectQuicConnection.getQuicChannel();
                        ch.pipeline().addLast(new TcpToQuicHandler(autoConnectQuicConnection));
                    }
                });

        b.bind(new InetSocketAddress(tcpHost, tcpPort)).sync();
        System.out.println("代理端启动，监听TCP端口: " + tcpPort);
    }

    private void createSharedQuicConnection() throws Exception {
        autoConnectQuicConnection = new AutoConnectQuicConnection(quicHost, quicPort);
    }

    public void stop() {
        if (group != null) group.shutdownGracefully();
    }
}
