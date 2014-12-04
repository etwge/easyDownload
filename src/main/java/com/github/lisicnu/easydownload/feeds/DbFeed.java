package com.github.lisicnu.easydownload.feeds;

/**
 * Download Task's item, save task's information except download position.
 * Note:<br/>此item 中的 curPos 會隨著下載的進行而隨時進行更新.
 * id值默認為-1, 當添加到數據庫之後, 會更改為數據庫記錄的id
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class DbFeed extends BaseFeed {

    private int id = -1;
    private long fileSize;
    private long startTime;
    private long endTime;
    private long lastAccessTime;
    private int status;
    private long addTime = 0;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * @return the lastAccessTime
     */
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * @param lastAccessTime the lastAccessTime to set
     */
    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public long getAddTime() {
        return addTime;
    }

    public void setAddTime(long addTime) {
        this.addTime = addTime;
    }
}
