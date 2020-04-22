package com.ap;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class service extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Toast.makeText(this, "Service started by user.", Toast.LENGTH_LONG).show();

        final Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        PowerManager mgr = (PowerManager)getBaseContext().getSystemService(Context.POWER_SERVICE);
        @SuppressLint("InvalidWakeLockTag") PowerManager.WakeLock wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        wakeLock.acquire();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    String url = "https://api.jsonbin.io/b/5e9a2e4b435f5604bb439aa9/latest";
                    String status = sendGET(url);
                    isStatusWinNow(status);
                    if (status.toLowerCase().contains("up")) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(500,
                                    VibrationEffect.DEFAULT_AMPLITUDE));

                            Intent intent1 = new Intent();
                            intent1.setAction("com.example.andy.myapplication");
                            intent1.putExtra("DATAPASSED", status);
                            sendBroadcast(intent1);
                        } else {
                            //deprecated in API 26
                            v.vibrate(500);
                            Intent intent1 = new Intent();
                            intent1.setAction("com.example.andy.myapplication");
                            intent1.putExtra("DATAPASSED", status);
                            sendBroadcast(intent1);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


        }, 0, 10000);

        return START_STICKY;
    }
    private boolean isStatusWinNow(String status) {
        try {
            JSONObject json = new JSONObject(status);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()){
                String key = keys.next();
                String dateTIme = key.substring(key.indexOf("[[") + 2, key.indexOf("]]"));
                System.out.println(dateTIme);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service destroyed by user.", Toast.LENGTH_LONG).show();
    }



    private static String sendGET(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        System.out.println("GET Response Code :: " + responseCode);
        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // print result
            return response.toString();
        } else {
            return responseCode + "";
        }
    }
}