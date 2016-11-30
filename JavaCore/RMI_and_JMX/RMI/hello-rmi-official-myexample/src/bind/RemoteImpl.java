package bind;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import remote.MyRemote;

public class RemoteImpl implements MyRemote {

    public RemoteImpl() {
        super();
    }

    public String getWorld(){
    	return "Hello!!! tha";
    }

    public static void main(String[] args) {
        try {
            String name = "MyRemote";
            MyRemote myRemote = new RemoteImpl();
            MyRemote stub =
                (MyRemote) UnicastRemoteObject.exportObject(myRemote, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("ComputeEngine bound");
        } catch (Exception e) {
            System.err.println("ComputeEngine exception:");
            e.printStackTrace();
        }
    }
}
