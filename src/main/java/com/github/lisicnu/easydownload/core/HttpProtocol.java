package com.github.lisicnu.easydownload.core;

import android.os.SystemClock;

import com.github.lisicnu.easydownload.feeds.DownloadingFeed;
import com.github.lisicnu.easydownload.protocol.IDownloadProtocol;
import com.github.lisicnu.log4android.LogManager;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class HttpProtocol implements IDownloadProtocol {

    final static String TAG = "HttpProtocol";

    DownloadingFeed item;
    File saveFile;
    DownloadPool.DownloadTask task;
    volatile boolean stopDownload;
    int limitSpeed = 0;

    public HttpProtocol(DownloadPool.DownloadTask task, DownloadingFeed item, File saveFile) {
        this.item = item;
        this.task = task;
        this.saveFile = saveFile;
        if (item == null || task == null || saveFile == null)
            throw new NullPointerException();
    }

    @Override
    public void stopDownload() {
        stopDownload = true;
    }

    @Override
    public void setLimitSpeed(int speed) {
        if (speed < 0)
            speed = 0;

        limitSpeed = speed * 1024;
    }

    @Override
    public int download() {
        int code = DBAccess.STATUS_DOWNLOAD_SUCCESS;
        InputStream inStream = null;
        RandomAccessFile rndFile = null;
        long curPos = item.getCurPos();
        HttpGet httpGet = null;
        HttpClient client = null;

        try {
            HttpParams httpParameters = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParameters, 10000);
            HttpConnectionParams.setSoTimeout(httpParameters, 30000);
            client = new DefaultHttpClient(httpParameters);
            httpGet = new HttpGet(task.downURL);
            httpGet.addHeader("Range", "bytes=" + curPos + "-" + item.getEndPos());

            HttpResponse response = client.execute(httpGet);
            inStream = response.getEntity().getContent();

            byte[] buffer = new byte[BUFFER_SIZE];
            int readLen = 0;

            rndFile = new RandomAccessFile(saveFile, "rws");
            rndFile.seek(curPos);

            // TODO 第一次更新的时间短一点, 防止同时刷新大量数据, 尽量防止同一时间更新数据库.
            long tmpLastUpdateTime = System.currentTimeMillis() - item.getId()
                    % task.getDbFeed().getThreadCount() * 200;

            while (!stopDownload) {

                readLen = inStream.read(buffer, 0, buffer.length);
                if (readLen <= 0 || stopDownload) break;

                if (!saveFile.exists() || !saveFile.canWrite()) {
                    return code = DBAccess.STATUS_DOWNLOAD_ERROR_WRITEFILE;
                }

                try {
                    rndFile.write(buffer, 0, readLen);
                } catch (Exception e) {
                    LogManager.e(TAG, item.getId() + e.toString());
                    if (!saveFile.exists() || !saveFile.canWrite()) {
                        return code = DBAccess.STATUS_DOWNLOAD_ERROR_WRITEFILE;
                    } else {
                        return code = DBAccess.STATUS_DOWNLOAD_ERROR_UNKNOW;
                    }
                }

                task.downloadedInSec += readLen;
                curPos += readLen;
                item.setCurPos(curPos);

                if (System.currentTimeMillis() - tmpLastUpdateTime > DownloadPool.getInstance
                        ().getUpdateTime()) {

                    task.onTaskSaveProgress(item.getId(), curPos);
                    task.onTaskProgress();

                    tmpLastUpdateTime = System.currentTimeMillis();
                }

                if (item.getEndPos() > 0 && item.getCurPos() > item.getEndPos()) {
//                    LogManager.e(TAG, "out of range..... end/cur=" + item.getEndPos() + "/" + item.getCurPos());
                    break;
                }

                limitSpeedCheck();
            }

            task.onTaskSaveProgress(item.getId(), curPos);
            task.onTaskProgress();

            if (stopDownload) {
                code = DBAccess.STATUS_DOWNLOAD_PAUSED;
            }
        } catch (Exception e) {
            LogManager.e(TAG, getClass().getName() + ":" + e.toString());
            code = DBAccess.STATUS_DOWNLOAD_ERROR_UNKNOW;
        } finally {
            if (httpGet != null) {
                httpGet.abort();
            }
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (Exception e2) {
                    // TODO: handle exception
                }
                inStream = null;
            }
            if (client != null) {
                client.getConnectionManager().shutdown();
                client = null;
            }
            if (rndFile != null) {
                try {
                    rndFile.close();
                } catch (Exception e2) {
                    // TODO: handle exception
                }
                rndFile = null;
            }
            item.setCurPos(curPos);
        }
        return code;
    }

    private void limitSpeedCheck() {
        if (limitSpeed <= 0 || task.downloadedInSec < limitSpeed)
            return;

        long t = SystemClock.elapsedRealtime() - task.lastSpeedCmpTime;
        // to sleep. and then modify the basic count value.
        try {
            Thread.sleep(t < 1000 ? 1000 - t : 0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (task.mTaskLocker) {
            if (task.downloadedInSec >= limitSpeed) {
                task.downloadedInSec = 0;
                task.lastSpeedCmpTime = SystemClock.elapsedRealtime();
            }
        }
    }
}
