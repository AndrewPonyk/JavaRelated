package genericsexamples;

public class WouldThisCodeCompile1 {
	public static void main(String[] args) {
		Test<Integer> temp = new Test<>();
	}
}

class Test <T> {
void test(T method) {
    Object my = (T)method;
}
}