package com.ap;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HelloBeamApp {

    public static void main(String[] args) {
        System.out.println("my first apache beam example");
        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline p = Pipeline.create(options);

        String inputFilePath = "D:\\mygit\\JavaRelated\\JavaTempProjects\\hello-apache-beam\\kinglear.txt";
        String outputFilePath = "D:\\mygit\\JavaRelated\\JavaTempProjects\\hello-apache-beam\\out.txt";
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


        wordCount.apply(MapElements.into(TypeDescriptors.strings())
                .via(count -> count.getKey() + " --> " + count.getValue()))
                .apply(TextIO.write().to(outputFilePath));

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
                .replace("!", "");
    }
}
