# -------------------------------------------------------
# run.ps1  —  compile and query PAROT
# Usage:
#   .\run.ps1                                      # runs the default test query
#   .\run.ps1 "What is the tensile strength of flax?"
# -------------------------------------------------------

param(
    [string]$Query = "What is the elongation of hemp fiber?"
)

# --- paths (edit these if you move the project) --------
$JAVA     = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"
$BASE     = "C:\Users\garo\OneDrive - Det Kongelige Akademi\AP\02_Research\4_SemanticModel\Parrot\PAROT-master"
$LIB      = "$BASE\lib\*"
$OUT      = "$BASE\out"
# -------------------------------------------------------

Write-Host ""
Write-Host "=== PAROT — compile ===" -ForegroundColor Cyan
Set-Location $BASE
.\compile.bat

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed. Aborting." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== PAROT — query ===" -ForegroundColor Cyan
Write-Host "Input: $Query" -ForegroundColor Yellow
Write-Host ""

& $JAVA -cp "$LIB;$OUT" qa.GAnswer $Query
