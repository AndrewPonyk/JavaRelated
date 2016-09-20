package book808questions.chap6Exceptions;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class SubclassCantChangeThrowsExceptions {
    public static void main(String[] args) {

    }
}



// --------------------------------------------------- Compile OK
class A implements Openable{

    @Override
    public void Open() throws IOException{

    }
}
interface Openable {
    public void Open() throws IOException;
}

// --------------------------------------------------- Compile OK
class B implements Openable{

    @Override
    public void Open() throws FileNotFoundException{

    }
}
interface BOpenable {
    public void Open() throws IOException;
}

// ----------------------------------------------------- DO NOT COMPILE !
/*class C implements COpenable{

    @Override
    public void Open() throws IOException{ // error overriden method do not thrown exception

    }
}
interface COpenable {
    public void Open();
}*/


//!!!!!
// P.S A subclass is allowed to declare fewer exceptions than the superclass or interface