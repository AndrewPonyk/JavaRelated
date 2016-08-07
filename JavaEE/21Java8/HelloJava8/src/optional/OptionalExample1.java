package optional;

import java.util.Optional;
import static java.lang.System.*;

public class OptionalExample1 {
    public static void main(String[] arg){
        System.out.println("OptionalExample1.main");

        String old = "Hello";
        Optional<String> opt = Optional.of("Hello");
        System.out.println(opt.get());

        /*if (old != null) {
            print(old);
        }*/
        opt.ifPresent(x -> System.out.println("I am inside Lablda x = " + x));


        /*if(opt.isPresent() && opt.get().contains("ab")) {
            print(opt.get());
        }*/
        opt.filter(x -> x.contains("el")).filter(x -> x.contains("H")).
                ifPresent(x -> System.out.println("opt containts 'el' and 'H'"));

        /*
        * if (x != null) {
            String t = x.trim();
            if (t.length() > 1) {
                print(t);
            }
          }
        */
        opt.map(String::trim).filter(x->x.length() > 1).ifPresent(out::println);

        /*
        int len = (x != null)? x.length() : -1;
         */
        int len = opt.map(String::length).orElse(-1);
        System.out.println("len = " + len);


        Optional<String> similarToOpt = opt.flatMap(OptionalExample1::getSimilar);
        similarToOpt.ifPresent(out::println);


        /*
        public char firstChar(String s) {
           if (s != null && !s.isEmpty())
             return s.charAt(0);
         else
            throw new IllegalArgumentException();
         }
         */

        Character first =  opt.filter(x -> !x.isEmpty()).map(x -> x.charAt(0)).orElseThrow(IllegalArgumentException::new);
        System.out.println("First character =" + first );

        ///////////////////////////////////////////////////////////
        // Bigger example
        /*
        return person != null &&
                person.getAddress() != null &&
                person.getAddress().getValidFrom() != null &&
                person.getAddress().getValidFrom().isBefore(now());
        */
        Optional<Person> personOpt = Optional.of(new Person());
       /* return personOpt.
                flatMap(Person::getAddress).
                flatMap(Address::getValidFrom).
                filter(x -> x.before(now())).
                isPresent();*/
    }

    public static void myPrint(String s){
        System.out.println(s);
    }

    public static Optional<String> getSimilar(String s){
        return  Optional.of(s + "-similar");
    }
}

class Person {

    private Optional<Address> address;

    public Optional<Address> getAddress() {
        return address;
    }

    //...
}

class Address {

    private Optional<String> validFrom;

    public Optional<String> getValidFrom() {
        return validFrom;
    }

    //...
}