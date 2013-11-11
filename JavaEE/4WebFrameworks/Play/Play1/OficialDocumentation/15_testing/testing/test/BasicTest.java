import org.junit.*;
import java.util.*;
import play.test.*;

public class BasicTest extends UnitTest {

    @Test
    public void aVeryImportantThingToTest() {
        assertEquals(2, 1 + 1);
    }
    
    @Test
    public void multiplicationTest(){
    	assertEquals(40*4, 160);
    }

}
