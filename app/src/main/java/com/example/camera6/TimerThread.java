package com.example.camera6;

import android.util.Log;

public class TimerThread extends Thread{
    private static StartCameraSource myTimerEvent;
    private static int delay = 500;

    public TimerThread(StartCameraSource myTimerEvent) {
        this.myTimerEvent = myTimerEvent;
    }

    @Override
    public void run() {
        while (true) {
            try {
                sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            myTimerEvent.fireWorkspaceStart();
        }
    }
}
