package com.ap.OutputDiffrentFiles;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HelloBeamApp_WordCountSelfExplainedExample_WRITE_TO_DIFFERENT_FILES {

    public static void main(String[] args) {
        System.out.println("my first apache beam example");
        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline p = Pipeline.create(options);

        String inputFilePath = "D:\\mygit\\JavaRelated\\JavaTempProjects\\hello-apache-beam\\kinglear.txt";
        String outputFilePath = "D:\\mygit\\JavaRelated\\JavaTempProjects\\hello-apache-beam\\DIFFERENT_FILES\\";
        String temp = "D:\\mygit\\JavaRelated\\JavaTempProjects\\hello-apache-beam\\temp";
        List<String> list = new ArrayList<String>();

        PCollection<KV<String, Long>> wordCount = p
                .apply("(1) Read all lines", TextIO.read().from(inputFilePath))
                .apply("(2) Flatmap to a list of words", FlatMapElements.into(TypeDescriptors.strings())
                        .via(line -> Arrays.asList(line.split("\\s"))))
                .apply("(3) Lowercase all", MapElements.into(TypeDescriptors.strings())
                        .via(word -> word.toLowerCase()))
                .apply("(4) Trim punctuations", MapElements.into(TypeDescriptors.strings())
                        .via(word -> trim(word)))
                .apply("(5) Filter stopwords", Filter.by(word -> !isStopWord(word)))
                .apply("(6) Count words", Count.perElement());

        //WRITE KVS to different files !!!!!  nice
        final long currentMillisString = System.currentTimeMillis();
        wordCount.apply(
                FileIO.<String, KV<String, Long>>writeDynamic()
                        .by((SerializableFunction<KV<String, Long>, String>) input -> input.getKey()) // name of the files // Here we can group, we can even make error handling (just for error documents have SINGLE key) - all error files would go to SINGLE file
                        .via(
                                Contextful.fn(
                                        /*its cast for lambda*/(SerializableFunction<KV<String, Long>, String>) input -> input.getKey() + ":" + input.getValue()),
                                TextIO.sink())
                        .to(outputFilePath)
                        .withNaming(type ->  FileIO.Write.defaultNaming(outputFilePath, type + ".txt"))
                        .withDestinationCoder(StringUtf8Coder.of())
                        .withTempDirectory(temp)
                        .withNumShards(1));

        //TASK write each line to separate file (line - is word and its COUND in Kinglear)
        //
        //Convert to Transaction
        final PCollection<BankTransaction> transactions = wordCount.apply(ParDo.of(new DoFn<KV<String, Long>, BankTransaction>() {
            @ProcessElement
            public void processElement(@Element KV<String, Long> element, ProcessContext c) {
                final BankTransaction bankTransaction = new BankTransaction();
                bankTransaction.setTypeName(element.getKey());
                bankTransaction.setData(element.getKey() + ":" + element.getValue());

                c.output(bankTransaction);
            }
        }));

//        transactions.apply(
//                FileIO.<String, BankTransaction>writeDynamic()
//                        .by((SerializableFunction<BankTransaction, String>) input -> input.getTypeName())
//                        .via(
//                                Contextful.fn(
//                                        /*its cast for lambda*/(SerializableFunction<BankTransaction, String>) input -> input.getData()),
//                                TextIO.sink())
//                        .to(outputFilePath)
//
//                        .withNaming(type ->  FileIO.Write.defaultNaming(outputFilePath, type + ".txt"))
//                        .withDestinationCoder(StringUtf8Coder.of())
//                        .withTempDirectory(temp)
//                        .withNumShards(0));


        p.run().waitUntilFinish();
    }

    public static boolean isStopWord(String word) {
        String[] stopwords = {"am", "are", "is", "i", "you", "me",
                "he", "she", "they", "them", "was",
                "were", "from", "in", "of", "to", "be",
                "him", "her", "us", "and", "or"};
        for (String stopword : stopwords) {
            if (stopword.compareTo(word) == 0) {
                return true;
            }
        }
        return false;
    }

    public static String trim(String word) {
        return word.replace("(", "")
                .replace(")", "")
                .replace(",", "")
                .replace(".", "")
                .replace("\"", "")
                .replace("'", "")
                .replace(":", "")
                .replace(";", "")
                .replace("-", "")
                .replace("?", "")
                .replace("!", "")
                .replace(">", "")
                .replace("<", "");
    }
}
