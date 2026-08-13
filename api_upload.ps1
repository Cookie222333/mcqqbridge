# 使用 gh api 逐文件上传到 GitHub 仓库（走 api.github.com，网络更稳定）
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
$repo = "Cookie222333/mcqqbridge"
$base = "D:\MCQQBridge"

# 需要排除的路径
$excludePatterns = @(
    '\.git\\', '\.gradle\\', '\\build\\', '\\out\\',
    'CommonPoolTest\.java', 'GatewayTest\.java',
    'config\\mcqqbridge\.json', 'release\\mcqqbridge\.example\.json',
    '\.github\\workflows\\build\.yml'  # 暂不传 CI，避免 workflow 权限问题
)

function Is-Excluded($relPath) {
    foreach ($p in $excludePatterns) {
        if ($relPath -match $p) { return $true }
    }
    return $false
}

# 收集文件
$files = Get-ChildItem -Path $base -Recurse -File | Where-Object {
    $rel = $_.FullName.Substring($base.Length + 1)
    -not (Is-Excluded $rel)
}

Write-Output "共 $($files.Count) 个文件待上传"
$ok = 0; $fail = 0

foreach ($f in $files) {
    $rel = $f.FullName.Substring($base.Length + 1).Replace('\', '/')
    $content = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($f.FullName))
    $body = @{ message = "upload $rel"; content = $content } | ConvertTo-Json
    $result = $body | gh api -X PUT "repos/$repo/contents/$rel" --input - 2>&1
    if ($LASTEXITCODE -eq 0) {
        $ok++
        Write-Output "  OK: $rel"
    } else {
        $fail++
        Write-Output "  FAIL: $rel -> $result"
    }
}

Write-Output "===== 完成: 成功 $ok, 失败 $fail ====="
