$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$compilerLib = Join-Path $projectRoot '.tools/gradle/gradle-9.6.0/lib'
$javaExe = Join-Path $projectRoot '.tools/jdk17/bin/java.exe'
$stdlib = Join-Path $compilerLib 'kotlin-stdlib-2.3.21.jar'
$annotations = Join-Path $compilerLib 'annotations-24.0.1.jar'
$resultDir = Join-Path $projectRoot '.tools/speed-checks'
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
$resultJar = Join-Path $resultDir 'speed-checks.jar'
$sourceRoot = Join-Path $projectRoot 'app/src/main/java/com/lingsheng/slidercontrol'
$sources = @(
    (Join-Path $sourceRoot 'protocol/LinshengProtocol.kt'),
    (Join-Path $sourceRoot 'motion/MotionSettings.kt'),
    (Join-Path $sourceRoot 'motion/SliderCoordinator.kt'),
    (Join-Path $sourceRoot 'examples/ExampleModels.kt'),
    (Join-Path $PSScriptRoot 'support/BluetoothSerialSession.kt'),
    (Join-Path $PSScriptRoot 'SpeedProtocolCheck.kt')
)
& $javaExe -cp "$compilerLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$stdlib;$annotations" -d $resultJar @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $javaExe -cp "$resultJar;$stdlib" SpeedProtocolCheckKt
exit $LASTEXITCODE
