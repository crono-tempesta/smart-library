param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if($JavaHome){$java=Join-Path $JavaHome 'bin\java.exe'}else{$java='java'}
$jar=Join-Path $PSScriptRoot 'ssd_ip_cp4_2026f_233007_app.jar'
if(-not(Test-Path -LiteralPath $jar)){$jar=Join-Path $PSScriptRoot 'dist\ssd_ip_cp4_2026f_233007_app.jar'}
& $java -jar $jar --demo --data (Join-Path $PSScriptRoot 'demo-library.xml')
