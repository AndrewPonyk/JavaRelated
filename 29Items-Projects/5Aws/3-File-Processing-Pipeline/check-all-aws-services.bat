@echo off
setlocal enabledelayedexpansion

REM ======================================================================
REM Comprehensive AWS Services Availability Check
REM Checks 80+ AWS services for availability in current account/region
REM ======================================================================

echo.
echo ======================================================================
echo     AWS Services Availability Check (80+ Services)
echo ======================================================================
echo.
echo Checking account and region...
echo.

set "TOTAL=0"
set "AVAILABLE=0"
set "NOT_AVAILABLE=0"

REM Get account info for display
for /f "tokens=*" %%a in ('aws sts get-caller-identity --query "Account" --output text 2^>nul') do set "ACCOUNT=%%a"
for /f "tokens=*" %%a in ('aws sts get-caller-identity --query "Arn" --output text 2^>nul') do set "ARN=%%a"
for /f "tokens=*" %%a in ('aws configure get region 2^>nul') do set "REGION=%%a"

echo Account: %ACCOUNT%
echo Region:  %REGION%
echo Identity: %ARN%
echo.
echo ======================================================================
echo.

REM Create temp file for results
set "RESULT_FILE=%TEMP%\aws_services_check_%RANDOM%.txt"
echo SERVICE ^| STATUS ^| NOTES > "%RESULT_FILE%"

REM Function-like subroutine to check service
REM Usage: call :check_service "ServiceName" "Command" "ExpectedSuccessPattern"
goto :main

:check_service
set "SVC_NAME=%~1"
set "SVC_CMD=%~2"
set /a "TOTAL+=1"

echo [%TOTAL%] Checking %SVC_NAME%...

%SVC_CMD% >nul 2>&1
if errorlevel 1 (
    echo [NOT AVAILABLE] %SVC_NAME%
    echo %SVC_NAME% ^| NOT_AVAIL ^| - >> "%RESULT_FILE%"
    set /a "NOT_AVAILABLE+=1"
) else (
    echo [OK] %SVC_NAME%
    echo %SVC_NAME% ^| OK ^| - >> "%RESULT_FILE%"
    set /a "AVAILABLE+=1"
)
exit /b

:main
REM ============================================================
REM COMPUTE SERVICES
REM ============================================================

REM 1. EC2
call :check_service "EC2" "aws ec2 describe-instances --max-items 1"

REM 2. Lambda
call :check_service "Lambda" "aws lambda list-functions --max-items 1"

REM 3. ECS
call :check_service "ECS" "aws ecs list-clusters"

REM 4. EKS
call :check_service "EKS" "aws eks list-clusters"

REM 5. Batch
call :check_service "AWS Batch" "aws batch describe-job-queues --max-items 1"

REM 6. Elastic Beanstalk
call :check_service "Elastic Beanstalk" "aws elasticbeanstalk describe-environments"

REM ============================================================
REM STORAGE & DATABASES
REM ============================================================

REM 7. S3
call :check_service "S3" "aws s3 ls"

REM 8. EFS
call :check_service "EFS" "aws efs describe-file-systems"

REM 9. DynamoDB
call :check_service "DynamoDB" "aws dynamodb list-tables --max-items 1"

REM 10. RDS
call :check_service "RDS" "aws rds describe-db-instances --max-items 1"

REM 11. Aurora (DocumentDB)
call :check_service "DocumentDB" "aws docdb describe-db-clusters"

REM 12. ElastiCache
call :check_service "ElastiCache" "aws elasticache describe-cache-clusters"

REM 13. Neptune
call :check_service "Neptune" "aws neptune describe-db-clusters"

REM 14. Timescale (Timestream)
call :check_service "Timestream" "aws timestream-write describe-databases"

REM 15. Keyspaces (Cassandra)
call :check_service "Keyspaces" "aws keyspaces list-keyspaces"

REM ============================================================
REM NETWORKING & CDN
REM ============================================================

REM 16. VPC
call :check_service "VPC" "aws ec2 describe-vpcs --max-items 1"

REM 17. Route 53
call :check_service "Route 53" "aws route53 list-hosted-zones --max-items 1"

REM 18. CloudFront
call :check_service "CloudFront" "aws cloudfront list-distributions --max-items 1"

REM 19. API Gateway
call :check_service "API Gateway (REST)" "aws apigateway get-rest-apis --limit 1"

REM 20. API Gateway v2 (HTTP/WebSocket)
call :check_service "API Gateway v2" "aws apigatewayv2 get-apis --max-items 1"

REM 21. Application Load Balancer (ALB/NLB)
call :check_service "ELB/ALB/NLB" "aws elb describe-load-balancers"

REM 22. Global Accelerator
call :check_service "Global Accelerator" "aws globalaccelerator list-accelerators"

REM 23. Direct Connect
call :check_service "Direct Connect" "aws directconnect describe-connections"

REM ============================================================
REM SECURITY & IDENTITY
REM ============================================================

REM 24. IAM
call :check_service "IAM" "aws iam list-roles --max-items 1"

REM 25. Cognito
call :check_service "Cognito IDP" "aws cognito-idp list-user-pools --max-results 1"

REM 26. Cognito Identity
call :check_service "Cognito Identity" "aws cognito-identity list-identity-pools --max-results 1"

REM 27. Secrets Manager
call :check_service "Secrets Manager" "aws secretsmanager list-secrets"

REM 28. KMS
call :check_service "KMS" "aws kms list-keys --limit 1"

REM 29. Certificate Manager
call :check_service "ACM" "aws acm list-certificates --max-items 1"

REM 30. Shield
call :check_service "Shield" "aws shield list-protections"

REM 31. WAF
call :check_service "WAF" "aws waf list-web-acls"

REM 32. Security Hub
call :check_service "Security Hub" "aws securityhub describe-hubs"

REM 33. GuardDuty
call :check_service "GuardDuty" "aws guardduty list-detectors"

REM 34. Macie
call :check_service "Macie" "aws macie2 list-members"

REM ============================================================
REM ANALYTICS & DATA LAKE
REM ============================================================

REM 35. Athena
call :check_service "Athena" "aws athena list-work-groups"

REM 36. Redshift
call :check_service "Redshift" "aws redshift describe-clusters"

REM 37. EMR
call :check_service "EMR" "aws emr list-clusters"

REM 38. Glue
call :check_service "Glue" "aws glue get-databases"

REM 39. Kinesis (Data Streams)
call :check_service "Kinesis" "aws kinesis list-streams"

REM 40. Firehose
call :check_service "Firehose" "aws firehose list-delivery-streams"

REM 41. MSK (Kafka)
call :check_service "MSK" "aws kafka list-clusters"

REM 42. QuickSight
call :check_service "QuickSight" "aws quicksight list-namespaces"

REM 43. OpenSearch
call :check_service "OpenSearch" "aws opensearch list-domain-names"

REM 44. CloudSearch
call :check_service "CloudSearch" "aws cloudsearch list-domain-names"

REM ============================================================
REM MESSAGING & NOTIFICATION
REM ============================================================

REM 45. SQS
call :check_service "SQS" "aws sqs list-queues"

REM 46. SNS
call :check_service "SNS" "aws sns list-topics"

REM 47. SES (Email)
call :check_service "SES" "aws ses list-identities"

REM 48. Pinpoint
call :check_service "Pinpoint" "aws pinpoint list-apps"

REM ============================================================
REM AI/ML & ANALYTICS
REM ============================================================

REM 49. SageMaker
call :check_service "SageMaker" "aws sagemaker list-notebook-instances --max-items 1"

REM 50. Textract
call :check_service "Textract" "aws textract get-limits"

REM 51. Comprehend
call :check_service "Comprehend" "aws comprehend list-dominant-language-detection-jobs"

REM 52. Rekognition
call :check_service "Rekognition" "aws rekognition describe-collection --collection-id fake"

REM 53. Polly (TTS)
call :check_service "Polly" "aws polly describe-voices"

REM 54. Transcribe (STT)
call :check_service "Transcribe" "aws transcribe list-transcription-jobs"

REM 55. Translate
call :check_service "Translate" "aws translate list-terminologies"

REM 56. Bedrock
call :check_service "Bedrock" "aws bedrock list-foundation-models"

REM 57. Lex (Chatbots)
call :check_service "Lex" "aws lexv2-models list-bots"

REM 58. Kendra (Search)
call :check_service "Kendra" "aws kendra list-indices"

REM 59. Personalize
call :check_service "Personalize" "aws personalize list-solution-versions"

REM 60. Forecast
call :check_service "Forecast" "aws forecast list-datasets"

REM ============================================================
REM IOT & EDGE
REM ============================================================

REM 61. IoT Core
call :check_service "IoT Core" "aws iot list-things --max-items 1"

REM 62. IoT Greengrass
call :check_service "Greengrass" "aws greengrass list-groups"

REM 63. IoT Analytics
call :check_service "IoT Analytics" "aws iotanalytics list-data-sets"

REM ============================================================
REM MANAGEMENT & GOVERNANCE
REM ============================================================

REM 64. CloudWatch
call :check_service "CloudWatch" "aws cloudwatch list-metrics"

REM 65. CloudWatch Logs
call :check_service "CloudWatch Logs" "aws logs describe-log-groups"

REM 66. X-Ray
call :check_service "X-Ray" "aws xray get-sampling-rules"

REM 67. CloudTrail
call :check_service "CloudTrail" "aws cloudwind describe-trails"

REM 68. Config
call :check_service "Config" "aws configservice describe-delivery-channels"

REM 69. Systems Manager
call :check_service "Systems Manager" "aws ssm describe-parameters --max-items 1"

REM 70. Control Tower
call :check_service "Control Tower" "aws controltower list-controls"

REM 71. Service Quotas
call :check_service "Service Quotas" "aws service-quotas list-service-quotas"

REM ============================================================
REM DEVELOPER TOOLS
REM ============================================================

REM 72. CodeCommit
call :check_service "CodeCommit" "aws codecommit list-repositories"

REM 73. CodeBuild
call :check_service "CodeBuild" "aws codebuild list-projects"

REM 74. CodeDeploy
call :check_service "CodeDeploy" "aws codedeploy list-applications"

REM 75. CodePipeline
call :check_service "CodePipeline" "aws codepipeline list-pipelines"

REM ============================================================
REM CONTAINER & SERVERLESS
REM ============================================================

REM 76. App Runner
call :check_service "App Runner" "aws apprunner list-services"

REM 77. CloudFormation / SAM
call :check_service "CloudFormation" "aws cloudformation list-stacks"

REM 78. Step Functions
call :check_service "Step Functions" "aws states list-state-machines"

REM 79. EventBridge
call :check_service "EventBridge" "aws events list-rules"

REM ============================================================
REM OTHER SERVICES
REM ============================================================

REM 80. SSM Incidents (CloudWatch Incident Response)
call :check_service "SSM Incidents" "aws ssm-incidents list-response-plans"

REM 81. Backup
call :check_service "Backup" "aws backup list-backup-vaults"

REM 82. Storage Gateway
call :check_service "Storage Gateway" "aws storage-gateway list-gateways"

echo.
echo ======================================================================
echo                       SUMMARY
echo ======================================================================
echo.
echo Total Services Checked: %TOTAL%
echo Available:             %AVAILABLE%
echo Not Available:         %NOT_AVAILABLE%
echo.

REM Calculate percentage
set /a "PERCENT=(AVAILABLE*100)/TOTAL"
echo Availability Rate:      %PERCENT%%%
echo.

REM Export results
echo Results saved to: %RESULT_FILE%

REM Create summary file
set "SUMMARY_FILE=%TEMP%\aws_services_summary_%RANDOM%.txt"
echo AWS Services Check Summary > "%SUMMARY_FILE%"
echo Account: %ACCOUNT% >> "%SUMMARY_FILE%"
echo Region: %REGION% >> "%SUMMARY_FILE%"
echo Date: %date% %time% >> "%SUMMARY_FILE%"
echo. >> "%SUMMARY_FILE%"
echo Total: %TOTAL% ^| Available: %AVAILABLE% ^| Not Available: %NOT_AVAILABLE% >> "%SUMMARY_FILE%"
echo. >> "%SUMMARY_FILE%"
echo AVAILABLE SERVICES: >> "%SUMMARY_FILE%"
findstr /C:"| OK|" "%RESULT_FILE%" >> "%SUMMARY_FILE%"
echo. >> "%SUMMARY_FILE%"
echo NOT AVAILABLE SERVICES: >> "%SUMMARY_FILE%"
findstr /C:"| NOT_AVAIL|" "%RESULT_FILE%" >> "%SUMMARY_FILE%"

echo Summary saved to: %SUMMARY_FILE%
echo.

REM Show not available services
if %NOT_AVAILABLE% GTR 0 (
    echo Services NOT available in this account/region:
    findstr /C:"| NOT_AVAIL|" "%RESULT_FILE%"
    echo.
)

REM Show available services count
echo Services available: %AVAILABLE%/%TOTAL%
echo.

pause
exit /b
