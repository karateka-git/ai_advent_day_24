param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$DatasetPath = "",
    [string]$InputDir = "./docs/articles/doroshevich",
    [int]$TopK = 3,
    [string]$OutputPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if (-not $DatasetPath) {
    $DatasetPath = Join-Path $ProjectRoot "docs\control-questions.json"
}

if (-not $OutputPath) {
    $OutputPath = Join-Path $ProjectRoot "data\rag-evaluation.md"
}

function Get-CliBatPath {
    param([string]$WorkingDirectory)
    Join-Path $WorkingDirectory "build\install\ai_advent_day_22\bin\local-document-indexer.bat"
}

function Invoke-CliCommand {
    param(
        [string]$WorkingDirectory,
        [string]$BatPath,
        [string[]]$Arguments
    )

    $escapedArgs = $Arguments | ForEach-Object {
        if ($_ -match '\s|"') {
            '"' + ($_ -replace '"', '\"') + '"'
        } else {
            $_
        }
    }
    $joinedArgs = [string]::Join(" ", $escapedArgs)

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c chcp 65001>nul && `"$BatPath`" $joinedArgs"
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $psi

    try {
        $process.Start() | Out-Null
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        return [PSCustomObject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdout
            Stderr = $stderr
        }
    } finally {
        $process.Dispose()
    }
}

function Remove-LoadingIndicatorArtifacts {
    param([string]$Text)

    $cleaned = $Text -replace "`rОтвет модели\.{0,3}\s*", ""
    $cleaned = $cleaned -replace "Ответ модели\.{0,3}\s*", ""
    return $cleaned.Trim()
}

function Extract-AnswerBody {
    param([string]$Output)

    $normalized = Remove-LoadingIndicatorArtifacts -Text $Output
    $answerMarker = "Ответ:"
    $retrievalMarker = "Retrieval-сводка:"
    $answerStart = $normalized.IndexOf($answerMarker)
    if ($answerStart -lt 0) {
        return $normalized
    }

    $afterAnswer = $normalized.Substring($answerStart + $answerMarker.Length).Trim()
    $retrievalIndex = $afterAnswer.IndexOf($retrievalMarker)
    if ($retrievalIndex -ge 0) {
        return $afterAnswer.Substring(0, $retrievalIndex).Trim()
    }

    return $afterAnswer.Trim()
}

function Extract-RetrievalBody {
    param([string]$Output)

    $normalized = Remove-LoadingIndicatorArtifacts -Text $Output
    $retrievalMarker = "Retrieval-сводка:"
    $retrievalStart = $normalized.IndexOf($retrievalMarker)
    if ($retrievalStart -lt 0) {
        return ""
    }

    return $normalized.Substring($retrievalStart + $retrievalMarker.Length).Trim()
}

function New-Section {
    param(
        [int]$Id,
        [string]$Question,
        [string[]]$Expectation,
        [string[]]$ExpectedSources,
        [string]$PlainAnswer,
        [string]$RagFixedAnswer,
        [string]$RagFixedRetrieval,
        [string]$RagStructuredAnswer,
        [string]$RagStructuredRetrieval
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("## $Id. $Question")
    $lines.Add("")
    $lines.Add("Ожидание:")
    foreach ($item in $Expectation) {
        $lines.Add("- $item")
    }
    $lines.Add("Ожидаемые источники:")
    foreach ($item in $ExpectedSources) {
        $lines.Add('- `' + $item + '`')
    }
    $lines.Add("")
    $lines.Add("### Plain")
    $lines.Add($PlainAnswer)
    $lines.Add("")
    $lines.Add("### RAG Fixed")
    $lines.Add($RagFixedAnswer)
    $lines.Add("")
    $lines.Add("Retrieval:")
    $lines.Add('```text')
    $lines.Add(($RagFixedRetrieval | Out-String).Trim())
    $lines.Add('```')
    $lines.Add("")
    $lines.Add("### RAG Structured")
    $lines.Add($RagStructuredAnswer)
    $lines.Add("")
    $lines.Add("Retrieval:")
    $lines.Add('```text')
    $lines.Add(($RagStructuredRetrieval | Out-String).Trim())
    $lines.Add('```')
    $lines.Add("")
    return [string]::Join("`n", $lines)
}

Set-Location $ProjectRoot

$cliBatPath = Get-CliBatPath -WorkingDirectory $ProjectRoot
if (-not (Test-Path -LiteralPath $cliBatPath)) {
    throw "Built CLI launcher not found: $cliBatPath. Run start-manual-check or installDist first."
}

$questions = Get-Content -LiteralPath $DatasetPath -Encoding UTF8 | ConvertFrom-Json

$indexCommands = @(
    @("index", "--input", $InputDir, "--strategy", "fixed"),
    @("index", "--input", $InputDir, "--strategy", "structured")
)

foreach ($commandArgs in $indexCommands) {
    $result = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments $commandArgs
    if ($result.ExitCode -ne 0) {
        throw "Indexing failed for command: $($commandArgs -join ' ')`n$result.Stdout`n$result.Stderr"
    }
}

$sections = New-Object System.Collections.Generic.List[string]
foreach ($question in $questions) {
    $plain = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments @(
        "ask", "--query", $question.question, "--mode", "plain"
    )
    $ragFixed = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments @(
        "ask", "--query", $question.question, "--mode", "rag", "--strategy", "fixed", "--top", "$TopK"
    )
    $ragStructured = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments @(
        "ask", "--query", $question.question, "--mode", "rag", "--strategy", "structured", "--top", "$TopK"
    )

    $sections.Add(
        (New-Section `
            -Id ([int]$question.id) `
            -Question ([string]$question.question) `
            -Expectation ([string[]]$question.expectation) `
            -ExpectedSources ([string[]]$question.expectedSources) `
            -PlainAnswer (Extract-AnswerBody -Output $plain.Stdout) `
            -RagFixedAnswer (Extract-AnswerBody -Output $ragFixed.Stdout) `
            -RagFixedRetrieval (Extract-RetrievalBody -Output $ragFixed.Stdout) `
            -RagStructuredAnswer (Extract-AnswerBody -Output $ragStructured.Stdout) `
            -RagStructuredRetrieval (Extract-RetrievalBody -Output $ragStructured.Stdout))
    )
}

$header = @(
    "# Сравнение plain и RAG",
    "",
    "Auto-generated report for 10 control questions over docs/articles/doroshevich.",
    "",
    "- plain - answer from external LLM without retrieval",
    "- rag-fixed - retrieval over fixed index",
    "- rag-structured - retrieval over structured index",
    "",
    "Run parameters:",
    "- input = $InputDir",
    "- topK = $TopK",
    ""
) -join "`n"

$report = $header + "`n" + ([string]::Join("`n`n", $sections))

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

[System.IO.File]::WriteAllText($OutputPath, $report, [System.Text.Encoding]::UTF8)
[Console]::Out.WriteLine("Evaluation report written to: $OutputPath")
