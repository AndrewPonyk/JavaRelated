package com.example;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class AggregationExample {
    public static void main(String[] args) {
        final List<File> LINES = Arrays.asList(new File("C:\\tmp\\1.txt"),
                new File("C:\\tmp\\2.txt"),
                new File("C:\\tmp\\2.txt"),
                new File("C:\\tmp\\2.txt"));

        PipelineOptions options =
                PipelineOptionsFactory.fromArgs(args).create();

        Pipeline p = Pipeline.create(options);
        PCollection<String> map_to_full_names = p.apply(Create.of(LINES))
                .apply("Map to full names", MapElements.via(new SimpleFunction<File, String>() {
                    @Override
                    public String apply(File input) {
                        return input.getAbsolutePath() + "->>" + input.exists();
                    }
                }));
        map_to_full_names.apply(Distinct.create())
                .apply("Print each element", ParDo.of(new DoFn<String, String>() {
            @ProcessElement
            public void processElement(ProcessContext c) {
                System.out.println(c.element());
                c.output(c.element());
            }
        }))
                .apply("Map to KV", MapElements.via(new SimpleFunction<String, KV<String, String>>() {
            @Override
            public KV<String, String> apply(String input) {
                return KV.of(input, input);
            }
        })).apply("GROUP BY KEY and Count", Count.perKey())
                .apply("PRINT Grouped elements", ParDo.of(new DoFn<KV<String, Long>, Void>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        System.out.println(c.element());
                    }
                }));

        p.run().waitUntilFinish();
    }
}