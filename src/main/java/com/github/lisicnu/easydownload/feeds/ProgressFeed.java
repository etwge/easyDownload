package com.github.lisicnu.easydownload.feeds;

import java.util.List;


/**
 * 下载进度条目
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class ProgressFeed {
    private int id;
    /**
     * 此处的refDId 引用的是下载表里面的 id
     */
    private int refDId;

    private String userData = "";
    private String userData2 = "";

    private String servers = ""; // 下载的服务器地址列表..

    private int total; // 当前下载的整个文件大小

    private int downloaded; // 当前下载的文件已下载的大小

    private String fileName = ""; // 本地文件名

    private String source = ""; // 下载地址..

    private String saveDir = ""; // 保存地址

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    private int priority; // 下载的优先级, 值越大, 优先级越高

    /**
     * 下载状态
     */
    private int status;

    private int retryCounter; // 重试次数...

    @Override
    public String toString() {
        return getFileName() + "#" + getRefDId();
    }

    public String getFileName() {
        if (fileName != null && !fileName.isEmpty() && fileName.startsWith("."))
            return fileName.substring(1);
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public String getUserData() {
        return userData;
    }

    public void setUserData(String userData) {
        this.userData = userData;
    }

    public String getUserData2() {
        return userData2;
    }

    public void setUserData2(String userData2) {
        this.userData2 = userData2;
    }

    public String getServers() {
        return servers;
    }

    public List<String> getServerList() {
        return DbFeed.parseServerToList(servers);
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getDownloaded() {
        return downloaded;
    }

    public void setDownloaded(int downloaded) {
        this.downloaded = downloaded;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getRetryCounter() {
        return retryCounter;
    }

    public void setRetryCounter(int retryCounter) {
        this.retryCounter = retryCounter;
    }

    public String getSaveDir() {
        return saveDir;
    }

    public void setSaveDir(String saveDir) {
        this.saveDir = saveDir;
    }
}
