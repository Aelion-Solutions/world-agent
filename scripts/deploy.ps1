# Build plugin + MCP, optionally copy the jar into a Paper plugins folder.
param(
    [string]$PluginsDir = ""
)

$ErrorActionPreference = "Stop"
$Repo = Split-Path $PSScriptRoot -Parent
$Plugin = Join-Path $Repo "plugin"
$Mcp = Join-Path $Repo "mcp"
$JarName = "AelionWorldAgent-0.1.0.jar"

Push-Location $Plugin
try {
    mvn -q test package
} finally {
    Pop-Location
}

$jar = Join-Path $Plugin "target\$JarName"
if (-not (Test-Path $jar)) {
    throw "Build failed: missing $jar"
}

if ($PluginsDir) {
    if (-not (Test-Path $PluginsDir)) {
        throw "PluginsDir not found: $PluginsDir"
    }
    Copy-Item -Force $jar (Join-Path $PluginsDir $JarName)
    Write-Host "Deployed $jar -> $PluginsDir"
} else {
    Write-Host "Built $jar (pass -PluginsDir to copy)"
}

Push-Location $Mcp
try {
    if (-not (Test-Path "node_modules")) { npm install }
    npm run build
} finally {
    Pop-Location
}

Write-Host "MCP ready: $(Join-Path $Mcp 'dist\index.js')"
Write-Host 'Health: curl -H "Authorization: Bearer <token>" http://127.0.0.1:8765/v1/health'
