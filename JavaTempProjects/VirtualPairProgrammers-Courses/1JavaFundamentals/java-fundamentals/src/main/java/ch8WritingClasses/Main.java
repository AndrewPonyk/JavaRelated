package ch8WritingClasses;

/**
 * Created by andrii on 22.04.17.
 */
public class Main {
    public static void main(String[] args) {
        Customer simon = new Customer("Mr", "Simon", "Pieman", "Lviv", "09823", "ivan@ukr.net", 1, GenderType.MALE);

        System.out.println(simon.getFirstName());
        System.out.println(simon.getMailingName());
        System.out.println(simon.getGender());
    }
}
