package com.ap;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Created by andrii on 28.11.16.
 */
public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 1099;
    private static Registry registry;

    public static void main(String[] args) throws RemoteException, NotBoundException {
        registry = LocateRegistry.getRegistry(HOST, PORT);
        WeatherService service = (WeatherService) registry.lookup(WeatherService.class.getSimpleName());

        System.out.println(service.getWeather(null,null));
    }
}
