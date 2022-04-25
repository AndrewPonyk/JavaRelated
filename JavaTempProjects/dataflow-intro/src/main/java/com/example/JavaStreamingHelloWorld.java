package com.example;

import com.example.common.WriteOneFilePerWindow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.joda.time.Duration;
import org.apache.beam.sdk.options.Validation.Required;

import java.io.IOException;

public class JavaStreamingHelloWorld {
    /*
     * Define your own configuration options. Add your own arguments to be processed
     * by the command-line parser, and specify default values for them.
     */
    public interface PubSubToGcsOptions extends PipelineOptions, StreamingOptions {
        @Description("The Cloud Pub/Sub topic to read from.")
        @Default.String("inputTopicForHelloStreaming")
        String getInputTopic();

        void setInputTopic(String value);

        @Description("Output file's window size in number of minutes.")
        @Default.Integer(1)
        Integer getWindowSize();

        void setWindowSize(Integer value);

        @Description("Path of the output file including its filename prefix.")
        @Required
        String getOutput();

        void setOutput(String value);
    }

    public static void main(String[] args) throws IOException {
        // The maximum number of shards when writing output.
        int numShards = 1;

        PubSubToGcsOptions options =
                PipelineOptionsFactory.fromArgs(args).withValidation().as(PubSubToGcsOptions.class);

        options.setStreaming(true);

        Pipeline pipeline = Pipeline.create(options);

        pipeline
                // 1) Read string messages from a Pub/Sub topic.
                .apply("Read PubSub Messages", PubsubIO.readStrings().fromTopic(options.getInputTopic()))
                // 2) Group the messages into fixed-sized minute intervals.
                .apply(Window.into(FixedWindows.of(Duration.standardMinutes(options.getWindowSize()))))
                // 3) Write one file to GCS for every window of messages.
                .apply("Write Files to GCS", new WriteOneFilePerWindow(options.getOutput(), numShards));

        // Execute the pipeline and wait until it finishes running.
        pipeline.run().waitUntilFinish();
    }
}

/*
mvn compile exec:java \
  -Dexec.mainClass=com.examples.pubsub.streaming.PubSubToGcs \
  -Dexec.cleanupDaemonThreads=false \
  -Dexec.args=" \
    --project=questionerwebapp \
    --inputTopic=projects/questionerwebapp/topics/inputTopicForHelloStreaming \
    --output=gs://questionerwebapp/samples/output \
    --runner=DataflowRunner \
    --windowSize=2"
 */

//gcloud dataflow jobs list - LIST OF ALL DATAFLOW JOBS
//gcloud dataflow jobs cancel JOB_ID - STOP DATAFLOW JOB