package classloaders;


import java.io.*;
import java.util.Properties;
import java.util.Scanner;

public class GetXmlFileFromClasspath {
    public static void main(String[] args) throws IOException {
        System.out.println(System.getProperty("java.class.path"));

        InputStream systemResourceAsStream = ClassLoader.getSystemResourceAsStream("xmlinclasspath2.xml");
        Scanner scanner = new Scanner(systemResourceAsStream);
        while (scanner.hasNext()){
            System.out.println(scanner.nextLine());
        }

    }
}
