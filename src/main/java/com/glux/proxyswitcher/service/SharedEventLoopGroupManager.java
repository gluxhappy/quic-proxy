package com.glux.proxyswitcher.service;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * 管理共享的EventLoopGroup实例
 */
public class SharedEventLoopGroupManager {
    
    private static final SharedEventLoopGroupManager INSTANCE = new SharedEventLoopGroupManager();
    
    // 用于接受连接的boss线程组
    private final EventLoopGroup bossGroup;
    
    // 用于处理IO的工作线程组
    private final EventLoopGroup workerGroup;
    
    private SharedEventLoopGroupManager() {
        // 创建boss线程组，通常只需要少量线程
        this.bossGroup = new NioEventLoopGroup(1);
        
        // 创建工作线程组，线程数为处理器核心数的2倍
        int workerThreads = Runtime.getRuntime().availableProcessors() * 2;
        this.workerGroup = new NioEventLoopGroup(workerThreads);
    }
    
    public static SharedEventLoopGroupManager getInstance() {
        return INSTANCE;
    }
    
    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }
    
    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }
    
    /**
     * 应用程序关闭时调用此方法释放资源
     */
    public void shutdown() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}