package staff;

class FinalTest {

	public static void main(String[] args) throws InterruptedException {
		House h = new House();
		h.address = "Main Street";
		System.gc();
		h = null;
		// cant determine does finalize method print its text =)
		//
	}
}

class House {
	public String address;

	public void finalize() {
		System.out.println("Inside House");
		address = null;
	}
}