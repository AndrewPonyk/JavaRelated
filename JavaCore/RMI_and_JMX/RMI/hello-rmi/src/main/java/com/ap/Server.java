package com.ap;


import java.rmi.AlreadyBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
    private static final int PORT = 1099;
    private static Registry registry;

    public static void startRegistry() throws RemoteException{
        registry = LocateRegistry.createRegistry(PORT);
    }

    public static void registerObject(String name, Remote remoteObj) throws AlreadyBoundException, RemoteException {
        registry.bind(name, remoteObj);
        System.out.println("Registered " + name + "->" + remoteObj);
    }

    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        System.out.println(WeatherService.class.getSimpleName());
        startRegistry();
        registerObject(WeatherService.class.getSimpleName(), new WeatherServiceImpl());
        System.out.println("ServiceStarted");
    }
}
