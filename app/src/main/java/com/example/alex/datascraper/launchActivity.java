package com.example.alex.datascraper;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/*
Activity class that displays the first page of the App
 */

public class launchActivity extends AppCompatActivity {

    private static Button nextScreenButton;

    String formatter = "\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tReward: $";

    modalityHabits mHabits;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String fuckyou = String.format("%.1f",  ((MyApplication) getApplication()).getComepnsation());
        fuckyou = fuckyou + "0";
        setTitle(formatter+fuckyou);

        mHabits = new modalityHabits(this);

        setContentView(R.layout.splash);

        //display the logo during 2.5 seconds,
        new CountDownTimer(2500,1000){
            @Override
            public void onTick(long millisUntilFinished){}

            @Override
            public void onFinish(){
                //set the new Content of your activity
                launchActivity.this.setContentView(R.layout.activity_launch);

                // Create the button for moving on to the next screen
                nextScreenButton = (Button) findViewById(R.id.nextSocial);
                nextScreenButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(launchActivity.this, recordActivity.class));

                    }
                });


            }
        }.start();



    }

    public void onResume() {
        super.onResume();
        String fuckyou = String.format("%.1f",  ((MyApplication) getApplication()).getComepnsation());
        fuckyou = fuckyou + "0";
        setTitle(formatter+fuckyou);

        mHabits.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if(mHabits != null){
            mHabits.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
