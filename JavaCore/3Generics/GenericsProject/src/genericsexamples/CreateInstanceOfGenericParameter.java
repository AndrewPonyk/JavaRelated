package genericsexamples;

public class CreateInstanceOfGenericParameter {
	public static void main(String[] args) throws InstantiationException, IllegalAccessException {
		
		Gen<String> str = new Gen<>();
		
		String tInstance = str.getTInstance(String.class);
		tInstance = "123";
		System.out.println(tInstance);
		
	}
}

class Gen <T>{
	
	public T getTInstance(Class cls) throws InstantiationException, IllegalAccessException{
		return (T) cls.newInstance();
	}
}
