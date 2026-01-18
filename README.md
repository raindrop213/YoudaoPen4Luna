# YoudaoPen4Luna

一个 Android 应用，用于抓取有道词典笔扫描的内容，并通过 LunaTranslator 进行分词、查词和翻译。

## 使用说明

### 前置要求

需要安装并运行 [LunaTranslator](https://github.com/HIllya51/LunaTranslator) 的网络服务

### 配置步骤

1. 在系统设置中开启无障碍服务权限，并启用本应用的无障碍服务
2. 配置 LunaTranslator API 地址

### 使用方法

1. 使用有道词典笔扫描文本（分屏）
2. 应用会自动抓取扫描内容并显示在主界面
3. 文本会自动进行分词，点击任意分词可查看词典释义

## 项目结构

```
app/src/main/java/com/example/youdaoa11yservice/
├── MainActivity.kt          # 主界面，处理分词、翻译和词典查询
├── YoudaoA11yService.kt     # 无障碍服务，抓取有道词典笔内容
└── FlowLayout.kt            # 自定义流式布局组件
```

## License

本项目仅供学习和研究使用。
