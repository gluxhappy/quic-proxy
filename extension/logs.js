// 加载并显示日志
function loadLogs() {
  chrome.storage.local.get('errorLogs', (result) => {
    const logs = result.errorLogs || [];
    const container = document.getElementById('logContainer');
    
    if (logs.length === 0) {
      container.innerHTML = `<div class="no-logs">${getMessage('noLogs')}</div>`;
      return;
    }
    
    const tableHtml = `
      <table class="log-table">
        <thead>
          <tr>
            <th>${getMessage('time')}</th>
            <th>${getMessage('type')}</th>
            <th>${getMessage('message')}</th>
            <th>${getMessage('details')}</th>
          </tr>
        </thead>
        <tbody>
          ${logs.map(log => {
            const timestamp = new Date(log.timestamp).toLocaleString('zh-CN');
            const typeClass = log.type.includes('error') ? 'error' : 
                             log.type.includes('warning') ? 'warning' : 'info';
            const details = log.details ? JSON.stringify(log.details, null, 2) : getMessage('none') || '无';
            
            return `
              <tr class="${typeClass}">
                <td class="timestamp">${timestamp}</td>
                <td><span class="type ${typeClass}">${log.type}</span></td>
                <td class="message">${log.message}</td>
                <td class="details">${details}</td>
              </tr>
            `;
          }).join('')}
        </tbody>
      </table>
    `;
    
    container.innerHTML = tableHtml;
  });
}

// 清空日志
function clearLogs() {
  if (confirm(getMessage('confirmClear'))) {
    chrome.storage.local.set({ errorLogs: [] }, () => {
      loadLogs();
    });
  }
}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
  initI18n();
  loadLogs();
  document.getElementById('refreshLogs').addEventListener('click', loadLogs);
  document.getElementById('clearLogs').addEventListener('click', clearLogs);
});