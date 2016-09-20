package book808questions.chap2;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class SwitchStatementExample {

    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        for(int i : list){ // here can be int !!

        }

        if(10<100) System.out.println();
        else System.out.println();
        //else System.out.println(); // COMPILE error



    }

    private int getSortOrder(String firstName, final String lastName) {
        String middleName = "Patricia";
        final String suffix = "JR";
        int id = 0;
        switch(firstName) {
            case "Test":
                return 52;
   // case middleName: // DOES NOT COMPILE
   // id = 5;
   // break;
    case suffix:
    id = 0;
    break;
  //  case lastName: // DOES NOT COMPILE,       FUCK!!! it is final , but it is method parameter
  //  id = 8;
  //  break;
  //  case 5: // DOES NOT COMPILE
  //  id = 7;
  //  break;
  //  case 'J': // DOES NOT COMPILE
  //  id = 10;
  //  break;
  //  case java.time.DayOfWeek.SUNDAY: // DOES NOT COMPILE
  //  id=15;
  //  break;
}
return 0;
}
}
