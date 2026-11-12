# Mealwise local launcher (Windows / PowerShell 5.1+)
# - finds a JDK 17+ (repo tools dir -> JAVA_HOME -> PATH java)
# - seeds the database if the mealwise schema is missing
# - builds the jar when absent
# - starts the app and opens the browser
# Usage:  powershell -ExecutionPolicy Bypass -File start-local.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$server = Join-Path $root 'mealwise-server'
$jar = Join-Path $server 'target\mealwise-server-1.0.0.jar'
$tmp = Join-Path $root '.run\tmp'
$log = Join-Path $root '.run\app.log'
$initSql = Join-Path $root 'docs\init.sql'

New-Item -ItemType Directory -Force -Path (Join-Path $root '.run') | Out-Null
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "!!  $msg" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- java
$javaCandidates = @()
$bundled = Get-ChildItem -Path (Join-Path $root '..\.tools\jdk17') -Filter 'java.exe' -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -like '*\bin\java.exe' } | Select-Object -First 1
if ($bundled) { $javaCandidates += $bundled.FullName }
if ($env:JAVA_HOME) { $javaCandidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$onPath = Get-Command java -ErrorAction SilentlyContinue
if ($onPath) { $javaCandidates += $onPath.Source }

$java = $javaCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $java) { Fail 'Java not found. Install JDK 17+ and set JAVA_HOME, or unzip one into ..\.tools\jdk17.' }
Write-Step "java: $java"

# ---------------------------------------------------------------- database
Write-Step 'checking MySQL on 127.0.0.1:3306 ...'
$mysqlOk = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect('127.0.0.1', 3306)
    $mysqlOk = $client.Connected
    $client.Close()
} catch { $mysqlOk = $false }
if (-not $mysqlOk) {
    Fail 'MySQL is not listening on 127.0.0.1:3306. Start MySQL, or use docker compose instead.'
}

$mysqlExe = Get-ChildItem 'C:\Program Files\MySQL' -Filter 'mysql.exe' -Recurse -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($mysqlExe -and (Test-Path $initSql)) {
    $exists = & $mysqlExe.FullName -u root -p123456 -N -B -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='mealwise';" 2>$null
    if ("$exists".Trim() -ne '1') {
        Write-Step 'initialising database mealwise (docs\init.sql) ...'
        cmd /c "`"$($mysqlExe.FullName)`" -u root -p123456 --default-character-set=utf8mb4 < `"$initSql`""
        if ($LASTEXITCODE -ne 0) { Fail 'init.sql failed. Check the MySQL root password in start-local.ps1 / application-dev.yml.' }
    } else {
        Write-Step 'database mealwise already exists - skipping seed'
    }
} else {
    Write-Step 'mysql client or docs\init.sql not found - assuming the schema exists'
}

# ---------------------------------------------------------------- build
if (-not (Test-Path $jar)) {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn) {
        $bundledMvn = Get-ChildItem -Path (Join-Path $root '..\.tools\maven') -Filter 'mvn.cmd' -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($bundledMvn) { $mvn = @{ Source = $bundledMvn.FullName } }
    }
    if (-not $mvn) { Fail "jar not found and maven not available. Build it first: mvn -f mealwise-server\pom.xml -DskipTests package" }
    Write-Step 'building backend jar ...'
    $repo = Join-Path $root '..\.tools\m2repo'
    & $mvn.Source -B -q -f (Join-Path $server 'pom.xml') "-Dmaven.repo.local=$repo" "-Djava.io.tmpdir=$tmp" -DskipTests package
    if ($LASTEXITCODE -ne 0) { Fail 'maven build failed' }
}

# ---------------------------------------------------------------- run
Write-Step 'starting Mealwise on http://localhost:8080 ...'
Start-Process -FilePath $java -ArgumentList @(
    "-Djava.io.tmpdir=$tmp",
    '-jar', $jar,
    '--spring.profiles.active=dev'
) -RedirectStandardOutput $log -RedirectStandardError (Join-Path $root '.run\app.err') -NoNewWindow

$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Started MealwiseApplication' -SimpleMatch -Quiet)) { break }
}

if (Select-String -Path $log -Pattern 'Started MealwiseApplication' -SimpleMatch -Quiet) {
    Write-Step 'ready. demo account: demo / Demo@123'
    Start-Process 'http://localhost:8080'
} else {
    Write-Host '!!  startup not confirmed, see .run\app.log' -ForegroundColor Yellow
    if (Test-Path $log) { Get-Content $log -Tail 20 }
}
