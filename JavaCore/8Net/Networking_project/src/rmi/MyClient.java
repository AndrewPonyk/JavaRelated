package rmi;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class MyClient {

	public MyClient(String host) {
		try {
			ServerRemote server = (ServerRemote) Naming.lookup("rmi://" + host + "/NiftyServer");

			System.out.println(server.getDate());

			System.out.println(server.execute("2"));

		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		new MyClient("localhost");
	}

}
