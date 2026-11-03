param([string]$JdkHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $JdkHome) { $compiler=Get-Command javac -ErrorAction SilentlyContinue; if ($compiler) { $JdkHome=Split-Path (Split-Path $compiler.Source) } }
if (-not $JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome with JDK 17 or newer.' }
$javac=Join-Path $JdkHome 'bin\javac.exe'
$java=Join-Path $JdkHome 'bin\java.exe'
$jar=Join-Path $JdkHome 'bin\jar.exe'
$javadoc=Join-Path $JdkHome 'bin\javadoc.exe'
New-Item -ItemType Directory -Force build\classes,build\test-classes,build\test-temp,dist,api_manual | Out-Null
$sources=@(Get-ChildItem src -Filter *.java -Recurse | ForEach-Object FullName)
& $javac -encoding UTF-8 --release 17 -d build\classes @sources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
& $javac -encoding UTF-8 --release 17 -cp build\classes -d build\test-classes test\library\LibraryServiceTest.java
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& $java "-Djava.io.tmpdir=$PSScriptRoot\build\test-temp" -cp 'build\classes;build\test-classes' library.LibraryServiceTest
if($LASTEXITCODE -ne 0){throw 'Tests failed'}
& $jar "-J-Djava.io.tmpdir=$PSScriptRoot\build\test-temp" --create --file dist\ssd_ip_cp4_2026f_233007_app.jar --main-class library.Main -C build\classes .
if($LASTEXITCODE -ne 0){throw 'JAR packaging failed'}
& $javadoc -quiet -Xdoclint:all,-missing -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -d api_manual -sourcepath src -subpackages library -windowtitle 'Smart Library API' -doctitle 'Smart Library Beta API'
if($LASTEXITCODE -ne 0){throw 'Javadoc failed'}
Write-Host 'Build, tests, JAR and Javadoc completed.'
