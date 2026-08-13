@echo off
REM =====================================================
REM  MCQQBridge 一键构建脚本
REM  需要 JDK 25。若 JDK 25 未安装在下方路径，请修改 JAVA_HOME。
REM =====================================================
setlocal

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] 未找到 JDK 25：%JAVA_HOME%
    echo 请安装 Temurin JDK 25 或修改本脚本中的 JAVA_HOME 路径。
    exit /b 1
)

echo [INFO] 使用 JDK：%JAVA_HOME%
call gradlew.bat build
if %errorlevel% neq 0 (
    echo [ERROR] 构建失败，请查看上方日志。
    exit /b 1
)

echo.
echo [DONE] 构建完成！模组位于 build/libs/ 目录。
echo       将 mcqqbridge-*.jar 与 fabric-api-*.jar 放入 mods/ 即可使用。
endlocal
