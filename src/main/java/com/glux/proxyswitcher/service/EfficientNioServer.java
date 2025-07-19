package com.glux.proxyswitcher.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;

import java.util.List;

public class EfficientNioServer {
    
    private final int port;
    
    // 共享的工作线程池，用于处理所有连接的IO操作
    private final EventLoopGroup workerGroup;
    
    public EfficientNioServer(int port, int workerThreads) {
        this.port = port;
        // 创建工作线程池，指定线程数量
        this.workerGroup = new NioEventLoopGroup(workerThreads);
    }
    
    public void start() throws Exception {
        try {
            // boss线程组仅接受连接
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup) // 使用共享的workerGroup处理所有IO
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new ByteToMessageDecoder() {
                             @Override
                             protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                                 // 简单的解码示例，实际应用中可以根据需求自定义
                                 if (in.readableBytes() >= 4) {
                                     // 读取消息长度
                                     int length = in.readInt();
                                     if (in.readableBytes() >= length) {
                                         ByteBuf data = in.readBytes(length);
                                         out.add(data);
                                     } else {
                                         // 数据不足，重置读索引
                                         in.resetReaderIndex();
                                     }
                                 }
                             }
                         },
                         new SimpleChannelInboundHandler<ByteBuf>() {
                             @Override
                             protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                 // 处理解码后的消息
                                 String receivedMessage = msg.toString(CharsetUtil.UTF_8);
                                 System.out.println("Received: " + receivedMessage);
                                 
                                 // 回复消息
                                 ByteBuf response = Unpooled.copiedBuffer("Response: " + receivedMessage, CharsetUtil.UTF_8);
                                 ctx.writeAndFlush(response);
                             }
                         }
                     );
                 }
             });
            
            // 绑定端口并启动服务器
            ChannelFuture f = b.bind(port).sync();
            System.out.println("Server started on port " + port);
            
            // 等待服务器关闭
            f.channel().closeFuture().sync();
        } finally {
            // 不关闭workerGroup，因为它可能被其他服务共享
            // workerGroup.shutdownGracefully();
        }
    }
    
    // 关闭服务器时调用
    public void shutdown() {
        workerGroup.shutdownGracefully();
    }
    
    public static void main(String[] args) throws Exception {
        int port = 8080;
        // 创建工作线程数为处理器核心数的2倍
        int workerThreads = Runtime.getRuntime().availableProcessors() * 2;
        
        EfficientNioServer server = new EfficientNioServer(port, workerThreads);
        server.start();
    }
}