package book808questions.chap6Exceptions;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class OrderOfExceptionsDoesMatter {
    public static void main(String[] args) {
        try {
            new FileInputStream("");
        }
        catch (FileNotFoundException e) {

        }catch (IOException e){

        } // compile, it is Ok
    // ---------------------------------------------------------

//        try {
//            new FileInputStream("");
//        }
//        catch (IOException e){
//
//        }
//        catch (FileNotFoundException e) {
//
//        } // Do NOT compile, java.io.FileNotFoundException has already been caught
    }
}
