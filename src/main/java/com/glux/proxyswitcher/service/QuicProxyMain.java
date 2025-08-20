package com.glux.proxyswitcher.service;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuicProxyMain {
    
    public static void main(String[] args) throws Exception {
        Logger rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if(rootLogger instanceof ch.qos.logback.classic.Logger rl) {
            String level = System.getProperty("log.level", "info");
            rl.setLevel(Level.toLevel(level));
        }
        if (args.length < 1) {
            System.out.println("用法:");
            System.out.println("  代理端: java QuicProxyMain client <TCP监听端口> <QUIC服务器地址> <QUIC服务器端口>");
            System.out.println("  服务端: java QuicProxyMain server <QUIC监听端口> <目标服务器地址> <目标服务器端口>");
            return;
        }
        
        String mode = args[0];
        
        if ("client".equals(mode)) {
            if (args.length != 4) {
                System.out.println("代理端参数错误");
                return;
            }
            
            int tcpPort = Integer.parseInt(args[1]);
            String quicHost = args[2];
            int quicPort = Integer.parseInt(args[3]);
            
            QuicProxy.ProxyClient client = new QuicProxy.ProxyClient(tcpPort, quicHost, quicPort);
            client.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
            Thread.currentThread().join();
            
        } else if ("server".equals(mode)) {
            if (args.length != 4) {
                System.out.println("服务端参数错误");
                return;
            }
            
            int quicPort = Integer.parseInt(args[1]);
            String targetHost = args[2];
            int targetPort = Integer.parseInt(args[3]);
            
            QuicProxy.ProxyServer server = new QuicProxy.ProxyServer(quicPort, targetHost, targetPort);
            server.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            Thread.currentThread().join();
            
        } else {
            System.out.println("未知模式: " + mode);
        }
    }
}