package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.math.BigDecimal;
import remote.MyRemote;

public class UseMyRemote {
    public static void main(String args[]) {
        try {
            String name = "MyRemote";
            Registry registry = LocateRegistry.getRegistry(args[0]);
            MyRemote myRemote = (MyRemote) registry.lookup(name);
           
            System.out.println(myRemote.getWorld());
        } catch (Exception e) {
            System.err.println("ComputePi exception:");
            e.printStackTrace();
        }
    }    
}
