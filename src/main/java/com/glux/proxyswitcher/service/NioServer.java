package com.glux.proxyswitcher.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NioServer {

    public static void start(String host, int port, int bufferSize, Supplier<Boolean> stopFlag, ChannelBridgeFactory bridgeFactory) throws IOException {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(host, port));
            serverSocketChannel.configureBlocking(false);
            ExecutorService executorService = null;
            try {
                executorService = Executors.newFixedThreadPool(10);
                try (Selector selector = Selector.open()) {
                    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                    while (Optional.ofNullable(stopFlag).map(Supplier::get).orElse(false)) {
                        selector.select();
                        Set<SelectionKey> selectedKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = selectedKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            if (key.isAcceptable()) {
                                SocketChannel socketChannel = serverSocketChannel.accept();
                                socketChannel.configureBlocking(false);
                                ChannelBridge bridge = null;
                                try {
                                    bridge = bridgeFactory.newInstance(socketChannel);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                // bridge.onRead(onRead(socketChannel, bridge));
                                socketChannel.register(selector, SelectionKey.OP_READ,
                                        new SocketChannelAttachment(bridge, ByteBuffer.allocate(bufferSize), ByteBuffer.allocate(bufferSize))
                                );
                            }
                            if (key.isReadable()) {
                                handleRead(executorService, key);
                            }
                        }
                    }
                }
            } finally {
                executorService.shutdown();
            }
        }
    }

    private static Consumer<ByteBuffer> onRead(final SocketChannel socketChannel, final ChannelBridge bridge) {
        return byteBuffer -> {
            try {
                while (byteBuffer.hasRemaining()) {
                    socketChannel.write(byteBuffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    bridge.close();
                } catch (IOException e1) {
                    e.printStackTrace();
                }
            }
        };
    }

    private static void handleRead(ExecutorService executorService, SelectionKey key) {
        final SocketChannel socketChannel = (SocketChannel) key.channel();
        final SocketChannelAttachment attachment = (SocketChannelAttachment) key.attachment();
        final ByteBuffer readBuffer = attachment.getReadBuffer();
        executorService.submit(() -> {
            synchronized (attachment) {
                try {
                    if(!socketChannel.isOpen()){
                        return;
                    }
                    int bytesRead = socketChannel.read(readBuffer);
                    if(bytesRead == 0) {
                        return;
                    }
                    if (bytesRead == -1) {
                        socketChannel.close();
                        key.cancel();
                        System.out.println("Connection closed by client: " + socketChannel.getRemoteAddress());
                        try {
                            attachment.getBridge().close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    readBuffer.flip();

                    attachment.getBridge().write(readBuffer);
                    readBuffer.clear();
//                byte[] data = new byte[readBuffer.remaining()];
//                readBuffer.get(data);
//
//                String message = new String(data);
//                System.out.println("Received from client: " + message);
//
//                ByteBuffer responseBuffer = ByteBuffer.wrap(("Server received: " + message).getBytes());
//                socketChannel.write(responseBuffer);

                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        key.cancel();
                        socketChannel.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

    }

    public static class SocketChannelAttachment {
        private ChannelBridge bridge;
        private ByteBuffer readBuffer;
        private ByteBuffer writeBuffer;

        public SocketChannelAttachment(ChannelBridge bridge, ByteBuffer readBuffer, ByteBuffer writeBuffer) {
            this.bridge = bridge;
            this.readBuffer = readBuffer;
            this.writeBuffer = writeBuffer;
        }

        public ChannelBridge getBridge() {
            return bridge;
        }

        public void setBridge(ChannelBridge bridge) {
            this.bridge = bridge;
        }

        public ByteBuffer getReadBuffer() {
            return readBuffer;
        }

        public void setReadBuffer(ByteBuffer readBuffer) {
            this.readBuffer = readBuffer;
        }

        public ByteBuffer getWriteBuffer() {
            return writeBuffer;
        }

        public void setWriteBuffer(ByteBuffer writeBuffer) {
            this.writeBuffer = writeBuffer;
        }
    }

    public interface ChannelBridge {
        void write(ByteBuffer buffer) throws IOException;

        void close() throws IOException;
    }

    public interface ChannelBridgeFactory {
        ChannelBridge newInstance(SocketChannel socketChannel) throws IOException, InterruptedException;
    }
}