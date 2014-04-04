package deadlocks;

public class DeadLockExampleFriends {
	public static void main(String[] args) {

		final Friend ivan = new Friend("Ivan");
		final Friend petro = new Friend("Petro");
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				ivan.bow(petro);
			}
		}).start();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				petro.bow(ivan);
			}
		}).start();
		
	}
}

class Friend {
	private final String name;
	public Friend(String name) {
		this.name = name;
	}
	
	public String getName(){
		return this.name;
	}
	
	public synchronized void bow(Friend bower){
		System.out.format("%s: %s has bowed to me!\n",this.name,bower.getName());
		bower.bowBack(this);  // this line causes DeadLock
	}
	
	public synchronized void bowBack(Friend bower){
		System.out.format("%s: %s has bowed back to me!\n",this.name,bower.getName());
	}
}