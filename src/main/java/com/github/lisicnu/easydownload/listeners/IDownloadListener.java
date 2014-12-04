package com.github.lisicnu.easydownload.listeners;
/**
 * <p/>Author: Eden <p/>
 * Date: 2014/11/20 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */

import com.github.lisicnu.easydownload.feeds.BaseFeed;


/**
 * 下載對外更新狀態接口.
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public interface IDownloadListener {

    /**
     * 下载状态的改变, 当文件下载结束时会先调用 onTaskFinish 然后再调用此方法.<br/>
     * 当调用 {@link com.github.lisicnu.easydownload.core.DownloadPool#pause(String)} 方法时, 此时的userData 和 userData2 将会为
     * empty.
     *
     * @param feed
     * @param status 此處的值參加 DownloadDbAccess里的STATUS_XX值.
     */
    void onStateChanged(BaseFeed feed, int status);

    /**
     * 开始下载任务之前执行, 用来执行用户动作... [e.g.如文件版本信息比较, 更改下载地址等].
     *
     * @param feed
     * @return 返回是否应该继续下载.. 默认需要返回true
     */
    boolean onStart(BaseFeed feed);

    /**
     * 任務進度更新通知.
     *
     * @param feed           下載的地址
     * @param downloadedSize 已經下載的文件大小
     * @param totalSize      當前文件的整體大小
     */
    void onProgress(BaseFeed feed, long downloadedSize, long totalSize);

    /**
     * 任務結束通知, 還是會觸發 {@link #onStateChanged(com.github.lisicnu.easydownload.feeds.BaseFeed, int)}
     * 方法.
     */
    void onTaskFinish(BaseFeed feed);

    /**
     * 显示提示信息到界面上
     *
     * @param feed
     * @param msgCode
     * @param msg
     */
    void onMessage(BaseFeed feed, int msgCode, String msg);

    public final int MSG_CODE_GET_FILE_SIZE_FAILD = 0x1;
    public final int MSG_CODE_STORAGE_NOT_ENOUGH = 0x2;
    public final int MSG_CODE_CREATE_FILE_FAILED = 0x3;
}