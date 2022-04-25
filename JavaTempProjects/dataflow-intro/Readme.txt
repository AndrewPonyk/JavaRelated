-P argument means that we use dataflow-runner


mvn compile exec:java     -Dexec.mainClass=com.example.WordCount
-Dexec.args="--project=questionerwebapp --gcpTempLocation=gs://questionerwebapp/tmp/ --output=gs://questionerwebapp/output --runner=DataflowRunner --jobName=dataflow-intro --region=us-central1"
-Pdataflow-runner