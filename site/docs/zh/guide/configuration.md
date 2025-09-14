# 配置

本页梳理了服务端支持的配置。

各配置项的默认值由 [Configuration.java](https://github.com/eclipse/jifa/blob/main/server/src/main/java/org/eclipse/jifa/server/Configuration.java)
或默认配置文件 [application.yml](https://github.com/eclipse/jifa/blob/main/server/src/main/resources/application.yml)
提供。

通过启动参数 `--jifa.<key>=<value>` 或指定配置文件进行设置。

## role

运行角色，参考[部署](./deployment.md)。

类型：Enum

- `STANDALONE_WORKER`
- `MASTER`
- `ELASTIC_WORKER`
- `STATIC_WORKER`

默认值：`STANDALONE_WORKER`

## port

服务监听端口。

类型：int

默认值：8102

## storage-path

文件的存储路径。

类型：Path

默认值：`${user.home}/jifa-storage`

## database-host

数据库的地址，对于非 `STANDALONE_WORKER` 角色，必须设置。目前仅支持 MySQL。

类型：String

默认值：从环境变量 `MYSQL_HOST` 中读取，若未设置则为空。

## database-name

数据库的名称，对于非 `STANDALONE_WORKER` 角色，必须设置。

类型：String

默认值：从环境变量 `MYSQL_DATABASE` 中读取，若未设置则为 `jifa`。

## database-user

数据库的用户，对于非 `STANDALONE_WORKER` 角色，必须设置。

类型：String

默认值：从环境变量 `MYSQL_USER` 中读取，若未设置则为 `jifa`。

## database-password

数据库的密码，对于非 `STANDALONE_WORKER` 角色，必须设置。

类型：String

默认值：从环境变量 `MYSQL_PASSWORD` 中读取，若未设置则为 `jifa`。

## storage-pvc-name

集群中使用的 PersistentVolumeClaim 名称。

类型：String

默认值：null

## service-account-name

集群中使用的 ServiceAccount 名称。

类型: String

默认值: null

## image-pull-secret-name

集群中用于拉取 `WORKER` 镜像的密钥的名字。

类型: String

默认值: null

## elastic-worker-image

集群中用于运行 `WORKER` 节点的镜像。

类型：String

默认值：null

## elastic-worker-jvm-options

集群中用于设置 `ELASTIC_WORKER` 节点的 JVM 参数。

类型：String

默认值：null

## elastic-worker-port

集群中 `ELASTIC_WORKER` 节点的服务监听端口。

类型：int

默认值：8102

## elastic-worker-idle-threshold

集群中 `ELASTIC_WORKER` 节点的空闲时间阈值，单位为分钟，最小值为 2。当一个 `ELASTIC_WORKER` 节点的空闲时间超过此值时，将会自动停止。

类型：int

默认值：5

## allow-login

是否允许登陆。

当其值为 false 时，allow-anonymous-access 将被设置为 true，allow-registration 将被设置为 false。

Type: boolean

Default: false

## allow-anonymous-access

是否允许匿名用户访问。

类型：boolean

默认值：ture

## allow-registration

是否允许注册新用户。

类型：boolean

默认值：false

## admin-username

管理员账号的用户名。

类型：String

默认值：admin

如果设置为空，或已有注册用户则不会进行管理员账号注册

## admin-password

管理员账号的密码。

类型：String

默认值：password

## security-filters-enabled

是否启用默认的安全过滤器用于认证与授权。

如果需要自定义应用的安全认证方式，请将此参数设置为 false。

默认值: true

## input-files

本地的待分析文件，仅在 `STANDALONE_WORKER` 角色中使用。

类型：Path[]

默认值：null

## 文件上传配置

### spring.servlet.multipart.max-file-size

文件上传允许的最大文件大小。

类型：DataSize（如 "512MB"、"2GB"）

默认值：512MB

### spring.servlet.multipart.max-request-size

multipart/form-data 请求允许的最大大小。

类型：DataSize（如 "512MB"、"2GB"）

默认值：512MB

示例：
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB
```
