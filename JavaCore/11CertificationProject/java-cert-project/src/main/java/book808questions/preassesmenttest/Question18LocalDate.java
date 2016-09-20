package book808questions.preassesmenttest;


import java.time.LocalDate;
import java.time.Month;

/**
 * Created by 4G-PC on 17.09.2016.
 */

/*
* Which of the following print out a date representing April 1, 2015? (Choose all that apply)
A. System.out.println(LocalDate.of(2015, Calendar.APRIL, 1));
B. System.out.println(LocalDate.of(2015, Month.APRIL, 1));
C. System.out.println(LocalDate.of(2015, 3, 1));
D. System.out.println(LocalDate.of(2015, 4, 1));
E. System.out.println(new LocalDate(2015, 3, 1));
F. System.out.println(new LocalDate(2015, 4, 1));*/

public class Question18LocalDate {
    public static void main(String[] args) {
        System.out.println(LocalDate.of(2015, Month.APRIL, 1));
        System.out.println(LocalDate.of(2015, 4, 1));

    }
    public Question18LocalDate(){

    }
}




// ANswer
/*
* B, D. The new date APIs added in Java 8 use static methods rather than a constructor
to create a new date, making options E and F incorrect. The months are indexed starting
with 1 in these APIs, making options A and C incorrect. Option A uses the old
Calendar constants which are indexed from 0. Therefore, options B and D are correct.
For more information, see Chapter 3.*/
