package com.github.lisicnu.easydownload.listeners;

import com.github.lisicnu.easydownload.feeds.BaseFeed;

/**
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class SimpleDownloadListener implements IDownloadListener {

    @Override
    public void onStateChanged(BaseFeed feed, int status) {

    }

    @Override
    public boolean onStart(BaseFeed feed) {
        return true;
    }

    @Override
    public void onProgress(BaseFeed feed, long downloadedSize, long totalSize) {

    }

    @Override
    public void onTaskFinish(BaseFeed feed) {

    }

    @Override
    public void onMessage(BaseFeed feed, int msgCode, String msg) {

    }
}
