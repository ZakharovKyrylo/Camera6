package com.example.camera6;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import java.util.Vector;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import static com.example.camera6.AllValueToChange.*;

public class MainActivity extends AppCompatActivity {

    private static View userRecord;
    private static View autoRecord;

    private CameraService myCameras = null;
    private CameraManager mCameraManager = null;
    private TextureView mImageView = null;
    private StartCameraSource myStartEvent;
    private Button mButtonOpenCamera;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {// = publick static void main(String args[]) //начало
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// убираем заголовок
        setContentView(R.layout.activity_main);

        checkPermission();// проверка на подтверждение пользователя
        initialization();
        myListeners();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermission(){
        // Запрашиваем разрешение на использования камеры и папок
        // БЕЗ ЭТОГО НЕ ЗАРАБОТАЕТ
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ||
                (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private void initialization(){
        mImageView = findViewById(R.id.textureView);   //находим экран
        mButtonOpenCamera = findViewById(R.id.userStartStopRecordButton);   //находим кнопку
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        userRecord = findViewById(R.id.userRecord); // находим иконку отвечающую за Принудительную запись
        autoRecord = findViewById(R.id.autoRecord); // находим иконку отвечающую за Принудительную запись
        myStartEvent = new StartCameraSource();
        myCameras = new CameraService(mCameraManager , mImageView , myStartEvent );
    }

    private void myListeners(){
        myStartEvent.setListeners(new Vector<StartCameraEventListener>());
        mButtonOpenCamera.setOnClickListener(v -> {// слушатель нажатия на кнопку с реализацией
            iconUserRecordReset();//одна кнопка на включение и выключение
            if (!isAutoRecordVisible()) {
                myStartEvent.fireWorkspaceStart();
            }
        });
        mImageView.setSurfaceTextureListener(mSurfaceTextureListener); // опрос создался ли экран
        myStartEvent.addStartCameraListener(myStartListener);// Слушатель отвечающий за начало записи
    }

    //Реализация слушателя для запуска Записи
    private StartCameraEventListener myStartListener = new StartCameraEventListener() {
        public void cameraStartEvent(StartCameraEvent event) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (event.getStart() == true) {
                        Log.i(myLog , "Start");
                        myCameras.startRecording();
                    } else {
                        Log.i(myLog , "Stop");
                        myCameras.stopRecordingVideo();
                    }
                }
            });
        }
    };

    //Слушатель, создался экран или нет, нужен для автоматического вывода изображения на экран при включении
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            myCameras.openCamera();//когда экран создался, выводим на него изображение с камеры
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private void iconUserRecordReset(){
        if(isUserRecordVisible()){
            userRecord.setVisibility(View.INVISIBLE);
        }else {
            userRecord.setVisibility(View.VISIBLE);
        }
    }

    public static boolean isUserRecordVisible(){
        if(userRecord.getVisibility() ==  View.VISIBLE){ //  использую иконки записи как флаги
            return true;
        }
        return false;
    }

    protected static void iconAutoRecordReset(){
        if(autoRecord.getVisibility() ==  View.VISIBLE){
            autoRecord.setVisibility(View.INVISIBLE);
        }else {
            autoRecord.setVisibility(View.VISIBLE);
        }
    }

    public static boolean isAutoRecordVisible(){
        if(autoRecord.getVisibility() ==  View.VISIBLE){ //  использую иконки записи как флаги
            return true;
        }
        return false;
    }

    @Override
    public void onPause() {
        myCameras.stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        myCameras.startBackgroundThread();
    }
}