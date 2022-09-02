package com.example.camera6;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContextWrapper;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import com.example.camera6.*;
import androidx.annotation.NonNull;

import static androidx.core.content.ContextCompat.checkSelfPermission;

public class CameraService {
    private final String mCameraID = "0"; // выбираем какую камеру использовать 0 - задняя, 1 - фронтальная\
    private static final String myLog = "My Log";
    private static final int delayRec = 2 * 60 * 1000; // время записи видео
    private final int screenDelay = 1000;

    private ScreenDetector mScreenDetector;
    private File mCurrentFile;
    private CameraDevice mCameraDevice = null;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mSession;
    private ImageReader mImageReader;
    private Timer timerForScreen;
    Timer timerStopRec = new Timer();
    private List<Surface> surfaceList = new ArrayList<>();
    private CameraManager mCameraManager = null;
    private TextureView mImageView = null;
    private MediaRecorder mMediaRecorder = null;
    StartCameraSource myStartEvent;
    private Handler mBackgroundHandler = null;
    private Handler mScreenHandler = null;

    public CameraService(CameraManager cameraManager , TextureView mImageView ,StartCameraSource myStartEvent) {
        mCameraManager = cameraManager;
        this.mImageView = mImageView;
        this.myStartEvent = myStartEvent;
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
        if(!MainActivity.isAutoRecordVisible()) {
            timerForScreen.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(!MainActivity.isAutoRecordVisible())  {
                        makePhoto();
                    }
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
                // mBackgroundHandler.post(mScreenDetector);
                mScreenHandler.post(mScreenDetector);
            } catch (Exception e) {
            }
        }
    };

    public void startRecording() {
        timerForScreen.cancel();//проверить можно ли отключить, не уверен что правильно
        MainActivity.iconAutoRecordReset();
        setUpMediaRecorder();
        mMediaRecorder.start();
        this.startCameraPreviewSession();
        timerStopRec.schedule(new TimerTask() {
            @Override
            public void run() {
                myStartEvent.fireWorkspaceStop();
            }
        }, delayRec);
    }

    public void stopRecordingVideo() {
        MainActivity.iconAutoRecordReset();
        mScreenDetector.setFirstStart();
        stopRepeatingMyRecord();
        try {
            mMediaRecorder.stop();
        }catch (Exception e){
            startRecording();
        }
        if (MainActivity.isUserRecordVisible()) {
            myStartEvent.fireWorkspaceStart();
        } else {
            this.startCameraPreviewSession();
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
        //mCurrentFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fileName());
        mCurrentFile = new File(createDirectory("DETECTION"), fileName());
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
    //создание папки для видео
    private File createDirectory(String name) {
        File baseDir;
        if (Build.VERSION.SDK_INT < 8) {
            baseDir = Environment.getExternalStorageDirectory();
        } else {
            baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        }
        if (baseDir == null)
            return Environment.getExternalStorageDirectory();
        File folder = new File(baseDir, name);
        if (folder.exists()) {
            return folder;
        }
        if (folder.isFile()) {
            folder.delete();
        }
        if (folder.mkdirs()) {
            return folder;
        }
        return Environment.getExternalStorageDirectory();
    }

    // Проверка разрешений на использование камеры
    @SuppressLint("NewApi")
    public void openCamera() {//проверяем, получено ли разрешение на использование камеры
        try {
            if (checkSelfPermission( this.mImageView.getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                mCameraManager.openCamera(mCameraID, mCameraCallback, null);
            }
        } catch (CameraAccessException e) {
        }
    }

    protected void setHalder(Handler mBackgroundHandler, Handler mScreenHandler ){
        this.mBackgroundHandler = mBackgroundHandler;
        this.mScreenHandler = mScreenHandler;

    }
}