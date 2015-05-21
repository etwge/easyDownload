package com.github.lisicnu.easydownload.feeds;

import com.github.lisicnu.libDroid.util.ArrayUtils;
import com.github.lisicnu.libDroid.util.StringUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class BaseFeed {
    private static final String SERVER_SEPARATOR = "[ds_separator]";

    private String downloadUrl = "";
    private String saveDir = "";
    private String fileName = "";
    private String userData = "";
    private String userData2 = "";
    private List<String> servers;
    private int priority;
    private int maxRetryTimes = 10; // 单次下载的重试次数.
    private int retryWaitMills = 3000; // 单次下载重试之间的等待时间.
    private int threadCount = 3;
    private int limitSpeed = 0;
    private int ignoreSize = 4194304;
    private boolean deleteExistFile = true;

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSaveDir() {
        return saveDir;
    }

    public void setSaveDir(String saveDir) {
        this.saveDir = saveDir;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void setMaxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes > 0 ? maxRetryTimes : 0;
    }

    public int getRetryWaitMills() {
        return retryWaitMills;
    }

    public void setRetryWaitMills(int retryWaitMills) {
        this.retryWaitMills = retryWaitMills < 0 ? 0 : retryWaitMills;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount > 0 ? threadCount : 1;
    }

    public int getLimitSpeed() {
        return limitSpeed;
    }

    public void setLimitSpeed(int limitSpeed) {
        this.limitSpeed = limitSpeed;
    }

    public int getIgnoreSize() {
        return ignoreSize;
    }

    public void setIgnoreSize(int ignoreSize) {
        this.ignoreSize = ignoreSize > 0 ? ignoreSize : 4194314;
    }

    public boolean isDeleteExistFile() {
        return deleteExistFile;
    }

    public void setDeleteExistFile(boolean deleteExistFile) {
        this.deleteExistFile = deleteExistFile;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public void setServers(String... servers) {
        setServers(ArrayUtils.asList(servers));
    }

    public String getServersStr() {
        return parseServersToStr(servers);
    }

    public static String parseServersToStr(List<String> items) {
        StringBuilder builder = new StringBuilder();
        if (items != null) {
            for (String string : items) {
                if (StringUtils.isNullOrEmpty(string)) continue;

                builder.append(string);
                builder.append(SERVER_SEPARATOR);
            }
        }
        return builder.toString();
    }

    public static List<String> parseServerToList(String server) {
        if (StringUtils.isNullOrEmpty(server)) {
            return new ArrayList<String>();
        }

        return StringUtils.split(server, SERVER_SEPARATOR, true);
    }
}
