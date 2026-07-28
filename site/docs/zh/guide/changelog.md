# 更新日志

## 0.3.0（TBD）

### 特性

- 实现 JFR 分析
- GC 日志分析：支持指标（Metrics）
- 堆快照分析：支持更多分析选项，新增 JVM 参数和环境变量页签
- 线程快照分析：支持 JDK 21
- 支持通过 `jpackage` 将 Jifa 打包为二进制安装包（单机模式）
- 新增用于集群部署的 Helm charts，合并静态与弹性调度策略
- 新增配置项：`allow-login`、`open-browser-when-ready`、`disabled-file-transfer-methods`、`security-filters-enabled` 和 `cluster-namespace`
- 支持配置文件上传大小限制，并提供友好的错误提示

### 改进

- 升级至 Spring Boot 4.1 和 Eclipse MAT 1.16.1
- 单机模式下服务就绪后自动打开浏览器
- 根据浏览器语言自动选择界面语言

### 安全

- 默认禁用 SCP 文件传输方式
- 限制 URL 文件传输方式仅支持 http(s) 协议

## 0.2.0（2023-10）

### 特性

- 实现 GC 日志分析
- 实现线程快照分析
- 项目重构，基于新框架
- 重新实现一些内部模块，如登录、存储管理

## 0.1.0（2020-8）

- 项目开源，主要包含了堆快照分析功能