/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.glux.proxyswitcher.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class QuicBridgeClient implements NioServer.ChannelBridgeFactory {
    private String quicHost;
    private int quicPort;
    private String targetHost;
    private int targetPort;
    private NioEventLoopGroup group;
    private Bootstrap bs;
    private Channel channel;
    private QuicChannel quicChannel;

    public String getQuicHost() {
        return quicHost;
    }

    public void setQuicHost(String quicHost) {
        this.quicHost = quicHost;
    }

    public int getQuicPort() {
        return quicPort;
    }

    public void setQuicPort(int quicPort) {
        this.quicPort = quicPort;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public void start() throws Exception {
        QuicSslContext context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
                applicationProtocols("http/0.9").build();
        group = new NioEventLoopGroup(10);
        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                // As we don't want to support remote initiated streams just setup the limit for local initiated
                // streams in this example.
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();

        bs = new Bootstrap();
        channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();

        quicChannel = QuicChannel.newBootstrap(channel)
                .streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.
                        ctx.close();
                    }
                })
                .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9999))
                .connect()
                .get();
    }


    public void stop() {
        try {
            quicChannel.closeFuture().sync();
            channel.closeFuture().sync();
            group.shutdownGracefully();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @Override
    public NioServer.ChannelBridge newInstance(SocketChannel socketChannel) throws InterruptedException {
        final CompletableFuture<Void> initSent = new CompletableFuture<>();
        final QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        //byteBuf.nioBuffer();
                        socketChannel.write(byteBuf.nioBuffer());
                        byteBuf.release();
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                            try {
                                socketChannel.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }).sync().getNow();
        if (targetHost != null) {
            streamChannel.writeAndFlush(Unpooled.copiedBuffer("CONNECT " + targetHost + " " + targetPort+"\n", CharsetUtil.US_ASCII)).sync();
        }
        initSent.complete(null);
        return new NioServer.ChannelBridge() {
            @Override
            public void write(ByteBuffer buffer) throws IOException {
                try {
                    initSent.get();
                    streamChannel.writeAndFlush(Unpooled.wrappedBuffer(buffer)).sync();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    streamChannel.closeFuture().sync();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        };
    }
}