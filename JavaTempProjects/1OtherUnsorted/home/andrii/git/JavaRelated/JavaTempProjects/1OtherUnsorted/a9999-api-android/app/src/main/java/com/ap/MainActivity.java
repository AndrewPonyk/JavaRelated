package com.ap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {


    private Button buttonMonitor;
    private TextView textView;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String s1 = intent.getStringExtra("DATAPASSED");
            textView.setText(s1);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.example.andy.myapplication");
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        buttonMonitor = findViewById(R.id.buttonMonitor);
        textView = findViewById(R.id.textView);

        buttonMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonMonitor.setText(buttonMonitor.getText() + "1");
                startService(new Intent(MainActivity.this,service.class));
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(broadcastReceiver);
    }


//        buttonMonitor.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View view) {
//            buttonMonitor.setText(buttonMonitor.getText() + "1");
//
//            new Timer().scheduleAtFixedRate(new TimerTask() {
//                @Override
//                public void run() {
//
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                String url = "https://api.myjson.com/bins/16oytu";
//                                String status = sendGET(url);
//                                if (status.toLowerCase().contains("up")) {
//                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                                        v.vibrate(VibrationEffect.createOneShot(700,
//                                                VibrationEffect.DEFAULT_AMPLITUDE));
//                                    } else {
//                                        //deprecated in API 26
//                                        v.vibrate(700);
//                                    }
//                                }
//                                buttonMonitor.setText(">>" + status);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    });
//                }
//            }, 0, 15000);//put here time 1000 milliseconds=1 second
//        }
//    });
}
