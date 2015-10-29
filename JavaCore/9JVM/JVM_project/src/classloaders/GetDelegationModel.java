package classloaders;

import java.net.URL;
import java.net.URLClassLoader;

public class GetDelegationModel {
    public static void main(String[] args) {
        System.out.println("Get Delegation model of classloaders\n");

        URLClassLoader systemClassLoader = (URLClassLoader)ClassLoader.getSystemClassLoader();
       do {
           System.out.println("\t"+systemClassLoader);
           for(URL url : systemClassLoader.getURLs()){
               System.out.println(url.getPath());
           }
       }while ((systemClassLoader = (URLClassLoader)systemClassLoader.getParent()) != null);
    }
}