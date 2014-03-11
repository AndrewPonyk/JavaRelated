package declarationInitializationScoping;

public enum IceCreamEnum {
	PLAIN(2),
	 SUGAR(3),
	 WAFFLE(5);
	
	private IceCreamEnum(int scoops) {
		this.scoops = scoops;
	}

	private int scoops;
	
	public int getScoops(){
		return this.scoops;
	}
	
	public static void main(String[] args) {
		System.out.println("main in enum");
		
		IceCreamEnum i = IceCreamEnum.PLAIN;
		System.out.println(i.getScoops());
	}
}
