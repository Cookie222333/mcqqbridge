@echo off
chcp 65001 >nul
REM ============================================================
REM  MCQQBridge - 一键上传到 GitHub 脚本
REM  用法：双击运行本脚本，按提示操作
REM  注意：本脚本不会上传任何敏感配置（已由 .gitignore 保护）
REM ============================================================
setlocal enabledelayedexpansion

echo.
echo  ============================================
echo   MCQQBridge 一键上传到 GitHub
echo  ============================================
echo.

REM ---- 检查是否已有远程仓库 ----
cd /d "%~dp0"
for /f "delims=" %%i in ('git remote -v 2^>nul') do set REMOTE_EXISTS=1
if defined REMOTE_EXISTS (
    echo [提示] 已存在远程仓库配置，直接推送。
    git push -u origin master
    if errorlevel 1 (
        echo.
        echo [错误] 推送失败，请检查认证信息后重试。
        echo 可尝试：删除 .git/config 中的 remote 后重新运行。
    )
    goto :end
)

echo [1/4] 检查 GitHub CLI (gh) ...
where gh >nul 2>nul
if %errorlevel%==0 (
    echo 发现 gh，检查登录状态...
    gh auth status >nul 2>nul
    if errorlevel 1 (
        echo 需要登录，正在打开浏览器...
        gh auth login
    )
    goto :use_gh
) else (
    echo 未安装 gh，将使用 Token 方式。
    goto :use_token
)

:use_gh
echo.
echo [2/4] 使用 gh 创建仓库并推送...
set /p REPO_NAME=请输入仓库名（直接回车默认 mcqqbridge）: 
if "%REPO_NAME%"=="" set REPO_NAME=mcqqbridge
set /p VISIBILITY=仓库公开性（public 或 private，默认 public）: 
if "%VISIBILITY%"=="" set VISIBILITY=public

gh repo create %REPO_NAME% --%VISIBILITY% --source . --remote origin --push
if errorlevel 1 (
    echo.
    echo [错误] 创建仓库失败。可能原因：
    echo   - 仓库已存在（可用 gh repo create %REPO_NAME% --source . --remote origin --push 或换个名字）
    echo   - 认证未完成（运行 gh auth login 后重试）
    goto :end
)
echo.
echo [完成] 上传成功！
goto :end

:use_token
echo.
echo [2/4] 使用 Personal Access Token 方式
echo.
echo 请先在 GitHub 网页生成 Token：
echo   1. 打开 https://github.com/settings/tokens
echo   2. 点击 Generate new token (classic)
echo   3. 勾选 repo 权限
echo   4. 生成后复制 Token
echo.
set /p GITHUB_USER=请输入你的 GitHub 用户名: 
if "%GITHUB_USER%"=="" (
    echo [错误] 用户名不能为空
    goto :end
)
set /p GITHUB_TOKEN=请输入 Personal Access Token: 
if "%GITHUB_TOKEN%"=="" (
    echo [错误] Token 不能为空
    goto :end
)
set /p REPO_NAME2=请输入仓库名（直接回车默认 mcqqbridge）: 
if "%REPO_NAME2%"=="" set REPO_NAME2=mcqqbridge
set /p VISIBILITY2=仓库公开性（public 或 private，默认 public）: 
if "%VISIBILITY2%"=="" set VISIBILITY2=public

REM 根据公开性生成 JSON 里的 private 值
if /i "%VISIBILITY2%"=="private" (set PRIVATE_VAL=true) else (set PRIVATE_VAL=false)

echo.
echo [3/4] 尝试在 GitHub 创建远程仓库...
curl -s -X POST -H "Authorization: token %GITHUB_TOKEN%" -H "Accept: application/vnd.github.v3+json" ^
    "https://api.github.com/user/repos" ^
    -d "{\"name\":\"%REPO_NAME2%\",\"private\":%PRIVATE_VAL%}"
echo.
echo [4/4] 推送代码到 GitHub...
git remote remove origin 2>nul
git remote add origin "https://%GITHUB_USER%:%GITHUB_TOKEN%@github.com/%GITHUB_USER%/%REPO_NAME2%.git" 2>nul
git push -u origin master
if errorlevel 1 (
    echo.
    echo [错误] 推送失败，请检查用户名/Token 是否正确。
    echo       注意：Token 会包含在远程地址中，请勿截图/分享。
) else (
    echo.
    echo [完成] 上传成功！仓库地址：https://github.com/%GITHUB_USER%/%REPO_NAME2%
)

:end
echo.
pause
