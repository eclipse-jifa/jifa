<!--
    Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0
 -->
# Eclipse Jifa

[![Eclipse License](https://img.shields.io/github/license/eclipse/jifa?label=License)](https://github.com/eclipse/jifa/blob/main/LICENSE)
![Commit Check](https://github.com/eclipse/jifa/actions/workflows/commit-check.yml/badge.svg?branch=main)

> [English](https://github.com/eclipse/jifa/blob/main/README.md)

## 简介

Eclipse Jifa 是一款在线分析工具，支持分析 Java 堆转储、GC 日志、线程转储以及 JFR 文件。

关于项目的更多信息请访问 [GitHub Pages](https://eclipse-jifa.github.io/jifa/zh/)。

## 快速上手

### [在线演示 🛝](https://jifa.dragonwell-jdk.io)

### 本地运行 Jifa

```shell
# 默认服务地址是 http://localhost:8102
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash

# 修改服务端口
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- -p <port>

# 分析本地文件
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- <file1 path> <file2 path> ...

# 设置 JVM 参数
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- --jvm-options "<JVM options>"
```

注：本地环境需要安装 docker

## 相关链接

- [GitHub Pages](https://eclipse-jifa.github.io/jifa/zh/)
- 钉钉交流群二维码

  <div>
    <img src=https://user-images.githubusercontent.com/33491035/226314386-e1cf71d4-8429-4e4c-bdc0-c511a9009ee1.JPG alt="DingTalk" width=35%/>
  </div>
