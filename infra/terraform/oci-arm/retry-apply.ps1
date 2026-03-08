param(
  [int]$DelaySeconds = 60,
  [int]$MaxAttempts = 0,
  [switch]$RunInitFirst
)

$ErrorActionPreference = "Stop"

function Test-RetryableCapacityError {
  param(
    [string]$Text
  )

  $patterns = @(
    "Out of host capacity",
    "Out of capacity",
    "Host capacity",
    "Capacity"
  )

  foreach ($pattern in $patterns) {
    if ($Text -match [regex]::Escape($pattern)) {
      return $true
    }
  }

  return $false
}

if ($RunInitFirst) {
  Write-Host "Running terraform init..." -ForegroundColor Cyan
  & terraform init -no-color
  if ($LASTEXITCODE -ne 0) {
    throw "terraform init failed."
  }
}

$attempt = 0

while ($true) {
  $attempt++
  $startedAt = Get-Date
  $stdoutFile = [System.IO.Path]::GetTempFileName()
  $stderrFile = [System.IO.Path]::GetTempFileName()

  Write-Host ""
  Write-Host ("[{0}] terraform apply attempt #{1}" -f $startedAt.ToString("yyyy-MM-dd HH:mm:ss"), $attempt) -ForegroundColor Yellow

  try {
    $process = Start-Process `
      -FilePath "terraform" `
      -ArgumentList @("apply", "-auto-approve", "-no-color") `
      -NoNewWindow `
      -Wait `
      -PassThru `
      -RedirectStandardOutput $stdoutFile `
      -RedirectStandardError $stderrFile

    $stdoutText = if (Test-Path $stdoutFile) { Get-Content $stdoutFile -Raw } else { "" }
    $stderrText = if (Test-Path $stderrFile) { Get-Content $stderrFile -Raw } else { "" }
    $outputText = ($stdoutText + [Environment]::NewLine + $stderrText).Trim()
    $exitCode = $process.ExitCode

    if ($stdoutText) {
      Write-Host $stdoutText.TrimEnd()
    }

    if ($stderrText) {
      Write-Host $stderrText.TrimEnd()
    }

    if ($exitCode -eq 0) {
      Write-Host ""
      Write-Host "terraform apply succeeded." -ForegroundColor Green
      exit 0
    }

    if (-not (Test-RetryableCapacityError -Text $outputText)) {
      Write-Host ""
      Write-Host "Non-retryable error detected. Stopping." -ForegroundColor Red
      exit $exitCode
    }

    Write-Host ""
    Write-Host ("Capacity error detected. Waiting {0} seconds before retry..." -f $DelaySeconds) -ForegroundColor Magenta
  }
  finally {
    if (Test-Path $stdoutFile) { Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $stderrFile) { Remove-Item $stderrFile -Force -ErrorAction SilentlyContinue }
  }

  if ($MaxAttempts -gt 0 -and $attempt -ge $MaxAttempts) {
    Write-Host ""
    Write-Host "Reached MaxAttempts=$MaxAttempts without success." -ForegroundColor Red
    exit 1
  }

  Start-Sleep -Seconds $DelaySeconds
}
