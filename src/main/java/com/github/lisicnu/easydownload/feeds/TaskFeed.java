package com.github.lisicnu.easydownload.feeds;

import com.github.lisicnu.libDroid.util.StringUtils;

/**
 * <p/>
 * <p/>Author: Eden <p/>
 * Date: 2014/9/15 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */

public class TaskFeed extends BaseFeed {

    private boolean writeToDb = true;

    /**
     * 持续时间, 只对不写入数据库的记录有效, 默认为0, 表示一直有效. 单位为 ms.
     */
    private long lastTime = 0;

    /**
     * 获取 不写入数据库的任务的持续时间. 0  表示一直存在.
     *
     * @return
     */
    public long getLastTime() {
        return lastTime;
    }

    /**
     * 设置不写入数据库任务的持续时间.
     *
     * @param lastTime <=0 表示一直存在.
     */
    public TaskFeed setLastTime(long lastTime) {
        this.lastTime = lastTime;
        return this;
    }

    public boolean isWriteToDb() {
        return writeToDb;
    }

    /**
     * 是否将此条目,写入数据库.默认为 true, 如果选择不写入数据库, 那么下载条目只在当前的运行过程中有效.
     *
     * @param writeToDb
     */
    public TaskFeed setWriteToDb(boolean writeToDb) {
        this.writeToDb = writeToDb;
        return this;
    }

    public boolean isValid() {
        if (StringUtils.isNullOrEmpty(getSaveDir()) && StringUtils.isNullOrEmpty(getFileName()))
            return false;
        return !StringUtils.isNullOrEmpty(getDownloadUrl());
    }

}


