package com.example.camera6;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.util.Log;

import java.nio.ByteBuffer;

import static com.example.camera6.AllValueToChange.maxDetection;
import static com.example.camera6.AllValueToChange.myLog;

class ScreenDetector extends Thread {

    private static int detected;
    private static boolean firstStart = true;
    private static StartCameraSource myEvent;
    private static Bitmap oldScreenBitmap;
    private Bitmap newBitmap;
    private Image newImage;

    ScreenDetector(StartCameraSource myEvent) {
        this.myEvent = myEvent;
    }

    public void startNewImage(Image image) {
        this.newImage = image;
        this.start();
    }

    public void setFirstStart() {
        firstStart = true;
    }

    @Override
    public void run() {
        ByteBuffer buffer = newImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        newImage.close();
        newBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (firstStart) {
            oldScreenBitmap = newBitmap;
            firstStart = false;
        }
        double difference = getDifferencePercent();
        if (difference > maxDetection) {
            myEvent.fireWorkspaceStart();
        } else {
            oldScreenBitmap = newBitmap;
        }
    }

    private double getDifferencePercent() {
        detected = 0;
        for (int x = 0; x < newBitmap.getWidth(); x++) {
            for (int y = 0; y < newBitmap.getHeight(); y++) {
                detected += pixelDiff(newBitmap.getPixel(x, y), oldScreenBitmap.getPixel(x, y));
            }
        }
        long maxDif = 3L * 25 * newBitmap.getHeight() * newBitmap.getWidth();
        return 100.0 * detected / maxDif;
    }

    private final int pixelDiff(int rgb1, int rgb2) {
        int r1 = rgb1 >> 16 & 255;
        int g1 = rgb1 >> 8 & 255;
        int b1 = rgb1 & 255;
        int r2 = rgb2 >> 16 & 255;
        int g2 = rgb2 >> 8 & 255;
        int b2 = rgb2 & 255;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }
}