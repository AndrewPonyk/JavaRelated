package vogellapack;

public class ReplaceSymbolIfAfterThisAnotherSymbolApp {
    public static void main(String[] args) {

        String t = "(1-0,2-0,0-0),a";
        //replace comman after which is 'digit'
        t = t.replaceAll(",(\\d)"," $1");


        System.out.println(t);


        String str = "plan plans lander planitia";
        System.out.println(str.replaceAll("(\\w*)lan(\\w+)", "$1<--->$2"));
// => plan p<--->s <--->der p<--->itia
    }
}
