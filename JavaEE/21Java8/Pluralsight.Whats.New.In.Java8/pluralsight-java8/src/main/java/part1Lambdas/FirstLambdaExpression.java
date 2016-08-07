package part1Lambdas;

import java.io.File;
import java.io.FileFilter;

/**
 * Created by andrew on 16.09.15.
 */
public class FirstLambdaExpression {
    public static void main(String [] arg){
        System.out.println("First Lambda expression");

        FileFilter javaFilter = (File f) -> { return f.isDirectory(); };
        // or faster way
        FileFilter javaFilter2 = (File f) -> f.isDirectory();

/*        FileFilter withoutLabmda = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        };*/

        File homeDir = new File("/home/andrew/");
        File[] directories = homeDir.listFiles(javaFilter);
        for(File item : directories){
            System.out.println(item.getName());
        }
    }
}
