mvn verify
$env:PYTHONPATH = "python"
New-Item -ItemType Directory -Force -Path coverage-data | Out-Null
python -m coverage run -m pytest
python -m coverage report
