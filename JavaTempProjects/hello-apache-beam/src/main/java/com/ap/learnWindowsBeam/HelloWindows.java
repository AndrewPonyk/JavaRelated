package com.ap.learnWindowsBeam;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.values.PCollection;

public class HelloWindows {
    public static void main(String[] args) {
        System.out.println("Test apache beam windows");
        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline p = Pipeline.create(options);
        String inputFilePath = "D:\\mygit\\JavaRelated\\JavaTempProjects\\hello-apache-beam\\kinglear.txt";

        PCollection<String> kingLearLines = p.apply("Read lines from KingLear", TextIO.read().from(inputFilePath));
        //kingLearLines.apply("Print lines", e->null);

        p.run().waitUntilFinish();
    }
}
