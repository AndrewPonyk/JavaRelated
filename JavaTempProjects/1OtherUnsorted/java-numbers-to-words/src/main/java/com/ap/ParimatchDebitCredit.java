package com.ap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.math3.util.Precision;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParimatchDebitCredit {
    public static void main(String[] args) throws IOException {

        List<String> lines = IOUtils.readLines(ParimatchDebitCredit.class.getClassLoader().getResourceAsStream("par_mon_04012019.txt"));


        Double deposit = 0.0;
        Double withdraw = 0.0;

        final String regex = "([\\d\\.]*\\suah)";

        //iterate through lines
        for (int i=0;i<lines.size();i++){
            String line = lines.get(i).toLowerCase().replaceAll("грн.", "uah");
            if(line.contains("onus") || line.contains("Обмен бонусных")
                    || line.trim().length() ==0){
                continue;
            }

            if(line.toLowerCase().contains("depo")){
                final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
                final Matcher matcher = pattern.matcher(line);

                while (matcher.find()) {
                    //System.out.println("Full match: " + matcher.group(0));
                    for (int j = 1; j <= matcher.groupCount(); j++) {
                        System.out.println("Deposit" + j + ": " + matcher.group(j));
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
                        System.out.println("Withdraw " + j + ": " + matcher.group(j));
                        Double withdrawSum = Double.valueOf(matcher.group(j).replaceAll(" ", ""));
                        withdraw += withdrawSum;
                    }
                }
            }

            //System.out.println(line);
        }
        System.out.println("Deposit: " + Precision.round(deposit, 2)
                + ", widhdraw:" + Precision.round(withdraw, 2)
                + "(diff: " + (-1*Precision.round(withdraw, 2) - Precision.round(deposit, 2)) + ")");
    }
}