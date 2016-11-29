package remote;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MyRemote extends Remote {
    String getWorld() throws RemoteException;
}
