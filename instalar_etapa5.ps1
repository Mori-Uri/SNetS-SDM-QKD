# SNetS-SDM-QKD — incremental Stage 5 patch, for Windows PowerShell 5.1+.
# Preconditions: Stage 4 files and binary JAR are installed unchanged.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)] [string]$ProjectRoot,
    [switch]$VerificarApenas,
    [switch]$SomenteFontes
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$manifest = Get-Content -LiteralPath (Join-Path $packageRoot 'MANIFESTO_ETAPA5.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $ProjectRoot -PathType Container)) {
    throw "Diretorio do projeto nao encontrado: $ProjectRoot"
}
$projectPath = (Resolve-Path -LiteralPath $ProjectRoot).Path
$all = @($manifest.files)
if ($SomenteFontes) { $all = @($all | Where-Object { -not $_.binary }) }
$actions = New-Object System.Collections.ArrayList
foreach ($f in $all) {
    $relative = ([string]$f.path).Replace('/', [IO.Path]::DirectorySeparatorChar)
    if ($relative.StartsWith('..') -or [IO.Path]::IsPathRooted($relative)) {
        throw "Caminho invalido no manifesto: $relative"
    }
    $destination = Join-Path $projectPath $relative
    $source = Join-Path $packageRoot $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Arquivo ausente no pacote: $source"
    }
    $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sourceHash -ne ([string]$f.afterSha256).ToLowerInvariant()) {
        throw "Arquivo do pacote difere do manifesto: $relative"
    }
    $existed = Test-Path -LiteralPath $destination -PathType Leaf
    if ($existed) {
        $currentHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($currentHash -eq $sourceHash) {
            Write-Host "[OK] Ja atualizado: $relative"
            continue
        }
        $expectedOld = [string]$f.beforeSha256
        if ($expectedOld -eq '' -or $currentHash -ne $expectedOld.ToLowerInvariant()) {
            throw "Arquivo local diferente da Etapa 4: $relative. Nao sobrescrevendo. Backup/manual merge necessarios."
        }
    } else {
        if ([string]$f.beforeSha256 -ne '') {
            throw "Fonte/base da Etapa 4 ausente: $relative"
        }
    }
    [void]$actions.Add([pscustomobject]@{ Path=$relative; Source=$source; Dest=$destination; Existed=$existed; Hash=$sourceHash })
    Write-Host "[OK] Verificado: $relative"
}
if ($VerificarApenas) {
    Write-Host "[OK] Somente verificacao. Arquivos a instalar: $($actions.Count). Nenhum arquivo alterado."
    exit 0
}
if ($actions.Count -eq 0) {
    Write-Host '[OK] Etapa 5 ja aplicada integralmente aos arquivos selecionados.'
    exit 0
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $projectPath ("backup_etapa5_" + $stamp)
if (Test-Path -LiteralPath $backup) { throw "Pasta de backup ja existe: $backup" }
New-Item -Path $backup -ItemType Directory -Force | Out-Null
$applied = New-Object System.Collections.ArrayList
try {
    foreach ($a in $actions) {
        if ($a.Existed) {
            $save = Join-Path $backup $a.Path
            New-Item -Path (Split-Path -Parent $save) -ItemType Directory -Force | Out-Null
            Copy-Item -LiteralPath $a.Dest -Destination $save -Force
        }
    }
    foreach ($a in $actions) {
        New-Item -Path (Split-Path -Parent $a.Dest) -ItemType Directory -Force | Out-Null
        [void]$applied.Add($a)
        Copy-Item -LiteralPath $a.Source -Destination $a.Dest -Force
        $actual = (Get-FileHash -LiteralPath $a.Dest -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $a.Hash) { throw "Erro na verificacao SHA-256 pos-copia: $($a.Path)" }
        Write-Host "[OK] Instalado: $($a.Path)"
    }
    Write-Host "[OK] Etapa 5 aplicada. Backup: $backup"
} catch {
    Write-Warning "Falha na instalacao: $_. Restaurando arquivos anteriormente aplicados."
    foreach ($a in $applied) {
        if ($a.Existed) {
            $save = Join-Path $backup $a.Path
            if (Test-Path -LiteralPath $save) { Copy-Item -LiteralPath $save -Destination $a.Dest -Force }
        } else {
            if (Test-Path -LiteralPath $a.Dest) { Remove-Item -LiteralPath $a.Dest -Force }
        }
    }
    throw
}
