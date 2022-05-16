package com.example.camera6;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final String myLog = "My Log";
    public static final int delayRec = 5 * 1000; // время записи видео

    private CameraService myCameras = null;
    private CameraManager mCameraManager = null;
    private TextureView mImageView = null;
    private boolean isStartUserRecording = false;
    private boolean cameraAlreadyRecording = false;
    private MediaRecorder mMediaRecorder = null;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler = null;
    StartCameraSource myStartEvent;
    private View myAutoRecord;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// убираем заголовок
        setContentView(R.layout.activity_main);

        // Запрашиваем разрешение на использования камеры и папок
        // БЕЗ ЭТОГО НЕ ЗАРАБОТАЕТ
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ||
                (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        mImageView = findViewById(R.id.textureView);   //находим экран
        Button mButtonOpenCamera = findViewById(R.id.button1);   //находим кнопку

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        myCameras = new CameraService(mCameraManager);
        View myUserRecord = findViewById(R.id.userRecord); // находим иконку отвечающую за Принудительную запись
        myAutoRecord = findViewById(R.id.record); // находим иконку отвечающую за Принудительную запись

        myStartEvent = new StartCameraSource();
        myStartEvent.setListeners(new Vector<StartCameraEventListener>());
        // слушатель нажатия на кнопку с реализацией
        mButtonOpenCamera.setOnClickListener(v -> {//одна кнопка на включение и выключение
            if (!isStartUserRecording) {
                isStartUserRecording = true;// сообщаем что камера включена
                myUserRecord.setVisibility(View.VISIBLE);// делаем значок принудительной записи на панели видимым
                if (!cameraAlreadyRecording) {
                    myStartEvent.fireWorkspaceStart();
                }
            } else if (isStartUserRecording) {
                myUserRecord.setVisibility(View.INVISIBLE);// делаем значок принудительной записи на панели видимым
                isStartUserRecording = false;// сообщаем что камера выключена
            }
        });
        mImageView.setSurfaceTextureListener(mSurfaceTextureListener); // опрос создался ли экран
        myStartEvent.addStartCameraListener(myStartListener);// Слушатель отвечающий за начало записи
    }

    //Реализация слушателя для запуска Записи
    private StartCameraEventListener myStartListener = new StartCameraEventListener() {
        public void cameraStartEvent(StartCameraEvent event) {
            if (event.getStart() == true) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myCameras.startRecording();
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myCameras.stopRecordingVideo();
                    }
                });
            }
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


    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {  }
    }

    public class CameraService {
        private final String mCameraID = "0"; // выбираем какую камеру использовать 0 - задняя, 1 - фронтальная\
        private final int screenDelay = 1500;
        private ScreenDetector mScreenDetector;
        private File mCurrentFile;
        private CameraDevice mCameraDevice = null;
        private CaptureRequest.Builder mPreviewBuilder;
        private CameraCaptureSession mSession;
        private ImageReader mImageReader;
        private Timer timerForScreen;
        Timer timerStopRec = new Timer();
        private List<Surface> surfaceList = new ArrayList<>();

        public CameraService(CameraManager cameraManager) {
            mCameraManager = cameraManager;
        }
// открытие камеры
        private CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCameraDevice = camera;
                startCameraPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
            }

            @Override
            public void onError(CameraDevice camera, int error) {
            }
        };


        private void startCameraPreviewSession() {  // вывод изображения на экран во время
            surfaceList.clear();
            timerForScreen = new Timer();
            SurfaceTexture texture = mImageView.getSurfaceTexture();
            mImageReader = ImageReader.newInstance(10, 10, ImageFormat.JPEG, 1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
            texture.setDefaultBufferSize(1920, 1080);
            Surface surface = new Surface(texture);
            try {
                surfaceList.add(0, surface);
                surfaceList.add(1, mImageReader.getSurface());
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewBuilder.addTarget(surface);
                try {
// при запуске setUpMediaRecorder = null, и выдает ошибку  выдает ошибку даже если в if сравнить с null
// чтоб не писать 2 метода для запуска программы в предпросмотре и при записи, заносив getSurface в try
                    mPreviewBuilder.addTarget(mMediaRecorder.getSurface());
                    surfaceList.add(2, mMediaRecorder.getSurface());
                } catch (Exception e) {
                }
                mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        mSession = session;
                        try {
                            mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                    }
                }, mBackgroundHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            if(!cameraAlreadyRecording) {
            timerForScreen.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(!cameraAlreadyRecording)     makePhoto();
                }
            } , screenDelay , screenDelay);
            }
        }


        // Запрос на готовность фото
        public void makePhoto() {
            try {
                // This is the CaptureRequest.Builder that we use to take a picture.
                final CaptureRequest.Builder captureBuilder =
                        mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(mImageReader.getSurface());
                CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {

                    }
                };
                mSession.capture(captureBuilder.build(), CaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        // Слушатель готовности фото и передача его на сравнение
        private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
                = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try {
                    mScreenDetector = new ScreenDetector(reader.acquireNextImage(), myStartEvent);
                    mBackgroundHandler.post(mScreenDetector);
                } catch (Exception e) {
                }
            }
        };


        public void startRecording() {
            cameraAlreadyRecording = true;
            timerForScreen.cancel();//проверить можно ли отключить, не уверен что правильно
            myAutoRecord.setVisibility(View.VISIBLE);
            setUpMediaRecorder();
            mMediaRecorder.start();
           myCameras.startCameraPreviewSession();
            timerStopRec.schedule(new TimerTask() {
                @Override
                public void run() {
                    myStartEvent.fireWorkspaceStop();
                }
            }, delayRec);
        }

        private void stopRecordingVideo() {

            myAutoRecord.setVisibility(View.INVISIBLE);
            mScreenDetector.setFirstStart();
            stopRepeatingMyRecord();
            try {
                mMediaRecorder.stop();
            }catch (Exception e){
                startRecording();
            }
            if (isStartUserRecording) {
                myStartEvent.fireWorkspaceStart();
            } else {
                cameraAlreadyRecording = false;
                myCameras.startCameraPreviewSession();
            }

        }

        private void stopRepeatingMyRecord() {
            try {
                mSession.stopRepeating();
                mSession.abortCaptures();
                mSession.close();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        //подготовка камеры к записи
        private void setUpMediaRecorder() {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mCurrentFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fileName());
            mMediaRecorder.setOutputFile(mCurrentFile.getAbsolutePath());
            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
            mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            try {
                mMediaRecorder.prepare();
                Log.i(myLog, " mMediaRecorder.prepare() successful ");
            } catch (Exception e) {
                Log.i(myLog, " mMediaRecorder.prepare() fail ");
                mCurrentFile.delete();
                setUpMediaRecorder();
            }
        }
        // генерация имени файла
        private String fileName() { // название файла в виде дата,месяц,год_час,минута,секунда
            Date dateNow = new Date();//("yyyy.MM.dd 'и время' hh:mm:ss a zzz");
            SimpleDateFormat formatForDateNow = new SimpleDateFormat("dd.MM.yyyy_hh:mm:ss");
            return ("" + formatForDateNow.format(dateNow) + ".mp4");
        }


        // Проверка разрешений на использование камеры
        @SuppressLint("NewApi")
        public void openCamera() {//проверяем, получено ли разрешение на использование камеры
            try {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    mCameraManager.openCamera(mCameraID, mCameraCallback, null);
                }
            } catch (CameraAccessException e) {
            }
        }

    }

    @Override
    public void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
    }


}