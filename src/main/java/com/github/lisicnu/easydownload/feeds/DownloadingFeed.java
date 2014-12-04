package com.github.lisicnu.easydownload.feeds;

/**
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class DownloadingFeed {
    private int id;
    /**
     * 此处的refDid 引用的是下载表里面的 id
     */
    private int refDId;

    /**
     * 此处 refResid 引用的是资源的id
     */
    private String refUserData = "";
    private String refUserData2 = "";
    private long startPos;
    private long endPos;
    private long curPos;

    /**
     * 只在运行时有效，不存入数据库
     */
    private int retryCounter;

    /**
     * 只在运行时有效，不存入数据库, 判断是否是最后一个分组�?
     */
    private boolean isLastTaskItem;

    public DownloadingFeed() {
        setRetryCounter(0);
        setLastTaskItem(false);
    }

    @Override
    public String toString() {

        return "downloadingItem:" + getRefUserData();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRefDId() {
        return refDId;
    }

    public void setRefDId(int refDId) {
        this.refDId = refDId;
    }

    public String getRefUserData() {
        return refUserData;
    }

    public void setRefUserData(String refUserData) {
        this.refUserData = refUserData;
    }

    public String getRefUserData2() {
        return refUserData2;
    }

    public void setRefUserData2(String refUserData2) {
        this.refUserData2 = refUserData2;
    }

    public long getStartPos() {
        return startPos;
    }

    public void setStartPos(long startPos) {
        this.startPos = startPos;
    }

    public long getEndPos() {
        return endPos;
    }

    public void setEndPos(long endPos) {
        this.endPos = endPos;
    }

    public long getCurPos() {
        return curPos;
    }

    public void setCurPos(long curPos) {
        this.curPos = curPos;
    }

    public int getRetryCounter() {
        return retryCounter;
    }

    public void setRetryCounter(int retryCounter) {
        this.retryCounter = retryCounter;
    }

    public boolean isLastTaskItem() {
        return isLastTaskItem;
    }

    public void setLastTaskItem(boolean isLastTaskItem) {
        this.isLastTaskItem = isLastTaskItem;
    }

}
