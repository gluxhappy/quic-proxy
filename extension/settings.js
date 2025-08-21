// 默认设置
const DEFAULT_SETTINGS = {
  enableConnectionErrors: true,
  enableRequestErrors: true,
  httpStatusThreshold: 400
};

// 加载设置
function loadSettings() {
  chrome.storage.local.get('logSettings', (result) => {
    const settings = { ...DEFAULT_SETTINGS, ...result.logSettings };
    
    document.getElementById('enableConnectionErrors').checked = settings.enableConnectionErrors;
    document.getElementById('enableRequestErrors').checked = settings.enableRequestErrors;
    document.getElementById('httpStatusThreshold').value = settings.httpStatusThreshold;
  });
}

// 保存设置
function saveSettings() {
  const settings = {
    enableConnectionErrors: document.getElementById('enableConnectionErrors').checked,
    enableRequestErrors: document.getElementById('enableRequestErrors').checked,
    httpStatusThreshold: parseInt(document.getElementById('httpStatusThreshold').value)
  };
  
  chrome.storage.local.set({ logSettings: settings }, () => {
    showSaveStatus();
  });
}

// 显示状态消息
function showStatus(message, isError = false) {
  const status = document.getElementById('saveStatus');
  status.textContent = message;
  status.className = `status ${isError ? 'error' : 'success'}`;
  status.style.display = 'block';
  
  setTimeout(() => {
    status.style.display = 'none';
  }, 3000);
}

// 显示保存状态
function showSaveStatus() {
  showStatus(getMessage('settingsSaved') || '设置已保存');
}

// 导出配置
function exportConfig() {
  chrome.storage.local.get(['logSettings', 'proxyConfigs', 'proxyNames'], (result) => {
    const config = {
      version: '1.0',
      timestamp: new Date().toISOString(),
      data: {
        logSettings: result.logSettings || DEFAULT_SETTINGS,
        proxyConfigs: result.proxyConfigs || {},
        proxyNames: result.proxyNames || {}
      }
    };
    
    const blob = new Blob([JSON.stringify(config, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `proxy-switcher-config-${new Date().toISOString().split('T')[0]}.json`;
    a.click();
    URL.revokeObjectURL(url);
    
    showStatus(getMessage('configExported') || '配置已导出');
  });
}

// 导入配置
function importConfig(file) {
  const reader = new FileReader();
  reader.onload = function(e) {
    try {
      const config = JSON.parse(e.target.result);
      
      if (!config.data) {
        throw new Error('无效的配置文件格式');
      }
      
      // 合并配置
      const updateData = {};
      if (config.data.logSettings) updateData.logSettings = config.data.logSettings;
      if (config.data.proxyConfigs) updateData.proxyConfigs = config.data.proxyConfigs;
      if (config.data.proxyNames) updateData.proxyNames = config.data.proxyNames;
      
      chrome.storage.local.set(updateData, () => {
        loadSettings();
        showStatus(getMessage('configImported') || '配置已导入');
      });
      
    } catch (error) {
      showStatus(getMessage('importError') || '导入失败：文件格式错误', true);
    }
  };
  reader.readAsText(file);
}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
  initI18n();
  loadSettings();
  
  document.getElementById('saveSettings').addEventListener('click', saveSettings);
  document.getElementById('exportConfig').addEventListener('click', exportConfig);
  
  document.getElementById('importConfig').addEventListener('click', () => {
    document.getElementById('importFile').click();
  });
  
  document.getElementById('importFile').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      importConfig(file);
      e.target.value = ''; // 清空文件选择
    }
  });
});