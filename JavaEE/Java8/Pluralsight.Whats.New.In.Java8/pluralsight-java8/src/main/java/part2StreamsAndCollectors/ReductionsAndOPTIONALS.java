package part2StreamsAndCollectors;

import com.my.java8.Person;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ReductionsAndOPTIONALS {

    public static void main(String[] args) {
        Optional<Integer> i = Optional.of(11);
        Optional<Integer> empty = Optional.empty();

        System.out.println("Get Integer from Optional = " + i.get());
        //System.out.println(empty.get()); // NoSuchElementException
        i.ifPresent(System.out::println);
        System.out.println(i.orElse(0));

        i.filter(e->e>4).ifPresent(e -> {
            System.out.println("I is greater than 4");
        });


        // ===========================================
        List<Long> longList = Arrays.asList(1L,100L,33L);

        //get 'max' from longList, we define own comparator
        longList.stream().max((o1, o2) -> {return (int)(o1%7 - o2%7); }).ifPresent(System.out::println); // result is 33 !!
        // because 1L%7=1, 100L%7=2, 33L%7 = 5 , so max=5

        // ============================================ Reductiooooooon example
        //https://docs.oracle.com/javase/tutorial/collections/streams/reduction.html
        Person p1 = new Person(23, "Andres");
        Person p2 = new Person(31, "Robert");
        Stream<Person> persons = Stream.of(p1, p2);

        Integer yearsSum = persons.map(Person::getAge).reduce(Integer::sum).orElse(0);
        System.out.println(yearsSum);

    }
}

// https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html
/*
METHODS :
* static <T> Optional<T>	empty()
Returns an empty Optional instance.

boolean	equals(Object obj)
Indicates whether some other object is "equal to" this Optional.

Optional<T>	filter(Predicate<? super T> predicate)
If a value is present, and the value matches the given predicate, return an Optional describing the value, otherwise return an empty Optional.

<U> Optional<U>	flatMap(Function<? super T,Optional<U>> mapper)
If a value is present, apply the provided Optional-bearing mapping function to it, return that result, otherwise return an empty Optional.

T	get()
If a value is present in this Optional, returns the value, otherwise throws NoSuchElementException.

int	hashCode()
Returns the hash code value of the present value, if any, or 0 (zero) if no value is present.

void	ifPresent(Consumer<? super T> consumer)
If a value is present, invoke the specified consumer with the value, otherwise do nothing.

boolean	isPresent()
Return true if there is a value present, otherwise false.

<U> Optional<U>	map(Function<? super T,? extends U> mapper)
If a value is present, apply the provided mapping function to it, and if the result is non-null, return an Optional describing the result.

static <T> Optional<T>	of(T value)
Returns an Optional with the specified present non-null value.

static <T> Optional<T>	ofNullable(T value)
Returns an Optional describing the specified value, if non-null, otherwise returns an empty Optional.

T	orElse(T other)
Return the value if present, otherwise return other.

T	orElseGet(Supplier<? extends T> other)
Return the value if present, otherwise invoke other and return the result of that invocation.
<X extends Throwable>

T	orElseThrow(Supplier<? extends X> exceptionSupplier)
Return the contained value, if present, otherwise throw an exception to be created by the provided supplier.

String	toString()
Returns a non-empty string representation of this Optional suitable for debugging.
*
* */