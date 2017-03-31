package com.ap;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by andrii on 16.02.17.
 */
public class CheckUrl {

    public static void main(String[] args) {
        System.out.println("http://google.com".matches("^(http|https|ftp)://.*$"));
        System.out.println("htstp://dgoogle.com".matches("^(http|https|ftp)://.*$"));
    }


}
