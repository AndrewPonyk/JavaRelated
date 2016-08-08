package com.ap;

import com.teamtreehouse.vending.AbstractChooser;
import com.teamtreehouse.vending.InvalidLocationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by andrii on 07.08.16.
 */
public class DemoAP {
    public static void main(String[] args) throws InvalidLocationException{
        Pattern pattern = Pattern.compile("^(?<row>[a-zA-Z]{1})(?<column>[0-9]+)(?<anotherval>[a-z]{2})$");
        Matcher matcher = pattern.matcher("B2dd");
        if (!matcher.matches()) {
            throw new InvalidLocationException("Invalid buttons");
        }
        System.out.println(matcher.group("row"));
        System.out.println(matcher.group("column"));
        System.out.println(matcher.group("anotherval"));
//        int row = inputAsRow(matcher.group("row"));
//        int column = inputAsColumn(matcher.group("column"));

    }
}
