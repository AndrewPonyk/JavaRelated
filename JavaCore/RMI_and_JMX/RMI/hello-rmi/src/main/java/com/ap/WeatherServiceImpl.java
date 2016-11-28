package com.ap;

import java.rmi.RemoteException;
import java.util.Date;


public class WeatherServiceImpl implements WeatherService{

    public WeatherData getWeather(Date date, String location) throws RemoteException {
        return new WeatherData(new Date(), Constants.LOCATION_TOKYO, Constants.WEATHER_RAIN);
    }

}
