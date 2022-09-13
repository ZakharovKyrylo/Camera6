package com.example.camera6;

import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.example.camera6.AllValueToChange.*;

public class VideoSetting {
    private MediaRecorder mMediaRecorder;

    public MediaRecorder getMediaRecorder() {
        return mMediaRecorder;
    }

    public void setUpMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        File mCurrentFile = new File(createDirectory(folderName), generateFileName());
        mMediaRecorder.setOutputFile(mCurrentFile.getAbsolutePath());
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setVideoSize( widthSurface, heightSurface);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        try {
            mMediaRecorder.prepare();
        } catch (Exception e) {
            mCurrentFile.delete();
            setUpMediaRecorder();
        }
    }

    // генерация имени файла
    private String generateFileName() { // название файла в виде дата,месяц,год_час,минута,секунда
        Date dateNow = new Date();//("yyyy.MM.dd 'и время' hh:mm:ss a zzz");
        SimpleDateFormat formatForDateNow = new SimpleDateFormat("dd.MM.yyyy_HH:mm:ss");
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
}
