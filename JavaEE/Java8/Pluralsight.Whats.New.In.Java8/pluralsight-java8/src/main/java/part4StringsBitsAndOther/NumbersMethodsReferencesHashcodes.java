package part4StringsBitsAndOther;

public class NumbersMethodsReferencesHashcodes {
    public static void main(String[] args) {
        System.out.println("SOme point on Hashcodes");

        Integer i = 100;
        System.out.println(i.hashCode()); // hashCode of Integer is its value

        // And getting hashCode from Long
        long longVal = 1000000000000L;
        System.out.println(new Long(longVal).hashCode()); // Heavy way of getting hashCode form long
        System.out.println(Long.hashCode(longVal)); // better way of getting hashCode from long

        // P.S
        // Long uses such algorithm in hashcode
        // return (int)(value ^ (value >>> 32));
    }
}
