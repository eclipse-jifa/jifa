@echo off
REM Copyright (c) 2025 Contributors to the Eclipse Foundation
REM
REM See the NOTICE file(s) distributed with this work for additional
REM information regarding copyright ownership.
REM
REM This program and the accompanying materials are made available under the
REM terms of the Eclipse Public License 2.0 which is available at
REM http://www.eclipse.org/legal/epl-2.0
REM
REM SPDX-License-Identifier: EPL-2.0

call gradlew.bat clean build -x test

set inputPath=server\build\libs
set destinationPath=server\build\distributions

set name=Eclipse Jifa
set version=1.0.0
set iconPath=package\windows\launcher.ico

echo Packaging %name%...

REM For "jpackage", please refer to: https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html#GUID-C1027043-587D-418D-8188-EF8F44A4C06A
call jpackage --name "%name%" ^
         --vendor "Eclipse" ^
         --app-version "%version%" ^
         --description "Eclipse Jifa (abbreviated as Jifa) stands for 'Java Issues Finding Assistant'. Online Analyzer for Heap Dump, GC Log, Thread Dump and JFR File." ^
         --input "%inputPath%" ^
         --dest "%destinationPath%" ^
         --main-jar jifa.jar ^
         --java-options --add-opens=java.base/java.lang=ALL-UNNAMED ^
         --java-options --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED ^
         --java-options -Djdk.util.zip.disableZip64ExtraFieldValidation=true ^
         --java-options -Djifa.role=standalone-worker ^
         --java-options -Djifa.open-browser-when-ready=true ^
         --about-url https://eclipse-jifa.github.io/jifa/ ^
         --icon "%iconPath%" ^
         --verbose ^
         --win-menu ^
         --win-shortcut ^

if %error level% neq 0 (
    exit /b 1
)

echo Completed!