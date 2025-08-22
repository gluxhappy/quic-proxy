# Proxy Switcher Chrome Extension

一个用于快速切换浏览器代理设置的Chrome扩展。

## 功能特性

- **直连 (DIRECT)**: 不使用代理
- **系统代理 (SYSTEM)**: 使用系统代理设置
- **PAC 代理**: 支持3个PAC脚本配置
- **HTTP 代理**: 支持3个HTTP代理服务器配置

## 安装方法

1. 打开Chrome浏览器，进入 `chrome://extensions/`
2. 开启"开发者模式"
3. 点击"加载已解压的扩展程序"
4. 选择此项目文件夹

## 使用方法

1. 点击浏览器工具栏中的扩展图标
2. 选择需要的代理类型
3. 代理设置会立即生效

## 文件结构

```
├── manifest.json     # 扩展清单文件
├── popup.html        # 弹出窗口界面
├── popup.js          # 弹出窗口逻辑
├── background.js     # 后台脚本
├── config.js         # 代理配置文件
├── icons/            # 图标文件夹
└── README.md         # 说明文档
```