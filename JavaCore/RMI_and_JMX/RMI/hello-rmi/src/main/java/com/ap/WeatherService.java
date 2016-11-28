package com.ap;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;

public interface WeatherService extends Remote{
    WeatherData getWeather(Date date, String location) throws RemoteException;
}
