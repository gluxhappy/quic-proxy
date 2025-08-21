// 设置缓存
var logSettingsCache = {
  enableConnectionErrors: true,
  enableRequestErrors: true,
  httpStatusThreshold: 400
};

// 加载设置到缓存
function loadSettingsCache() {
  chrome.storage.local.get('logSettings', function(result) {
    if (result.logSettings) {
      logSettingsCache = result.logSettings;
    }
  });
}

// 监听设置变化
chrome.storage.onChanged.addListener(function(changes, namespace) {
  if (namespace === 'local' && changes.logSettings) {
    logSettingsCache = changes.logSettings.newValue;
  }
});

// 扩展安装时初始化
chrome.runtime.onInstalled.addListener(function() {
  chrome.storage.local.set({ 
    currentProxy: 'direct', 
    errorLogs: [],
    logSettings: logSettingsCache
  });
  console.log(chrome.i18n.getMessage('proxyInitialized'));
});

// 启动时加载缓存
loadSettingsCache();

// 存储错误日志
function storeError(error) {
  // 使用缓存的设置，避免频繁读取localStorage
  if ((error.type === 'request_error' && !logSettingsCache.enableConnectionErrors) ||
      (error.type === 'http_error' && !logSettingsCache.enableRequestErrors)) {
    return;
  }
  
  var errorLog = {
    timestamp: new Date().toISOString(),
    message: error.message || error.toString(),
    details: error.details || null,
    type: error.type || 'proxy_error'
  };
  
  chrome.storage.local.get('errorLogs', function(result) {
    var logs = result.errorLogs || [];
    logs.unshift(errorLog);
    
    if (logs.length > 100) {
      logs.splice(100);
    }
    
    chrome.storage.local.set({ errorLogs: logs });
  });
}

// 监听代理设置错误
chrome.proxy.onProxyError.addListener(function(details) {
  storeError({
    message: chrome.i18n.getMessage('proxySetError'),
    details: details,
    type: 'proxy_error'
  });
});

// 监听网络请求错误
chrome.webRequest.onErrorOccurred.addListener(
  function(details) {
    storeError({
      message: chrome.i18n.getMessage('networkRequestFailed'),
      details: {
        url: details.url,
        error: details.error,
        method: details.method,
        tabId: details.tabId,
        timeStamp: details.timeStamp
      },
      type: 'request_error'
    });
  },
  { urls: ['<all_urls>'] }
);

// 监听请求完成（记录HTTP错误状态码）
chrome.webRequest.onCompleted.addListener(
  function(details) {
    // 使用缓存的设置
    if (details.statusCode >= logSettingsCache.httpStatusThreshold) {
      storeError({
        message: chrome.i18n.getMessage('httpErrorResponse'),
        details: {
          url: details.url,
          statusCode: details.statusCode,
          method: details.method,
          tabId: details.tabId,
          timeStamp: details.timeStamp
        },
        type: 'http_error'
      });
    }
  },
  { urls: ['<all_urls>'] }
);
