#!/bin/sh
# Copyright (c) 2025 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

set -e

./gradlew clean build -x test

# Path of the input directory that contains the files to be packaged (absolute path or relative to the current directory)
# All files in the input directory will be packaged into the application image.
inputPath="server/build/libs"
destinationPath="server/build/distributions"

name="Eclipse Jifa"
osName=$(uname -s)
version="1.0.0"

if [ "${osName}" = "Darwin" ]; then
  iconPath=package/macOS/launcher.icns
else
  iconPath=package/linux/launcher.png
fi

echo "Packaging ${name}..."

# For "jpackage", please refer to: https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html#GUID-C1027043-587D-418D-8188-EF8F44A4C06A
jpackage --name "${name}" \
        --vendor "Eclipse" \
        --app-version "${version}" \
        --description "Eclipse Jifa (abbreviated as Jifa) stands for \"Java Issues Finding Assistant\". Online Analyzer for Heap Dump, GC Log, Thread Dump and JFR File." \
        --input "${inputPath}" \
        --dest "${destinationPath}" \
        --main-jar jifa.jar \
        --java-options --add-opens=java.base/java.lang=ALL-UNNAMED \
        --java-options --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
        --java-options -Djava.awt.headless=false \
        --java-options -Djdk.util.zip.disableZip64ExtraFieldValidation=true \
        --java-options -Djifa.role=standalone-worker \
        --java-options -Djifa.open-browser-when-ready=true \
        --about-url https://eclipse-jifa.github.io/jifa/ \
        --icon "${iconPath}" \
        --verbose \

echo "Completed!"