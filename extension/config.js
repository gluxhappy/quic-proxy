// 代理配置文件 - 可根据需要修改
const PROXY_SETTINGS = {
  // PAC 脚本 URL
  pac: {
    pac1: 'http://example.com/pac1.pac',
    pac2: 'http://example.com/pac2.pac', 
    pac3: 'http://example.com/pac3.pac'
  },
  
  // HTTP 代理服务器
  http: {
    http1: { host: '127.0.0.1', port: 8080 },
    http2: { host: '127.0.0.1', port: 8081 },
    http3: { host: '127.0.0.1', port: 8082 }
  }
};