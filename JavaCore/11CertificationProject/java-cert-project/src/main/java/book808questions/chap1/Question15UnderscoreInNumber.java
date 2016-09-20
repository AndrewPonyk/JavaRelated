package book808questions.chap1;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class Question15UnderscoreInNumber {
    public static void main(String[] args) {
        int i1 = 1_234;
       // double d1 = 1_234_.0;
        //double d2 = 1_234._0;
        //double d3 = 1_234.0_;
        double d4 = 1_234.0;
    }
}

//Underscores are allowed as long as they are directly between two other digits.