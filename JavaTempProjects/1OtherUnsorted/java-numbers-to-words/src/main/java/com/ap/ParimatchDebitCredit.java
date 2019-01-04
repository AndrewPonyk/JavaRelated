package com.ap;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParimatchDebitCredit {
    public static void main(String[] args) throws IOException {
        List<String> lines = FileUtils.readLines(new File("/home/andrii/Documents/par_mon_20102018.txt"), "UTF-8");

        Double deposit = 0.0;
        Double withdraw = 0.0;

        final String regex = "([\\d\\.]*\\suah)";
        final String withDrawregex = "(-)";

        for (int i=0;i<lines.size();i++){
            String line = lines.get(i).toLowerCase().replaceAll("грн.", "uah");
            if(line.contains("onus")
                    || line.trim().length() ==0){
                continue;
            }

            if(line.toLowerCase().contains("depo")){
                final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
                final Matcher matcher = pattern.matcher(line);

                while (matcher.find()) {
                    //System.out.println("Full match: " + matcher.group(0));
                    for (int j = 1; j <= matcher.groupCount(); j++) {
                        //System.out.println("Group " + j + ": " + matcher.group(j));
                        Double sum = Double.valueOf(matcher.group(j).replaceAll("uah", "").replaceAll(" ", ""));
                        deposit +=sum;
                    }
                }

            }
            if(line.toLowerCase().contains("withd") && lines.get(i+1).toLowerCase().contains("заявка выполнена")){
                final String regex1 = "(-[\\d\\s\\.]*)";
                final String string = line;

                final Pattern pattern = Pattern.compile(regex1, Pattern.MULTILINE);
                final Matcher matcher = pattern.matcher(string);

                while (matcher.find()) {
                    for (int j = 1; j <= matcher.groupCount(); j++) {
                        System.out.println("Group " + j + ": " + matcher.group(j));
                        Double withdrawSum = Double.valueOf(matcher.group(j).replaceAll(" ", ""));
                        withdraw += withdrawSum;
                    }
                }
            }

            //System.out.println(line);
        }
        System.out.println("Deposit: " + deposit + ", widhdraw:" + withdraw);
    }
}