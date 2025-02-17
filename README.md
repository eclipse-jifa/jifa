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

> [中文](https://github.com/eclipse/jifa/blob/main/README_zh.md)

## Introduction

Online Analyzer for Heap Dump, GC Log, Thread Dump and JFR File.

Please refer to [GitHub Pages](https://eclipse-jifa.github.io/jifa/) for more information.

## Quick Start

### [Playground 🛝](https://jifa.dragonwell-jdk.io)

### Run Jifa Locally

```shell
# Default service address is at http://localhost:8102
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash

# Change the server port
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- -p <port>

# Analyze local files
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- <file1 path> <file2 path> ...

# Set JVM Options
curl -fsSL https://raw.githubusercontent.com/eclipse/jifa/main/jifa.sh | bash -s -- --jvm-options "<JVM options>"
```

Note: Please make sure that Docker is installed.

## Links
- [GitHub Pages](https://eclipse-jifa.github.io/jifa/)
