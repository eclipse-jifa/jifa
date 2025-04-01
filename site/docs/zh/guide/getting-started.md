# 快速上手

## Docker

```shell
$ docker run -p 8102:8102 eclipsejifa/jifa
```

[镜像链接](https://hub.docker.com/r/eclipsejifa/jifa/tags)

## jifa.sh

```shell
# http://localhost:8102
$ curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash

# 修改服务端口
$ curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- -p <port>

# 分析本地文件
$ curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- <file1 path> <file2 path> ...

# 添加 JVM 参数
$ curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- --jvm-options "<JVM options>"
```

::: tip 贴士
[jifa.sh](https://github.com/eclipse/jifa/blob/main/jifa.sh) 脚本内封装了 docker 启动命令，如果你想直接使用 docker
命令并修改服务端口或分析本地文件，请参考此脚本。
:::

## jifa-jpackage.{sh | bat}

使用`jpackage`命令打包成二进制安装包，便于在本地使用（单机模式）。

## 使用已发布的构建

平台支持：linux/amd64、linux/arm64，其他平台请自行构建。

1. 从[此处](https://github.com/eclipse/jifa/releases)下载最新的构建。
2. 解压：`unzip jifa.zip`
3. 运行：`./jifa/bin/jifa`

## 从源码开始

### 环境要求

- JDK 17+
- Node.js 18+

### 运行

```shell
# 以 standalone worker 角色运行 
$ ./gradlew runStandaloneWorker

# 以 master 角色运行
$ ./gradlew runMaster

# 以 static worker 角色运行
$ ./gradlew runStaticWorker
```

你也可以在 IDE 中运行这些任务。

关于运行角色的介绍，请参考[部署](./deployment.md)。

### 前端

前端代码会被自动打包进后端，但是在修改代码后，不会自动重新打包。为了方便前端工程的开发调试，可以通过以下命令启动一个额外的进程：

```shell
$ cd frontend

$ npm ci

# http://localhost:8089
$ npm run dev
```

### 构建

```shell
$ ./gradlew build

# 跳过测试
$ ./gradlew build -x test
```

构建结果位于 `./server/build/distributions` 目录。

### Docker 镜像

可以使用项目根目录下的 [Dockerfile](https://github.com/eclipse/jifa/blob/main/Dockerfile)。