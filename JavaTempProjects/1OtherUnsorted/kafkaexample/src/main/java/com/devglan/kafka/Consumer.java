package com.devglan.kafka;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Consumer {

    public static final String path = getCurrentDir();

    public static void main(String[] args) throws ParseException, URISyntaxException {
        Options options = getOptions();

        CommandLineParser parser = new GnuParser();
        CommandLine cmd = parser.parse(options, args);

        String topicName = cmd.hasOption("topic") ? cmd.getOptionValue("topic") : "some-topic";
        int partitions = cmd.hasOption("partitions") ? Integer.valueOf(cmd.getOptionValue("partitions")) : 2;
        long offset = cmd.hasOption("offset") ? Long.valueOf(cmd.getOptionValue("offset")) : 0L;

        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:9092");
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("group.id", "tp-group");

        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<String, String>(properties);
        List<String> topics = new ArrayList<String>();
        topics.add(topicName);
        kafkaConsumer.subscribe(topics);


        boolean flag = true;
        try {
            while (true) {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(10);
                if (flag) {
                    try {
                        for (int i = 0; i < partitions; i++) {
                            kafkaConsumer.seek(new TopicPartition(topicName, i), 0);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    flag = false;
                }


                for (ConsumerRecord<String, String> record : records) {
                    int messageLenght = record.value().length();
                    int printLenght = messageLenght > 100 ? 100 : messageLenght;
                    System.out.println(String.format("Topic - %s, Partition - %d, offset - %d Value: %s",
                            record.topic(), record.partition(), record.offset(), record.value()));
                    if (cmd.hasOption("get")) {
                        if (record.offset() == offset) {
                            System.out.println("write to file" + record.offset());
                            FileUtils.writeStringToFile(new File(path + "/" + record.topic() + "_" +
                                    record.partition() + "_" + record.offset() + ".txt"),
                                    record.value().substring(0, printLenght),
                                    "UTF-8");
                        }
                    }
                }

            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        } finally {
            kafkaConsumer.close();
        }
    }

    private static Options getOptions() {
        Options options = new Options();
        // add option "-a"
        options.addOption("topic", true, "Kafka topic");

        // add option "-m"
        options.addOption("partitions", true, "Kafka offset");

        // add option "-m"
        options.addOption("offset", true, "Kafka offset");

        // add option "-m"
        options.addOption("get", true, "Get file content");

        // add option "-m"
        options.addOption("text", true, "Contain text");

        return options;
    }

    public static String getCurrentDir() {
        try {
            CodeSource codeSource = Consumer.class.getProtectionDomain().getCodeSource();
            File jarFile = new File(codeSource.getLocation().toURI().getPath());
            String jarDir = jarFile.getParentFile().getPath();
            return jarDir;
        } catch (Exception e) {
            return "/tmp";
        }
    }


}
