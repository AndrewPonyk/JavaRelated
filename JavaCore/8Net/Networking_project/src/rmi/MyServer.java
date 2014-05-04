package rmi;

import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.util.Date;


public class MyServer extends java.rmi.server.UnicastRemoteObject implements
		ServerRemote {

	public MyServer() throws RemoteException {}

	public Date getDate() throws RemoteException {
		return new Date();
	}

	public Object execute(String work) throws RemoteException {
		return work + work;
	}
	
	public static void main(String[] args) {
		System.setSecurityManager( new SecurityManager() );

		try {
			ServerRemote server = new MyServer();
			Naming.rebind("NiftyServer",  server);
			
		} catch (java.io.IOException e) {
			e.printStackTrace(); // with this code EXCEPTION
			
			//System.out.println("Ioexception");
		}
	}


}
