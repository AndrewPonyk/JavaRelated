package book808questions.chap3CoreJavaAPIs;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public abstract class MethodDelcarationsModifiers {

    public void walk1() {}
    public final void walk2() {}
    public static final void walk3() {}
    public final static void walk4() {}
    //public void final walk6() {} // DOES NOT COMPILE //walk6() doesn’t compile because the optional specifi er is after the return type
     abstract public void walk7();

    public static void main(String[] args) {
        System.out.println("");
    }
}
