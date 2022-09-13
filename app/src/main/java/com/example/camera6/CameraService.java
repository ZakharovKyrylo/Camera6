package com.example.camera6;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;

import static androidx.core.content.ContextCompat.checkSelfPermission;
import static com.example.camera6.AllValueToChange.heightScreen;
import static com.example.camera6.AllValueToChange.heightSurface;
import static com.example.camera6.AllValueToChange.mCameraID;
import static com.example.camera6.AllValueToChange.myLog;
import static com.example.camera6.AllValueToChange.recordTime;
import static com.example.camera6.AllValueToChange.screenDelay;
import static com.example.camera6.AllValueToChange.widthScreen;
import static com.example.camera6.AllValueToChange.widthSurface;

public class CameraService {

    private ScreenDetector mScreenDetector;
    private CameraDevice mCameraDevice = null;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mSession;
    private ImageReader mImageReader;
    private Timer timerForScreen;
    private Timer timerStopRec = new Timer();
    private List<Surface> surfaceList = new ArrayList<>();
    private CameraManager mCameraManager;
    private TextureView mImageView;
    private MediaRecorder mMediaRecorder = null;
    private StartCameraSource myStartEvent;
    private Handler mBackgroundHandler = null;
    private HandlerThread mBackgroundThread;
    private Handler screenHandler = null;
    private HandlerThread screenThread;
    private VideoSetting createMyFile = new VideoSetting();
    private Surface surface;

    public CameraService(CameraManager cameraManager, TextureView mImageView, StartCameraSource myStartEvent) {
        this.mCameraManager = cameraManager;
        this.mImageView = mImageView;
        this.myStartEvent = myStartEvent;
        mScreenDetector = new ScreenDetector(myStartEvent);
        this.startScreenThread();
        this.startBackgroundThread();
    }

    @SuppressLint("NewApi")//проверяем, получено ли разрешение на использование камеры
    public void openCamera() {
        try {
            if (checkSelfPermission(this.mImageView.getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                mCameraManager.openCamera(mCameraID, mCameraCallback, null);
            }
        } catch (CameraAccessException e) {
            Log.i(myLog, e.toString());
        }
    }

    // открытие камеры
    private final CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {
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

    // ToDo
    private void startCameraPreviewSession() {  // вывод изображения на экран во время
        mImageReader = ImageReader.newInstance(widthScreen, heightScreen, ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, screenHandler);
        this.createSurface();
        try {
            surfaceList.add(mImageReader.getSurface());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(surfaceList, previewStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        this.startTimerForScreen();
    }

    private void startRecordSession() {
        this.createSurface();
        mPreviewBuilder.addTarget(mMediaRecorder.getSurface());
        surfaceList.add(mMediaRecorder.getSurface());
        try {
            mCameraDevice.createCaptureSession(surfaceList, previewStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createSurface() {
        surfaceList.clear();
        SurfaceTexture texture = mImageView.getSurfaceTexture();
        texture.setDefaultBufferSize(widthSurface, heightSurface);
        surface = new Surface(texture);
        surfaceList.add(surface);
    }

    CameraCaptureSession.StateCallback previewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mSession = session;
            try {
                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    private void startTimerForScreen() {
        timerForScreen = new Timer();
        timerForScreen.schedule(new TimerTask() {
            @Override
            public void run() {
                makePhoto();
            }
        }, screenDelay, screenDelay);
    }

    // разобратся что тут происходит
    // Запрос на готовность фото
    private void makePhoto() {
        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(mImageReader.getSurface());
            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                }
            };
            mSession.capture(captureBuilder.build(), CaptureCallback, screenHandler);
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
                mScreenDetector.startNewImage(reader.acquireNextImage());
//                mScreenHandler.post(mScreenDetector);
            } catch (Exception e) {
                Log.i(myLog, "mOnImageAvailableListener");
            }
        }
    };

    public void startRecording() {
        timerForScreen.cancel();
        MainActivity.iconAutoRecordReset();
        createMyFile.setUpMediaRecorder();
        mMediaRecorder = createMyFile.getMediaRecorder();
        mMediaRecorder.start();
        this.startRecordSession();
        timerStopRec.schedule(new TimerTask() {
            @Override
            public void run() {
                myStartEvent.fireWorkspaceStop();
            }
        }, recordTime);
    }

    public void stopRecordingVideo() {
        MainActivity.iconAutoRecordReset();
        mScreenDetector.setFirstStart();
        this.stopRepeatingMyRecord();
        try {
            mMediaRecorder.stop();
        } catch (Exception e) {
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

    protected void startScreenThread() {
        screenThread = new HandlerThread("CameraScreen");
        screenThread.start();
        screenHandler = new Handler(screenThread.getLooper());
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
/*
    protected void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.i(myLog, e.toString());
        }
    }
*/
}