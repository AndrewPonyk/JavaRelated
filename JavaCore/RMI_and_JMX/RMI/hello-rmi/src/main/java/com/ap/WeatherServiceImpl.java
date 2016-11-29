package com.ap;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;


public class WeatherServiceImpl extends UnicastRemoteObject implements WeatherService{

    protected WeatherServiceImpl() throws RemoteException {
    }

    public WeatherData getWeather(Date date, String location) throws RemoteException {
        return new WeatherData(new Date(), Constants.LOCATION_TOKYO, Constants.WEATHER_RAIN);
    }

}
