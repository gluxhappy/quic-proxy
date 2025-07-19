package com.glux.proxyswitcher.service;

import java.io.IOException;

public class BridgeBroker {

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        QuicBridgeClient quicBridgeClient = new QuicBridgeClient();
        quicBridgeClient.setQuicHost("127.0.0.1");
        quicBridgeClient.setQuicPort(9999);
        quicBridgeClient.setTargetHost("www.baidu.com");
        quicBridgeClient.setTargetPort(80);
        quicBridgeClient.start();
        try {
            NioServer.start(
                    "127.0.0.1", 8899, 1024,
                    () -> true, quicBridgeClient
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
