@echo off
setlocal enabledelayedexpansion

REM ======================================================================
REM AWS Services Check Script for File Processing Pipeline
REM Checks if required services are available in current AWS account
REM ======================================================================

echo.
echo ========================================
echo AWS Services Availability Check
echo ========================================
echo.

set "ALL_PASSED=1"

REM Check if AWS CLI is installed
echo [1/8] Checking AWS CLI installation...
aws --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] AWS CLI not found. Install from: https://aws.amazon.com/cli/
    goto :error
) else (
    aws --version
    echo [OK] AWS CLI installed
)
echo.

REM Get current AWS identity
echo [2/8] Checking AWS identity...
aws sts get-caller-identity --query "Account" --output text >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Not authenticated or no credentials. Run: aws configure
    goto :error
) else (
    echo Account:    %aws sts get-caller-identity --query "Account" --output text%
    echo User/Role:  %aws sts get-caller-identity --query "Arn" --output text%
    echo Region:     %aws configure get region%
    echo [OK] Authenticated
)
echo.

REM Check DynamoDB
echo [3/8] Checking DynamoDB access...
aws dynamodb list-tables --max-items 1 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] DynamoDB access denied or not available
    set "ALL_PASSED=0"
) else (
    echo [OK] DynamoDB available
)
echo.

REM Check S3
echo [4/8] Checking S3 access...
aws s3 ls >nul 2>&1
if errorlevel 1 (
    echo [ERROR] S3 access denied or not available
    set "ALL_PASSED=0"
) else (
    echo [OK] S3 available
)
echo.

REM Check SQS
echo [5/8] Checking SQS access...
aws sqs list-queues --max-items 1 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] SQS access denied or not available
    set "ALL_PASSED=0"
) else (
    echo [OK] SQS available
)
echo.

REM Check Lambda
echo [6/8] Checking Lambda access...
aws lambda list-functions --max-items 1 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Lambda access denied or not available
    set "ALL_PASSED=0"
) else (
    echo [OK] Lambda available
)
echo.

REM Check API Gateway
echo [7/8] Checking API Gateway access...
aws apigateway get-rest-apis --limit 1 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] API Gateway access denied or not available
    set "ALL_PASSED=0"
) else (
    echo [OK] API Gateway available
)
echo.

REM Check Textract (may NOT be available in all sandbox accounts)
echo [8/8] Checking AWS Textract access...
aws textract detect-document-text --document '{"S3Object": {"Bucket": "dummy-bucket", "Name": "dummy"}}}' >nul 2>&1
if errorlevel 1 (
    echo [WARNING] Textract test failed. This may be due to:
    echo   - Service not enabled in this region
    echo   - Sandbox account restrictions
    echo   - Or actual access denied
    echo.
    echo To enable Textract: https://console.aws.amazon.com/textract/
    set "ALL_PASSED=0"
) else (
    echo [OK] Textract available
)
echo.

REM Check Comprehend (may NOT be available in all sandbox accounts)
echo [BONUS] Checking Amazon Comprehend access...
aws comprehend detect-dominant-language --text "test" >nul 2>&1
if errorlevel 1 (
    echo [WARNING] Comprehend test failed. This may be due to:
    echo   - Service not enabled in this region
    echo   - Sandbox account restrictions
    echo   - Or actual access denied
    echo.
    echo To enable Comprehend: https://console.aws.amazon.com/comprehend/
    set "ALL_PASSED=0"
) else (
    echo [OK] Comprehend available
)
echo.

echo ========================================
echo Summary
echo ========================================

if "%ALL_PASSED%"=="1" (
    echo [SUCCESS] All required services are available!
    echo.
    echo You can proceed with deployment:
    echo   cd infrastructure
    echo   sam build --template-file template.yaml
    echo   sam deploy --guided
) else (
    echo [WARNING] Some services may be unavailable or restricted.
    echo.
    echo Common issues with Sandbox accounts:
    echo   - Textract and Comprehend may require explicit activation
    echo   - Some regions may not support all services
    echo   - Service quotas may limit resources
    echo.
    echo Try enabling services in AWS Console before deployment.
)

echo.
pause
exit /b

:error
echo.
echo [FAILED] Cannot proceed without fixing errors above.
pause
exit /b 1
