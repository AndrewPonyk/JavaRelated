
public class Main {

	public static void main(String[] args) {

		NameManager simonManager = new NameManager();
		NameManager joanneManager = new NameManager();
		
		simonManager.setName("Simon Pieman");
		joanneManager.setName("Joanne Smith");
		
		System.out.println(simonManager.getFirstName());
		System.out.println(joanneManager.getSurname());
		
	}
	
}
