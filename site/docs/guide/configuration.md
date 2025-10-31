# Configuration

This page outlines the configurations supported by Jifa Server.

The default value of each configuration is provided
by [Configuration.java](https://github.com/eclipse/jifa/blob/main/server/src/main/java/org/eclipse/jifa/server/Configuration.java)
or the default configuration
file [application.yml](https://github.com/eclipse/jifa/blob/main/server/src/main/resources/application.yml).

Configure by adding application arguments `--jifa.<key>=<value>` or specifying a configuration file.

## role

The running role. Refer to [deployment](./deployment.md) for more information.

Type: Enum

- `STANDALONE_WORKER`
- `MASTER`
- `ELASTIC_WORKER`
- `STATIC_WORKER`

Default: `STANDALONE_WORKER`

## port

The service port.

Type: int

Default: 8102

## storage-path

The storage path.

Type: Path

Default: `${user.home}/jifa-storage`

## database-host

Database address, required if the role is not `STANDALONE_WORKER`. Currently only MySQL is supported.

Type：String

Default：Read from the environment variable `MYSQL_HOST`, and empty string is used if it's not set.

## database-name

Database name, required if the role is not `STANDALONE_WORKER`.

Type：String

Default：Read from the environment variable `MYSQL_DATABASE`, and `jifa` is used if it's not set.

## database-user

Database username, required if the role is not `STANDALONE_WORKER`.

Type：String

Default：Read from the environment variable `MYSQL_USER`, and `jifa` is used if it's not set.

## database-password

Database password, required if the role is not `STANDALONE_WORKER`.

Type：String

Default：Read from the environment variable `MYSQL_PASSWORD`, and `jifa` is used if it's not set.

## storage-pvc-name

The name of PersistentVolumeClaim used in the cluster.

Type: String

Default: null

## service-account-name

The name of ServiceAccount used in the cluster.

Type: String

Default: null

## image-pull-secret-name

The name of image pull secret

Type: String

Default: null

## elastic-worker-image

Docker image used in the cluster to run `ELASTIC_WORKER` nodes.

Type: String

Default: null

## elastic-worker-jvm-options

JVM options used by `ELASTIC_WORKER` nodes in the cluster

Type：String

Default：null

## elastic-worker-port

The service port of `ELASTIC_WORKER` nodes in the cluster.

Type: int

Default: 8102

## elastic-worker-idle-threshold

The idle threshold of `ELASTIC_WORKER` nodes in the cluster, in minutes, with a minimum value of 2. When
a `ELASTIC_WORKER` node is idle for more than this threshold, it will automatically stop.

Type: int

Default: 5

## allow-login

Whether to allow login.

When it is false, allow-anonymous-access will be set to true and allow-registration will be set to false automatically.

Type: boolean

Default: false

## allow-anonymous-access

Whether to allow anonymous user access.

Type: boolean

Default: true

## allow-registration

Whether to allow the registration of new users.

Type: boolean

Default: false

## admin-username

The username of the administrator account.

Type: String

Default: admin

If set to blank or if there are already registered users, administrator account registration will not be performed.

## admin-password

The password of the administrator account.

Type: String

Default: password

## security-filters-enabled

Whether to enable the default security filters for web authentication. If set to 'false', the web security filters will not be enabled.

Use this option if you intend to customize JIFA with an alternative authentication and authorization layer.

Default: true

## input-files

Local files to be analyzed, used only in `STANDALONE_WORKER` role.

Type: Path[]

Default: null

## File Upload Configuration

### spring.servlet.multipart.max-file-size

Maximum file size allowed for file uploads.

Type: DataSize (e.g., "512MB", "2GB")

Default: 512MB

### spring.servlet.multipart.max-request-size

Maximum size allowed for multipart/form-data requests.

Type: DataSize (e.g., "512MB", "2GB")

Default: 512MB

Example:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB
```
