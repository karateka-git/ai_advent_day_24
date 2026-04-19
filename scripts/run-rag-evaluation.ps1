param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$DatasetPath = "",
    [string]$InputDir = "./docs/articles/doroshevich",
    [string]$Strategy = "structured",
    [int]$TopK = 3,
    [string]$OutputPath = "",
    [string]$RawOutputPath = ""
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
    $OutputPath = Join-Path $ProjectRoot "data\rag-evaluation-summary.md"
}

if (-not $RawOutputPath) {
    $RawOutputPath = Join-Path $ProjectRoot "data\rag-evaluation-raw.md"
}

$ragModes = @(
    @{ Id = "none"; Label = "RAG None" },
    @{ Id = "threshold-filter"; Label = "RAG Threshold Filter" },
    @{ Id = "heuristic-filter"; Label = "RAG Heuristic Filter" },
    @{ Id = "heuristic-rerank"; Label = "RAG Heuristic Rerank" },
    @{ Id = "model-rerank"; Label = "RAG Model Rerank" }
)

$stopWords = @(
    "а", "без", "бы", "в", "во", "вот", "все", "всё", "вы", "где", "да", "для", "до", "его", "ее", "её",
    "если", "есть", "же", "за", "здесь", "и", "из", "или", "им", "их", "к", "как", "ко", "кто", "ли", "мне",
    "мы", "на", "над", "не", "него", "нее", "неё", "нет", "но", "о", "об", "однако", "он", "она", "они",
    "оно", "от", "по", "под", "при", "с", "со", "так", "там", "то", "тоже", "только", "у", "уже", "что",
    "чтобы", "это", "эта", "эти", "этого", "этой", "этот", "я"
)

function Get-CliBatPath {
    param([string]$WorkingDirectory)
    return (Join-Path $WorkingDirectory "build\install\ai_advent_day_23\bin\local-document-indexer.bat")
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

function Normalize-CommandOutput {
    param([string]$Text)

    $normalized = $Text -replace "`r", ""
    $normalized = [regex]::Replace(
        $normalized,
        "\u041E\u0442\u0432\u0435\u0442 \u043C\u043E\u0434\u0435\u043B\u0438\.{0,3}",
        ""
    )
    $normalized = [regex]::Replace($normalized, "^\s*$[\n]?", "", [System.Text.RegularExpressions.RegexOptions]::Multiline)
    return $normalized.Trim()
}

function Extract-AnswerBody {
    param([string]$Output)

    $normalized = Normalize-CommandOutput -Text $Output
    $match = [regex]::Match(
        $normalized,
        "(?s)\u041E\u0442\u0432\u0435\u0442:\s*(.*?)(?:\n\s*Retrieval-\u0441\u0432\u043E\u0434\u043A\u0430:|$)"
    )
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }

    return $normalized.Trim()
}

function Extract-RetrievalBody {
    param([string]$Output)

    $normalized = Normalize-CommandOutput -Text $Output
    $match = [regex]::Match(
        $normalized,
        "(?s)Retrieval-\u0441\u0432\u043E\u0434\u043A\u0430:\s*(.*)$"
    )
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }

    return ""
}

function Get-NormalizedTerms {
    param([string]$Text)

    $terms = New-Object System.Collections.Generic.HashSet[string]
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $terms
    }

    $matches = [regex]::Matches($Text.ToLowerInvariant(), "[\p{L}\p{Nd}]+")
    foreach ($match in $matches) {
        $token = $match.Value
        if ($token.Length -lt 3) {
            continue
        }
        if ($stopWords -contains $token) {
            continue
        }
        [void]$terms.Add($token)
    }

    return $terms
}

function Get-ExpectationTerms {
    param([string[]]$Expectation)

    $terms = New-Object System.Collections.Generic.HashSet[string]
    foreach ($line in $Expectation) {
        foreach ($term in (Get-NormalizedTerms -Text $line)) {
            [void]$terms.Add($term)
        }
    }
    return $terms
}

function Get-FilePathsFromRetrieval {
    param([string]$RetrievalBody)

    $paths = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($RetrievalBody -split "\n")) {
        if ($line -match "filePath = (.+)$") {
            $paths.Add($matches[1].Trim())
        }
    }
    return $paths
}

function Test-AnswerLooksLikeRefusal {
    param([string]$Answer)

    return [regex]::IsMatch(
        $Answer,
        "(?i)(\u043A\u043E\u043D\u0442\u0435\u043A\u0441\u0442 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D)|(\u0434\u0430\u043D\u043D\u044B\u0445 \u043D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E)|(\u043D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u0434\u0430\u043D\u043D\u044B\u0445)|(\u043D\u0435 \u043C\u043E\u0433\u0443 \u043E\u0442\u0432\u0435\u0442\u0438\u0442\u044C)|(\u043D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043D\u0430\u0439\u0442\u0438)"
    )
}

function Get-AnswerCoverageRatio {
    param(
        [System.Collections.Generic.HashSet[string]]$ExpectationTerms,
        [string]$Answer
    )

    if ($ExpectationTerms.Count -eq 0) {
        return 0.0
    }

    $answerTerms = Get-NormalizedTerms -Text $Answer
    $intersectionCount = 0
    foreach ($term in $ExpectationTerms) {
        if ($answerTerms.Contains($term)) {
            $intersectionCount++
        }
    }

    return ($intersectionCount / [double]$ExpectationTerms.Count)
}

function Test-HasExpectedSource {
    param(
        [System.Collections.Generic.List[string]]$RetrievalFilePaths,
        [string[]]$ExpectedSources
    )

    foreach ($expectedSource in $ExpectedSources) {
        foreach ($path in $RetrievalFilePaths) {
            if ($path -eq $expectedSource -or $path.EndsWith($expectedSource)) {
                return $true
            }
        }
    }

    return $false
}

function Evaluate-RunResult {
    param(
        [string]$ModeId,
        [string]$Answer,
        [string]$RetrievalBody,
        [string[]]$ExpectedSources,
        [System.Collections.Generic.HashSet[string]]$ExpectationTerms
    )

    $retrievalFilePaths = Get-FilePathsFromRetrieval -RetrievalBody $RetrievalBody
    $hasExpectedSource = Test-HasExpectedSource -RetrievalFilePaths $retrievalFilePaths -ExpectedSources $ExpectedSources
    $coverageRatio = Get-AnswerCoverageRatio -ExpectationTerms $ExpectationTerms -Answer $Answer
    $looksLikeRefusal = Test-AnswerLooksLikeRefusal -Answer $Answer

    $score = 0
    if ($hasExpectedSource) {
        $score += 2
    }
    if ($coverageRatio -ge 0.45) {
        $score += 2
    } elseif ($coverageRatio -ge 0.20) {
        $score += 1
    }
    if ($looksLikeRefusal) {
        $score -= 1
    }

    $verdict = if ($hasExpectedSource -and $coverageRatio -ge 0.35 -and -not $looksLikeRefusal) {
        "success"
    } elseif (($hasExpectedSource -and $coverageRatio -ge 0.15) -or ($coverageRatio -ge 0.30)) {
        "partial"
    } else {
        "fail"
    }

    $noteParts = New-Object System.Collections.Generic.List[string]
    if ($hasExpectedSource) {
        $noteParts.Add("retrieval contains the expected source")
    } else {
        $noteParts.Add("expected source missing in retrieval")
    }
    $noteParts.Add(("answer coverage {0:P0}" -f $coverageRatio))
    if ($looksLikeRefusal) {
        $noteParts.Add("answer looks like a grounded refusal")
    }

    return [PSCustomObject]@{
        ModeId = $ModeId
        Verdict = $verdict
        Score = $score
        CoverageRatio = $coverageRatio
        HasExpectedSource = $hasExpectedSource
        LooksLikeRefusal = $looksLikeRefusal
        Note = [string]::Join("; ", $noteParts)
        RetrievalFilePaths = $retrievalFilePaths
    }
}

function New-RawSection {
    param(
        [int]$Id,
        [string]$Question,
        $Expectation,
        $ExpectedSources,
        $RunResults
    )

    $lines = @()
    $lines += "## $Id. $Question"
    $lines += ""
    $lines += "Expectation:"
    foreach ($item in $Expectation) {
        $lines += "- $item"
    }
    $lines += "Expected sources:"
    foreach ($item in $ExpectedSources) {
        $lines += ('- `' + $item + '`')
    }
    $lines += ""

    foreach ($run in $RunResults) {
        $lines += "### $($run.Label)"
        $lines += "Verdict: $($run.Evaluation.Verdict) | Score: $($run.Evaluation.Score)"
        $lines += "Note: $($run.Evaluation.Note)"
        $lines += ""
        $lines += "Answer:"
        $lines += '```text'
        $lines += (($run.Answer | Out-String).Trim())
        $lines += '```'
        $lines += ""
        $lines += "Retrieval:"
        $lines += '```text'
        $lines += (($run.Retrieval | Out-String).Trim())
        $lines += '```'
        $lines += ""
    }

    return [string]::Join("`n", $lines)
}

function New-SummarySection {
    param(
        $QuestionRecord,
        $ModeResults,
        $PlainResult
    )

    $successCount = @($ModeResults | Where-Object { $_.Evaluation.Verdict -eq "success" }).Count
    $partialCount = @($ModeResults | Where-Object { $_.Evaluation.Verdict -eq "partial" }).Count
    $bestScore = (($ModeResults | ForEach-Object { $_.Evaluation.Score }) | Measure-Object -Maximum).Maximum
    $bestModes = @($ModeResults | Where-Object { $_.Evaluation.Score -eq $bestScore } | ForEach-Object { $_.Label })

    $questionConclusion = if ($successCount -gt 0) {
        "There are working RAG modes; the strongest result came from: $([string]::Join(', ', $bestModes))."
    } elseif ($partialCount -gt 0) {
        "There is no full success yet, but these modes achieved partial matches: $([string]::Join(', ', $bestModes))."
    } else {
        "No RAG mode produced a confident result; retrieval still looks like the main weak point."
    }

    $lines = @()
    $lines += "## $($QuestionRecord.id). $($QuestionRecord.question)"
    $lines += ""
    $lines += "Expectation:"
    foreach ($item in $QuestionRecord.expectation) {
        $lines += "- $item"
    }
    $lines += "Expected sources:"
    foreach ($item in $QuestionRecord.expectedSources) {
        $lines += ('- `' + $item + '`')
    }
    $lines += ""
    $lines += "| Mode | Verdict | Score | Note |"
    $lines += "|---|---|---:|---|"
    $lines += "| Plain | $($PlainResult.Evaluation.Verdict) | $($PlainResult.Evaluation.Score) | $($PlainResult.Evaluation.Note) |"
    foreach ($modeResult in $ModeResults) {
        $lines += "| $($modeResult.Label) | $($modeResult.Evaluation.Verdict) | $($modeResult.Evaluation.Score) | $($modeResult.Evaluation.Note) |"
    }
    $lines += ""
    $lines += "- RAG successes: $successCount/5"
    $lines += "- RAG partials: $partialCount/5"
    $lines += "- Best modes: $([string]::Join(', ', $bestModes))"
    $lines += "- Conclusion: $questionConclusion"
    $lines += ""

    return [string]::Join("`n", $lines)
}

Set-Location $ProjectRoot

$cliBatPath = Get-CliBatPath -WorkingDirectory $ProjectRoot
if (-not (Test-Path -LiteralPath $cliBatPath)) {
    throw "Built CLI launcher not found: $cliBatPath. Run start-manual-check.ps1 first."
}

$questions = Get-Content -LiteralPath $DatasetPath -Encoding UTF8 | ConvertFrom-Json

$indexCommand = @("index", "--input", $InputDir, "--strategy", $Strategy)
$indexResult = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments $indexCommand
if ($indexResult.ExitCode -ne 0) {
    throw "Indexing failed for command: $($indexCommand -join ' ')`n$($indexResult.Stdout)`n$($indexResult.Stderr)"
}

$summarySections = New-Object System.Collections.ArrayList
$rawSections = New-Object System.Collections.ArrayList
$modeWinCounters = @{}
foreach ($mode in $ragModes) {
    $modeWinCounters[$mode.Id] = 0
}

foreach ($question in $questions) {
    $expectationTerms = Get-ExpectationTerms -Expectation ([string[]]$question.expectation)

    $plainRun = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments @(
        "ask", "--query", [string]$question.question, "--mode", "plain"
    )
    if ($plainRun.ExitCode -ne 0) {
        throw "Plain evaluation failed for question $($question.id).`n$($plainRun.Stdout)`n$($plainRun.Stderr)"
    }

    $plainAnswer = Extract-AnswerBody -Output $plainRun.Stdout
    $plainRetrieval = Extract-RetrievalBody -Output $plainRun.Stdout
    $plainEvaluation = Evaluate-RunResult `
        -ModeId "plain" `
        -Answer $plainAnswer `
        -RetrievalBody $plainRetrieval `
        -ExpectedSources ([string[]]$question.expectedSources) `
        -ExpectationTerms $expectationTerms
    $plainResult = [PSCustomObject]@{
        Id = "plain"
        Label = "Plain"
        Answer = $plainAnswer
        Retrieval = $plainRetrieval
        Evaluation = $plainEvaluation
    }

    $modeResults = New-Object System.Collections.Generic.List[object]
    foreach ($mode in $ragModes) {
        $run = Invoke-CliCommand -WorkingDirectory $ProjectRoot -BatPath $cliBatPath -Arguments @(
            "ask",
            "--query", [string]$question.question,
            "--mode", "rag",
            "--strategy", $Strategy,
            "--top", "$TopK",
            "--post-mode", $mode.Id,
            "--show-all-candidates"
        )
        if ($run.ExitCode -ne 0) {
            throw "RAG evaluation failed for question $($question.id), mode $($mode.Id).`n$($run.Stdout)`n$($run.Stderr)"
        }

        $answer = Extract-AnswerBody -Output $run.Stdout
        $retrieval = Extract-RetrievalBody -Output $run.Stdout
        $evaluation = Evaluate-RunResult `
            -ModeId $mode.Id `
            -Answer $answer `
            -RetrievalBody $retrieval `
            -ExpectedSources ([string[]]$question.expectedSources) `
            -ExpectationTerms $expectationTerms

        $modeResults.Add([PSCustomObject]@{
            Id = $mode.Id
            Label = $mode.Label
            Answer = $answer
            Retrieval = $retrieval
            Evaluation = $evaluation
        })
    }

    $bestModeScore = (($modeResults | ForEach-Object { $_.Evaluation.Score }) | Measure-Object -Maximum).Maximum
    foreach ($bestMode in @($modeResults | Where-Object { $_.Evaluation.Score -eq $bestModeScore })) {
        $modeWinCounters[$bestMode.Id]++
    }

    $summarySection = New-SummarySection -QuestionRecord $question -ModeResults $modeResults -PlainResult $plainResult
    [void]$summarySections.Add([string]::Join("", @($summarySection)))

    $combinedResults = @($plainResult) + @($modeResults | ForEach-Object { $_ })
    $rawSection = New-RawSection `
        -Id ([int]$question.id) `
        -Question ([string]$question.question) `
        -Expectation ([string[]]$question.expectation) `
        -ExpectedSources ([string[]]$question.expectedSources) `
        -RunResults $combinedResults
    [void]$rawSections.Add([string]::Join("", @($rawSection)))
}

$summaryHeader = @(
    "# RAG mode comparison by question",
    "",
    "Auto-generated report for docs/articles/doroshevich.",
    "",
    "Run parameters:",
    "- input = $InputDir",
    "- strategy = $Strategy",
    "- topK = $TopK",
    "- post-modes = none, threshold-filter, heuristic-filter, heuristic-rerank, model-rerank",
    "",
    "Summary rubric:",
    "- success - retrieval contains the expected source and the answer covers a meaningful part of expected facts",
    "- partial - the answer has some useful overlap, but not enough for a confident success",
    "- fail - retrieval does not provide reliable support and/or the answer drifts away from expected content",
    "",
    "Mode wins:",
    ("- none: {0} wins" -f $modeWinCounters["none"]),
    ("- threshold-filter: {0} wins" -f $modeWinCounters["threshold-filter"]),
    ("- heuristic-filter: {0} wins" -f $modeWinCounters["heuristic-filter"]),
    ("- heuristic-rerank: {0} wins" -f $modeWinCounters["heuristic-rerank"]),
    ("- model-rerank: {0} wins" -f $modeWinCounters["model-rerank"])
) -join "`n"

$rawHeader = @(
    "# Raw evaluation by question",
    "",
    "Full run for each question and mode.",
    "",
    "Run parameters:",
    "- input = $InputDir",
    "- strategy = $Strategy",
    "- topK = $TopK",
    "- all RAG runs use --show-all-candidates"
) -join "`n"

$summaryReport = $summaryHeader + "`n`n" + ([string]::Join("`n`n", @($summarySections | ForEach-Object { [string]$_ })))
$rawReport = $rawHeader + "`n`n" + ([string]::Join("`n`n", @($rawSections | ForEach-Object { [string]$_ })))

foreach ($path in @($OutputPath, $RawOutputPath)) {
    $directory = Split-Path -Parent $path
    if ($directory) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
}

[System.IO.File]::WriteAllText($OutputPath, $summaryReport, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText($RawOutputPath, $rawReport, [System.Text.Encoding]::UTF8)
[Console]::Out.WriteLine("Evaluation summary written to: $OutputPath")
[Console]::Out.WriteLine("Evaluation raw report written to: $RawOutputPath")
