# Runs every JAlias example through the command line tool.
#
#   ./examples/run-examples.ps1
#
# Builds the jar with the Maven wrapper when it is missing, then runs each example. Every example
# verifies at runtime that its aliases resolved to the real classes and exits with a non zero status
# if that is not the case.

$ErrorActionPreference = 'Stop'

$examples = $PSScriptRoot
$root = Split-Path -Parent $examples
$jar = Join-Path $root 'target\jalias-0.1.0.jar'

# Use the JDK from JAVA_HOME when it is set, otherwise rely on "java" being on the PATH.
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }

if (-not (Test-Path $jar)) {
    Write-Host "building $jar"
    Push-Location $root
    try {
        & (Join-Path $root 'mvnw.cmd') -B -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "the build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Invoke-Example {
    param([string[]] $Arguments)

    Write-Host ''
    Write-Host "==> jalias run $($Arguments -join ' ')"
    & $java -cp $jar io.jalias.cli.JAliasCli run @Arguments
    if ($LASTEXITCODE -ne 0) { throw "example failed with exit code $LASTEXITCODE" }
}

Invoke-Example @(
    (Join-Path $examples 'date-alias\DateAliasExample.java')
)
Invoke-Example @(
    (Join-Path $examples 'same-simple-name\lib'),
    (Join-Path $examples 'same-simple-name\SameSimpleNameExample.java'),
    '-main', 'SameSimpleNameExample'
)
Invoke-Example @(
    (Join-Path $examples 'generics-arrays\GenericsArraysExample.java')
)
Invoke-Example @(
    (Join-Path $examples 'nested-static\NestedStaticExample.java')
)

Write-Host ''
Write-Host 'all examples passed'
