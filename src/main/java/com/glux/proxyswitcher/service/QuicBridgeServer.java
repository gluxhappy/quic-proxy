package com.glux.proxyswitcher.service;
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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QuicBridgeServer {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicBridgeServer.class);

    private static final NioClientPool nioClientPool = new NioClientPool();

    public QuicBridgeServer() throws Exception {
        nioClientPool.start();
    }

    public static void main(String[] args) throws Exception {
        new QuicBridgeServer().run();
    }

    public void run() throws Exception {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                        selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("http/0.9").build();
        NioEventLoopGroup group = new NioEventLoopGroup(10);
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .activeMigration(true)

                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        // Create streams etc..
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                LOGGER.info("Connection closed: {}", f.getNow());
                            }
                        });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel quicStreamChannel) {
                        quicStreamChannel.pipeline()
//                                .addLast(new LineBasedFrameDecoder(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    private volatile boolean initialized = false;
                                    private ByteBuf headerBuffer;
                                    private SocketChannel socketChannel;

                                    @Override
                                    public void handlerAdded(ChannelHandlerContext ctx) {
                                        headerBuffer = ctx.alloc().buffer(1024);
                                    }

                                    @Override
                                    public void handlerRemoved(ChannelHandlerContext ctx) {
                                        releaseResources();
                                    }

                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        ByteBuf byteBuf = (ByteBuf) msg;
                                        try {
                                            if (!initialized) {
                                                if (!processConnectCommand(ctx, byteBuf)) {
                                                    return;
                                                }
                                            }
                                            
                                            if (byteBuf.readableBytes() > 0 && socketChannel != null && socketChannel.isActive()) {
                                                ByteBuf copy = byteBuf.copy();
                                                socketChannel.writeAndFlush(copy);
                                            }
                                        } catch (Exception e) {
                                            LOGGER.error("Error processing QUIC stream data", e);
                                            ctx.close();
                                        } finally {
                                            byteBuf.release();
                                        }
                                    }

                                    private boolean processConnectCommand(ChannelHandlerContext ctx, ByteBuf byteBuf) {
                                        try {
                                            int totalLength = byteBuf.readableBytes();
                                            int newlineIndex = byteBuf.indexOf(byteBuf.readerIndex(),
                                                    byteBuf.readerIndex() + totalLength, (byte) '\n');
                                            
                                            if (newlineIndex < 0) {
                                                if (headerBuffer.readableBytes() + totalLength > 1024) {
                                                    LOGGER.warn("CONNECT command too long, closing connection");
                                                    ctx.close();
                                                    return false;
                                                }
                                                headerBuffer.writeBytes(byteBuf, totalLength);
                                                return false;
                                            }
                                            
                                            int commandLength = newlineIndex - byteBuf.readerIndex() + 1;
                                            headerBuffer.writeBytes(byteBuf, commandLength);
                                            
                                            String cmd = headerBuffer.toString(CharsetUtil.UTF_8).trim();
                                            String[] parts = cmd.split("\\s+");
                                            
                                            if (parts.length != 3 || !"CONNECT".equals(parts[0])) {
                                                LOGGER.warn("Invalid CONNECT command: {}", cmd);
                                                ctx.close();
                                                return false;
                                            }
                                            
                                            String host = parts[1];
                                            int port = Integer.parseInt(parts[2]);
                                            
                                            socketChannel = nioClientPool.connect(host, port, new ChannelInboundHandlerAdapter() {
                                                @Override
                                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                                    if (quicStreamChannel.isActive()) {
                                                        quicStreamChannel.writeAndFlush(msg);
                                                    } else {
                                                        ((ByteBuf) msg).release();
                                                    }
                                                }

                                                @Override
                                                public void channelInactive(ChannelHandlerContext ctx) {
                                                    if (quicStreamChannel.isActive()) {
                                                        quicStreamChannel.close();
                                                    }
                                                }

                                                @Override
                                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                                    LOGGER.error("Socket channel error", cause);
                                                    ctx.close();
                                                    if (quicStreamChannel.isActive()) {
                                                        quicStreamChannel.close();
                                                    }
                                                }
                                            });
                                            
                                            initialized = true;
                                            LOGGER.debug("Connected to {}:{}", host, port);
                                            return true;
                                            
                                        } catch (NumberFormatException e) {
                                            LOGGER.warn("Invalid port number in CONNECT command", e);
                                            ctx.close();
                                            return false;
                                        } catch (Exception e) {
                                            LOGGER.error("Failed to establish connection", e);
                                            ctx.close();
                                            return false;
                                        }
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        releaseResources();
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        LOGGER.error("QUIC stream error", cause);
                                        releaseResources();
                                        ctx.close();
                                    }

                                    private void releaseResources() {
                                        if (headerBuffer != null && headerBuffer.refCnt() > 0) {
                                            headerBuffer.release();
                                            headerBuffer = null;
                                        }
                                        if (socketChannel != null && socketChannel.isActive()) {
                                            socketChannel.close();
                                        }
                                    }
                                });
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}