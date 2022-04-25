package com.ap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

public class App {
    public static void main(String[] args) throws IOException {
        System.out.println("Create file in user home");
        String userHome = System.getProperty("user.home");
        byte data[] = ("Hello " + LocalDateTime.now().toString()).getBytes();
        FileOutputStream out = new FileOutputStream(userHome + "/the-file-name.txt");
        out.write(data);
        out.close();
    }
}
