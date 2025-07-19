package com.glux.proxyswitcher.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.CharsetUtil;

/**
 * 演示如何在多个服务器之间共享EventLoopGroup
 */
public class MultiServerExample {
    
    /**
     * 启动一个服务器，使用共享的EventLoopGroup
     */
    public static ChannelFuture startServer(int port, String serverName) {
        // 获取共享的EventLoopGroup实例
        EventLoopGroup bossGroup = SharedEventLoopGroupManager.getInstance().getBossGroup();
        EventLoopGroup workerGroup = SharedEventLoopGroupManager.getInstance().getWorkerGroup();
        
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
         .channel(NioServerSocketChannel.class)
         .option(ChannelOption.SO_BACKLOG, 128)
         .childOption(ChannelOption.SO_KEEPALIVE, true)
         .childHandler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ch.pipeline().addLast(
                     // 添加长度字段解码器，处理粘包/拆包问题
                     new LengthFieldBasedFrameDecoder(65535, 0, 4, 0, 4),
                     // 添加长度字段编码器
                     new LengthFieldPrepender(4),
                     new SimpleChannelInboundHandler<ByteBuf>() {
                         @Override
                         protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                             String message = msg.toString(CharsetUtil.UTF_8);
                             System.out.println(serverName + " received: " + message);
                             
                             // 回复消息
                             ByteBuf response = Unpooled.copiedBuffer(
                                 serverName + " response: " + message, CharsetUtil.UTF_8);
                             ctx.writeAndFlush(response);
                         }
                         
                         @Override
                         public void channelActive(ChannelHandlerContext ctx) {
                             System.out.println("New connection on " + serverName + 
                                 " from " + ctx.channel().remoteAddress());
                         }
                     }
                 );
             }
         });
        
        // 绑定端口并启动服务器
        try {
            ChannelFuture f = b.bind(port).sync();
            System.out.println(serverName + " started on port " + port);
            return f;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static void main(String[] args) throws Exception {
        // 启动多个服务器，共享同一个EventLoopGroup
        ChannelFuture server1 = startServer(8080, "Server-1");
        ChannelFuture server2 = startServer(8081, "Server-2");
        ChannelFuture server3 = startServer(8082, "Server-3");
        
        // 等待所有服务器关闭
        if (server1 != null) {
            server1.channel().closeFuture().sync();
        }
        
        // 应用程序关闭时释放资源
        SharedEventLoopGroupManager.getInstance().shutdown();
    }
}