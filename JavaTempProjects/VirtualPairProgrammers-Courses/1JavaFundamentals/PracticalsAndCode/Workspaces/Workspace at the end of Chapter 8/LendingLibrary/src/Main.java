
public class Main {

	public static void main(String[] args) {

		Customer simon = new Customer("Mr","Simon","Pieman","1, The High Street","0000000000","simon@pieman.com",123, GenderType.MALE);
		
		System.out.println(simon.getFirstName());
		System.out.println(simon.getMailingName());
		System.out.println(simon.getGender());
		
		if (simon.getGender() == GenderType.MALE)
		{
			System.out.println("He is male");
		}
	}
	
}
