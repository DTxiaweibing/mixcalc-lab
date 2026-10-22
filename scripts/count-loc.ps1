# 统计项目手写源码行数（排除构建产物与二进制资源）。
# 用法： powershell -ExecutionPolicy Bypass -File scripts\count-loc.ps1
#        powershell -ExecutionPolicy Bypass -File scripts\count-loc.ps1 -Root app\src\main

param(
    [string]$Root = "."
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$exts = @(".java", ".kt", ".xml", ".html", ".css", ".js", ".json", ".gradle", ".pro", ".properties", ".txt")

# 这些目录不算业务代码
$excludeDirs = @("\\build\\", "\\.gradle\\", "\\.git\\", "\\.idea\\", "\\build-tmp\\")

$res = @{}
$total = 0
$count = 0

Get-ChildItem -Recurse -File -LiteralPath $Root -ErrorAction SilentlyContinue | ForEach-Object {
    $ext = $_.Extension.ToLower()
    if ($ext -notin $exts) { return }

    $path = $_.FullName
    foreach ($ex in $excludeDirs) {
        if ($path -match $ex) { return }
    }

    $n = (Get-Content -LiteralPath $path -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
    $total += $n
    $count++
    if ($res.ContainsKey($ext)) { $res[$ext] += $n } else { $res[$ext] = $n }
}

Write-Host ("FILES: {0}  TOTAL: {1}" -f $count, $total)
Write-Host ("ROOT : {0}" -f (Resolve-Path -LiteralPath $Root).Path)
Write-Host ""
$res.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host ("{0,-12} {1}" -f $_.Key, $_.Value)
}
