# QUIC代理转发程序

基于QUIC协议的转发程序，包含代理端和服务端。

## 功能

- **代理端**: 监听TCP端口，将连接数据通过QUIC stream转发到服务端
- **服务端**: 监听QUIC端口，将收到的stream数据转发到指定TCP服务器

## 使用方法

### 编译
```bash
mvn clean compile
```

### 启动服务端
```bash
java -cp target/classes com.glux.proxyswitcher.service.QuicProxyMain server 9443 localhost 8080
```
参数说明：
- `server`: 服务端模式
- `9443`: QUIC监听端口
- `localhost`: 目标服务器地址
- `8080`: 目标服务器端口

### 启动代理端
```bash
java -cp target/classes com.glux.proxyswitcher.service.QuicProxyMain client 8888 localhost 9443
```
参数说明：
- `client`: 代理端模式
- `8888`: TCP监听端口
- `localhost`: QUIC服务器地址
- `9443`: QUIC服务器端口

## 工作流程

1. 客户端连接到代理端的TCP端口(8888)
2. 代理端建立到服务端的QUIC连接，创建双向stream
3. TCP数据通过QUIC stream转发到服务端
4. 服务端将stream数据转发到目标TCP服务器(localhost:8080)
5. 响应数据原路返回

## 依赖

- Netty QUIC (netty-incubator-codec-http3)
- BouncyCastle (用于SSL/TLS)