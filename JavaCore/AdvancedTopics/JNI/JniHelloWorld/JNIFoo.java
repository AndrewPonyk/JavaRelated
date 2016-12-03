public class JNIFoo {    
    public native void nativeFoo();    

    static {
        System.loadLibrary("foo");
    }        

    public void print () {
	    nativeFoo();
    }
    
    public static void main(String[] args) {
    (new JNIFoo()).print();
    return;
    }
}
