package com.github.lisicnu.easydownload.core;

import java.util.concurrent.PriorityBlockingQueue;


/**
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class TaskPriorityQueue extends PriorityBlockingQueue<DownloadPool.DownloadTask> {

    @Override
    public boolean add(DownloadPool.DownloadTask downloadTask) {
        return downloadTask != null && super.add(downloadTask);
    }

}
