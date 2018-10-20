package com.google.sample.cloudvision;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class Splash extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.logo);

        //Fade
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.3f, .9f);

        ObjectAnimator alphaAnimator1 = ObjectAnimator.ofFloat(logo, View.TRANSLATION_X, 40, -40);



        alphaAnimator.setDuration(650);
        alphaAnimator.setRepeatMode(ValueAnimator.REVERSE);
        alphaAnimator.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator.start();
        alphaAnimator1.setDuration(1300);
        alphaAnimator1.setRepeatMode(ValueAnimator.REVERSE);
        alphaAnimator1.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator1.start();


        (new Thread() {
            @Override
            public void run() {
                try {
                    sleep(2850);
                    startActivity(new Intent(getApplicationContext(), MainActivity.class));
                    finish();
                } catch (InterruptedException e) {
                    Log.e("SplashScreen", "Splash Screen Animation", e);
                }
            }
        }).start();
    }

}
