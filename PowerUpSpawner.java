package com.almasb.fxglgames.pong;

import java.util.Timer;
import java.util.TimerTask;

public class PowerUpSpawner {
    private static final long respawnTime = 1000 * 5;
    public boolean isPowerUpSpawned = false;
    public void startTimer(){
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                isPowerUpSpawned = true;
            }
        }, respawnTime);
    }
}
