package com.github.lisicnu.easydownload.protocol;

/**
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public interface IDownloadProtocol {
    /**
     * buffer size in byte. 8K.
     */
    final static int BUFFER_SIZE = 0x1000;

    /**
     * start download.
     *
     * @return download status
     */
    int download();

    /**
     * pause download and exit.
     */
    void stopDownload();

    /**
     * set limit speed.
     *
     * @param speed max speed in kb.
     */
    void setLimitSpeed(int speed);
}
