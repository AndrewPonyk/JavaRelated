package com.ap;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class WordFinder {
    public static void main(String[] args) {
        System.out.println("FIND word");
        File file = new File("D:\\mygit\\dict_uk\\data\\freq\\uk_wordlist.xml");

        try {
            List<String> contents = FileUtils.readLines(file, "UTF-8");

            // Iterate the result to print each line of the file.
            int counter = 1;
            for (String string : contents) {
                if(!string.contains("flags")){
                    continue;
                }
                counter++;
                String word = string.substring(string.indexOf("\">")+2, string.indexOf("</"));
                slowko0607Condition(word);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public  static void slowko2806Condition(String word){
        if(word.length()==5 && word.charAt(1)== 'о' && word.charAt(3)== 'н'
        && !word.contains("т") && !word.contains("к") && !word.contains("п") && !word.contains("а")
        && !word.contains("р") && !word.contains("у") && !word.contains("с") && !word.contains("х")
                && !word.contains("г") && !word.contains("ь") && !word.contains("ф")
                && !word.contains("й") && !word.contains("б")

                && word.contains("в") && word.contains("а") && word.contains("о") && word.contains("л")
        ){
            System.out.println(word);
        }
    }

    public  static void slowko3006Condition(String word){
        if(word.length()==5
                //&& word.charAt(1)== 'о' && word.charAt(3)== 'н'
                && !word.contains("у") && !word.contains("к") && !word.contains("щ") && !word.contains("н")
                && !word.contains("і") && !word.contains("п") && !word.contains("р") && !word.contains("д")
                && !word.contains("ж") && !word.contains("я")
                && !word.contains("я") && !word.contains("с") && !word.contains("м") && !word.contains("т")){
            System.out.println(word);
        }
    }

    public  static void slowko0607Condition(String word){
        if(word.length()==5
                && notSontains(word, "кнгвролмитюшес")
        &&containsAll(word, "аід")
        && word.charAt(1)=='а' ){
            System.out.println(word);
        }
    }

    public static boolean notSontains(String word, String s){
        for (int i = 0; i < s.length(); i++) {
            if(word.contains(s.charAt(i)+"")){
                return false;
            }
        }
        return true;
    }

    public static boolean containsAll(String word, String s){
        for (int i = 0; i < s.length(); i++) {
            if(!word.contains(s.charAt(i)+"")){
                return false;
            }
        }
        return true;
    }

}
