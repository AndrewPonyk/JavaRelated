package declarationInitializationScoping;


 public class EnumInCase {
	 public static void main(String[] args) {
		
		 Flavor f = Flavor.STRAWBERRY;
		 
		// switch(f) {
		// case 0:        //// ERROR   use VANILLA
		// System.out.println("vanilla");
		 
		 
		 switch(f) {
		 case VANILLA:
		 System.out.println("vanilla");
		 case CHOCOLATE:
		 System.out.println("chocolate");
		 case STRAWBERRY:
		 System.out.println("strawberry");
		 break;
		 default:
			 System.out.println("missing flavor");
		 }
	}
 }
 
 
 enum Flavor {
	 VANILLA, CHOCOLATE, STRAWBERRY
	 }