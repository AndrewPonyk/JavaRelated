/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example;

import com.example.common.ExampleUtils;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Distribution;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An example that counts words in Shakespeare and includes Beam best practices.
 *
 * <p>This class, {@link WordCountLocalPC}, is the second in a series of four successively more detailed
 * 'word count' examples. You may first want to take a look at {@link MinimalWordCount}. After
 * you've looked at this example, then see the {@link DebuggingWordCount} pipeline, for introduction
 * of additional concepts.
 *
 * <p>For a detailed walkthrough of this example, see <a
 * href="https://beam.apache.org/get-started/wordcount-example/">
 * https://beam.apache.org/get-started/wordcount-example/ </a>
 *
 * <p>Basic concepts, also in the MinimalWordCount example: Reading text files; counting a
 * PCollection; writing to text files
 *
 * <p>New Concepts:
 *
 * <pre>
 *   1. Executing a Pipeline both locally and using the selected runner
 *   2. Using ParDo with static DoFns defined out-of-line
 *   3. Building a composite transform
 *   4. Defining your own pipeline options
 * </pre>
 *
 * <p>Concept #1: you can execute this pipeline either locally or using by selecting another runner.
 * These are now command-line options and not hard-coded as they were in the MinimalWordCount
 * example.
 *
 * <p>To change the runner, specify:
 *
 * <pre>{@code
 * --runner=YOUR_SELECTED_RUNNER
 * }</pre>
 *
 * <p>To execute this pipeline, specify a local output file (if using the {@code DirectRunner}) or
 * output prefix on a supported distributed file system.
 *
 * <pre>{@code
 * --output=[YOUR_LOCAL_FILE | YOUR_OUTPUT_PREFIX]
 * }</pre>
 *
 * <p>The input file defaults to a public data set containing the text of of King Lear, by William
 * Shakespeare. You can override it and choose your own input with {@code --inputFile}.
 */
public class WordCountLocalPC {

    /**
     * Concept #2: You can make your pipeline assembly code less verbose by defining your DoFns
     * statically out-of-line. This DoFn tokenizes lines of text into individual words; we pass it to
     * a ParDo in the pipeline.
     */
    static class ExtractWordsFn extends DoFn<String, String> {
        private final Counter emptyLines = Metrics.counter(ExtractWordsFn.class, "emptyLines");
        private final Distribution lineLenDist =
                Metrics.distribution(ExtractWordsFn.class, "lineLenDistro");

        @ProcessElement
        public void processElement(@Element String element, OutputReceiver<String> receiver) {
            lineLenDist.update(element.length());
            if (element.trim().isEmpty()) {
                emptyLines.inc();
            }

            // Split the line into words.
            String[] words = element.split(ExampleUtils.TOKENIZER_PATTERN, -1);

            // Output each word encountered into the output PCollection.
            for (String word : words) {
                if (!word.isEmpty()) {
                    receiver.output(word);
                }
            }
        }
    }

    /**
     * A SimpleFunction that converts a Word and Count into a printable string.
     */
    public static class FormatAsTextFn extends SimpleFunction<KV<String, Long>, String> {
        @Override
        public String apply(KV<String, Long> input) {
            return input.getKey() + ":->" + input.getValue();
        }
    }

    /**
     * A PTransform that converts a PCollection containing lines of text into a PCollection of
     * formatted word counts.
     *
     * <p>Concept #3: This is a custom composite transform that bundles two transforms (ParDo and
     * Count) as a reusable PTransform subclass. Using composite transforms allows for easy reuse,
     * modular testing, and an improved monitoring experience.
     */
    public static class CountWords
            extends PTransform<PCollection<String>, PCollection<KV<String, Long>>> {
        @Override
        public PCollection<KV<String, Long>> expand(PCollection<String> lines) {

            // Convert lines of text into individual words.
            PCollection<String> words = lines.apply(ParDo.of(new ExtractWordsFn()));

            // Count the number of times each word occurs.
            PCollection<KV<String, Long>> wordCounts = words.apply(Count.perElement());

            return wordCounts;
        }
    }

    public static class FilterContains implements ProcessFunction<String, Boolean> {
        private String regex;


        public FilterContains(String s) {
            this.regex = s;
        }

        @Override
        public Boolean apply(String input) throws Exception {
            return input.contains(regex);
        }
    }

    /**
     * Options supported by {@link WordCountLocalPC}.
     *
     * <p>Concept #4: Defining your own configuration options. Here, you can add your own arguments to
     * be processed by the command-line parser, and specify default values for them. You can then
     * access the options values in your pipeline code.
     *
     * <p>Inherits standard configuration options.
     */
    public interface WordCountOptions extends PipelineOptions {

        /**
         * By default, this example reads from a public dataset containing the text of King Lear. Set
         * this option to choose a different input file or glob.
         */
        @Description("Path of the file to read from")
        @Default.String("D:\\Temp\\folder_with_text_files\\1.txt")
        String getInputFile();

        void setInputFile(String value);

        /**
         * Set this required option to specify where to write the output.
         */
        @Description("Path of the file to write to")
        @Default.String("D:\\Temp\\folder_with_text_files\\")
        @Required
        String getOutput();

        void setOutput(String value);
    }

    static void runWordCount(WordCountOptions options) {
        Pipeline p = Pipeline.create(options);

        // Concepts #2 and #3: Our pipeline applies the composite CountWords transform, and passes the
        // static FormatAsTextFn() to the ParDo transform.
//    p.apply("ReadLines", TextIO.read().from(options.getInputFile()))
//        .apply(new CountWords())
//        .apply(MapElements.via(new FormatAsTextFn()))
//        .apply("WriteCounts", TextIO.write().to(options.getOutput()+"\\results").withNumShards(1));

        //CSV generation
//        p.apply("ReadLines", TextIO.read().from(options.getInputFile()))
//                .apply(new CountWords())
//                .apply(MapElements.via(new FormatAsTextFn()))
//                .apply(MapElements.into(TypeDescriptors.lists(TypeDescriptors.strings())).via(s -> Arrays.asList(s.split(":->")[0], s.split(":->")[1])))
//                .apply("WriteCSV Using beam", FileIO.<List<String>>write().withNumShards(1).
//                        via(new CSVSink(Arrays.asList("word", "amount")))
//                        .to(options.getOutput())
//                        .withPrefix("words")
//                        .withSuffix(".csv"));

        //EXCEL
        PCollection<String> linsesFromFiles = p.apply("ReadLines", TextIO.read().from(options.getInputFile()));

        //filtering lines
        //PCollection<String> filteredLines = linsesFromFiles.apply(Filter.by(new FilterContains("ira")));
        PCollection<String> filteredLines = linsesFromFiles.apply(Filter.by(e-> e.contains("ira")));


        PCollection<KV<String, Long>> wordCounts = filteredLines.apply(new CountWords());

        PCollection<String> keyValStrings = wordCounts.apply(MapElements.via(new FormatAsTextFn()));
        PCollection<List<String>> eachListContainsWordAndCount =
                keyValStrings.apply(MapElements.into(TypeDescriptors.lists(TypeDescriptors.strings())).via(s -> Arrays.asList(s.split(":->")[0], s.split(":->")[1])));
        eachListContainsWordAndCount.apply("WriteCSV Using beam", FileIO.<List<String>>write().withNumShards(1).
                        via(new ExcelSink(Arrays.asList("user", "amount")))
                        .to(options.getOutput())
                        .withPrefix("wordsExcel")
                        .withSuffix(".xls"));


        p.run().waitUntilFinish();
    }

    public static void main(String[] args) {
        WordCountOptions options =
                PipelineOptionsFactory.fromArgs(args).withValidation().as(WordCountOptions.class);

        runWordCount(options);
    }

    public static class CSVSink implements FileIO.Sink<List<String>> {
        private String header;
        private PrintWriter writer;

        public CSVSink(List<String> colNames) {
            this.header = String.join(",", colNames);
        }

        public void open(WritableByteChannel channel) throws IOException {
            writer = new PrintWriter(Channels.newOutputStream(channel));
            writer.println(header);
        }

        public void write(List<String> element) throws IOException {
            writer.println(String.join(",", element));
        }

        public void flush() throws IOException {
            writer.flush();
        }
    }

    public static class ExcelSink implements FileIO.Sink<List<String>> {
    private String header;
    private PrintWriter writer;
    private OutputStream outputStream;
    private List<String> data = new ArrayList<>();

        public ExcelSink(List<String> colNames) {
      this.header = String.join(",", colNames);
    }

    public void open(WritableByteChannel channel) throws IOException {
        outputStream = Channels.newOutputStream(channel);
    }

    public void write(List<String> element) throws IOException {
        data.addAll(element);
    }

    public void flush() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = TestGenerateExcel.generateExcelFileBytes(data);
        outputStream.write(byteArrayOutputStream.toByteArray());
        outputStream.flush();
    }
  }
}
