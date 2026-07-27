# Changelog

## 0.3.0 (TBD)

### Features

- Add support for JFR analysis
- GC log analysis: add support for metrics
- Heap dump analysis: add more analysis options, and new tabs for JVM options and environment variables
- Thread dump analysis: add support for JDK 21
- Add support for packaging Jifa into a binary installation package via `jpackage` (standalone mode)
- Add Helm charts for cluster deployment, and consolidate the static and elastic scheduling strategies
- New configurations: `allow-login`, `open-browser-when-ready`, `disabled-file-transfer-methods`, `security-filters-enabled` and `cluster-namespace`
- Add configurable file upload size limits with friendly error messages

### Improvements

- Upgrade to Spring Boot 4.1 and Eclipse MAT 1.16.1
- Open the browser automatically when the server is ready in standalone mode
- Select the locale based on the browser language

### Security

- Disable the SCP file transfer method by default
- Restrict the file transfer URL scheme to http(s)

## 0.2.0 (2023-10)

### Feature

- add support for GC log analysis
- add support for thread dump analysis
- refactor using new frameworks
- reimplement some internal modules, such as login and storage management

## 0.1.0 (2020-08)

- open source, primarily including heap dump analysis