$ErrorActionPreference = "Stop"

$env:PYTHONPATH = "src/main/python"
python -m divide_conquer.demo @args
