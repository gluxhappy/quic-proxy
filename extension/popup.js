// 获取动态代理配置
function getProxyConfig(proxyType) {
  if (proxyType === 'direct') return { mode: 'direct' };
  if (proxyType === 'system') return { mode: 'system' };
  
  if (proxyType.startsWith('pac')) {
    const url = document.getElementById(`${proxyType}-url`).value;
    return { mode: 'pac_script', pacScript: { url } };
  }
  
  if (proxyType.startsWith('http')) {
    const server = document.getElementById(`${proxyType}-server`).value;
    const bypass = document.getElementById(`${proxyType}-bypass`).value;
    const [host, port] = server.split(':');
    
    const config = {
      mode: 'fixed_servers',
      rules: { singleProxy: { scheme: 'http', host, port: parseInt(port) } }
    };
    
    if (bypass.trim()) {
      config.rules.bypassList = bypass.split(',').map(s => s.trim());
    }
    
    return config;
  }
}

// 获取当前代理状态
async function getCurrentProxy() {
  return new Promise((resolve) => {
    chrome.proxy.settings.get({}, (config) => {
      resolve(config.value);
    });
  });
}

// 设置代理
function setProxy(proxyType) {
  const config = getProxyConfig(proxyType);
  chrome.proxy.settings.set({ value: config }, () => {
    if (chrome.runtime.lastError) {
      storeError({
        message: '代理设置失败',
        details: { proxyType, error: chrome.runtime.lastError.message },
        type: 'proxy_set_error'
      });
    } else {
      chrome.storage.local.set({ currentProxy: proxyType });
      saveProxyConfigs();
      updateStatus();
    }
  });
}

// 存储错误日志
function storeError(error) {
  const errorLog = {
    timestamp: new Date().toISOString(),
    message: error.message || error.toString(),
    details: error.details || null,
    type: error.type || 'general_error'
  };
  
  chrome.storage.local.get('errorLogs', (result) => {
    const logs = result.errorLogs || [];
    logs.unshift(errorLog);
    
    if (logs.length > 100) {
      logs.splice(100);
    }
    
    chrome.storage.local.set({ errorLogs: logs });
  });
}

// 保存代理配置
function saveProxyConfigs() {
  const configs = {};
  const names = {};
  
  ['direct', 'system', 'pac1', 'pac2', 'pac3', 'http1', 'http2', 'http3'].forEach(type => {
    const nameElement = document.getElementById(`${type}-name`);
    if (nameElement) names[type] = nameElement.textContent;
  });
  
  ['pac1', 'pac2', 'pac3'].forEach(type => {
    configs[`${type}-url`] = document.getElementById(`${type}-url`).value;
  });
  ['http1', 'http2', 'http3'].forEach(type => {
    configs[`${type}-server`] = document.getElementById(`${type}-server`).value;
    configs[`${type}-bypass`] = document.getElementById(`${type}-bypass`).value;
  });
  
  chrome.storage.local.set({ proxyConfigs: configs, proxyNames: names });
}

// 加载代理配置
function loadProxyConfigs() {
  chrome.storage.local.get(['proxyConfigs', 'proxyNames'], (result) => {
    if (result.proxyConfigs) {
      Object.entries(result.proxyConfigs).forEach(([key, value]) => {
        const element = document.getElementById(key);
        if (element) element.value = value;
      });
    }
    
    if (result.proxyNames) {
      Object.entries(result.proxyNames).forEach(([type, name]) => {
        const nameElement = document.getElementById(`${type}-name`);
        if (nameElement) nameElement.textContent = name;
      });
    }
  });
}

// 显示/隐藏配置输入框
function toggleConfigInput(proxyType) {
  document.querySelectorAll('.config-input').forEach(el => el.classList.remove('show'));
  if (proxyType.startsWith('pac') || proxyType.startsWith('http')) {
    document.getElementById(`${proxyType}-config`).classList.add('show');
  }
}

// 更新状态显示
async function updateStatus() {
  const current = await chrome.storage.local.get('currentProxy');
  const proxyType = current.currentProxy || 'direct';
  document.getElementById('currentProxy').textContent = getProxyDisplayName(proxyType);
  document.getElementById(proxyType).checked = true;
  toggleConfigInput(proxyType);
}

// 获取代理显示名称
function getProxyDisplayName(type) {
  const nameElement = document.getElementById(`${type}-name`);
  return nameElement ? nameElement.textContent : getMessage('unknown') || '未知';
}

// 更新代理名称显示
function updateProxyName(type, name) {
  const nameElement = document.getElementById(`${type}-name`);
  const renameInput = document.getElementById(`${type}-rename`);
  if (nameElement && name.trim()) {
    nameElement.textContent = name;
    if (renameInput) renameInput.value = '';
  }
}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
  initI18n();
  loadProxyConfigs();
  updateStatus();
  
  // 日志查看按钮
  document.getElementById('viewLogs').addEventListener('click', () => {
    chrome.tabs.create({ url: chrome.runtime.getURL('logs.html') });
  });
  
  // 设置按钮
  document.getElementById('openSettings').addEventListener('click', () => {
    chrome.tabs.create({ url: chrome.runtime.getURL('settings.html') });
  });
  
  // 添加事件监听
  document.querySelectorAll('input[name="proxy"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        toggleConfigInput(e.target.value);
        setProxy(e.target.value);
      }
    });
  });
  
  // 配置输入框变化时保存
  document.querySelectorAll('.config-input input').forEach(input => {
    input.addEventListener('blur', saveProxyConfigs);
  });
  
  // 名称输入框事件
  document.querySelectorAll('.name-input').forEach(input => {
    input.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        const type = e.target.id.replace('-rename', '');
        const newName = e.target.value.trim();
        if (newName) {
          updateProxyName(type, newName);
          saveProxyConfigs();
        }
      }
    });
    
    input.addEventListener('blur', (e) => {
      const type = e.target.id.replace('-rename', '');
      const newName = e.target.value.trim();
      if (newName) {
        updateProxyName(type, newName);
        saveProxyConfigs();
      }
    });
  });
  
  // 双击名称进行编辑
  document.querySelectorAll('.proxy-name').forEach(nameSpan => {
    nameSpan.addEventListener('dblclick', (e) => {
      const type = e.target.id.replace('-name', '');
      const renameInput = document.getElementById(`${type}-rename`);
      if (renameInput) {
        renameInput.value = e.target.textContent;
        renameInput.style.display = 'inline';
        renameInput.focus();
        renameInput.select();
      }
    });
  });
  
  // 点击其他地方隐藏输入框
  document.addEventListener('click', (e) => {
    if (!e.target.classList.contains('name-input')) {
      document.querySelectorAll('.name-input').forEach(input => {
        if (input.style.display === 'inline') {
          input.style.display = 'none';
        }
      });
    }
  });
});