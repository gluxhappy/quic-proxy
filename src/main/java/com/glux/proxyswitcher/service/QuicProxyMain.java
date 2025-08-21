package com.glux.proxyswitcher.service;

import ch.qos.logback.classic.Level;
import com.glux.proxyswitcher.util.CertificateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuicProxyMain {

    public static void main(String[] args) throws Exception {
        if ("trace".equalsIgnoreCase(System.getProperty("log.level", "error"))) {
            System.setProperty("javax.net.debug", "all");
        }
        Logger rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (rootLogger instanceof ch.qos.logback.classic.Logger rl) {
            String level = System.getProperty("log.level", "trace");
            rl.setLevel(Level.toLevel(level));
        }
        if (args.length < 1) {
            System.out.println("用法:");
            System.out.println("  代理端: client <TCP监听地址> <TCP监听端口> <QUIC服务器地址> <QUIC服务器端口>");
            System.out.println("  服务端: server <QUIC监听地址> <QUIC监听端口> <目标服务器地址> <目标服务器端口>");
            System.out.println("  服务端: cert");
            return;
        }

        String mode = args[0];

        if ("client".equals(mode)) {
            if (args.length != 5) {
                System.out.println("代理端参数错误");
                return;
            }
            String tcpHost = args[1];
            int tcpPort = Integer.parseInt(args[2]);
            String quicHost = args[3];
            int quicPort = Integer.parseInt(args[4]);

            QuicProxy.ProxyClient client = new QuicProxy.ProxyClient(tcpHost, tcpPort, quicHost, quicPort);
            client.start();

            Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
            Thread.currentThread().join();

        } else if ("server".equals(mode)) {
            if (args.length != 5) {
                System.out.println("服务端参数错误");
                return;
            }

            String quicHost = args[1];
            int quicPort = Integer.parseInt(args[2]);
            String targetHost = args[3];
            int targetPort = Integer.parseInt(args[4]);

            QuicProxy.ProxyServer server = new QuicProxy.ProxyServer(quicHost, quicPort, targetHost, targetPort);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            Thread.currentThread().join();

        } else if ("cert".equals(mode)) {
            CertificateGenerator.generateCertificates("Microsoft", "auth.azure.com", "u1082983.azure.com");
        } else {
            System.out.println("未知模式: " + mode);
        }
    }
}