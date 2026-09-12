$ErrorActionPreference = "Stop"

mvn test
$env:PYTHONPATH = "src/main/python"
$coverageFile = "target/.coverage-python-$PID"
coverage run --data-file=$coverageFile -m pytest
coverage report --data-file=$coverageFile
