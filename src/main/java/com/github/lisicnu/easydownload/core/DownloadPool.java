package com.github.lisicnu.easydownload.core;

import android.content.Context;
import android.os.SystemClock;
import android.webkit.URLUtil;

import com.github.lisicnu.easydownload.feeds.BaseFeed;
import com.github.lisicnu.easydownload.feeds.DbFeed;
import com.github.lisicnu.easydownload.feeds.DownloadingFeed;
import com.github.lisicnu.easydownload.feeds.TaskFeed;
import com.github.lisicnu.easydownload.listeners.IDownloadListener;
import com.github.lisicnu.easydownload.protocol.IDownloadProtocol;
import com.github.lisicnu.log4android.LogManager;
import com.github.lisicnu.libDroid.util.FileUtils;
import com.github.lisicnu.libDroid.util.MiscUtils;
import com.github.lisicnu.libDroid.util.StringUtils;
import com.github.lisicnu.libDroid.util.URLUtils;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */

/**
 * 當調用了 {@link DownloadTask#stopDownload()} 之後, 如果需要重新開始下載,你需要重新實例化.
 * 仅支持HTTP 下载
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class DownloadPool {
    public final static String TMP_FILE_EXT = ".edlt";

    final static int DEFAULTUPDATETIME = 1000;
    int updateTime = DEFAULTUPDATETIME;
    /**
     * 内存中的缓存下载条目.. 超过此数目的,将直接被写到数据库里面... 然后等待内存中的下载完成之后才进行读取然后继续下载
     */
    final static int MAX_CACHE_ITEMS = 16;
    final static String TAG = DownloadPool.class.getSimpleName();
    final Object listenerObj = new Object();
    /**
     * 下載池的鎖
     */
    private final Object locker = new Object();
    private final ReentrantLock taskQueueLocker = new ReentrantLock();
    private final Condition taskQueueEmptyCondition = taskQueueLocker.newCondition();
    /**
     * 同一時間進行的最多的任務個數.
     */
    int MAX_TASK = 1;
    /**
     * 这个链表用来维护下载隊列.
     */
    TaskPriorityQueue taskQueue = new TaskPriorityQueue();

    /**
     * 是否正在添加中..... 此时会在 {@link #isUseSmartDownload()} 模式下进入下一次获取记录.
     */
    boolean isAdding = false;
    private boolean autoResumeItems = true;
    /**
     * 是否可以自动继续人为暂停的条目
     */
    private boolean autoResumePaused = true;
    private boolean useSmartDownload = true;
    private DBAccess mDBAccess = null;
    private TaskCheckerRunnable taskChecker;
    /**
     * 等待继续操作时变量... 当一次操作很多时... 用此变量进行暂时停止开始下载工作..
     */
    private volatile boolean waitingAdd = false;
    private volatile boolean isPause = false;
    private Vector<IDownloadListener> listeners = new Vector<IDownloadListener>();
    /**
     * 結束下載的標誌
     */
    private volatile boolean stopped = false;
    /**
     * 保存當前正在下載中的任務信息.
     */
    private List<DownloadTask> tasks = new ArrayList<DownloadTask>();
    private String autoResumeFilter = "";
    /**
     * 保存上次没有空间的检测时间, 3min 之后再次继续下载.
     */
    private long lastNoSpace = 0;

    private DownloadPool() {
        initDownload();
    }

    /**
     * after call this method, call {@link #setDBHelper} to setDbHelper.
     *
     * @return
     */
    public static DownloadPool getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 将临时文件写成隐藏文件
     */
    private static synchronized String getTmpFileName(String fileName) {
        if (StringUtils.isNullOrEmpty(fileName))
            return fileName;

        fileName = "." + fileName;
        if (fileName.endsWith(File.separator)) {
            fileName = fileName.substring(0, fileName.length() - 1);
        }
        fileName += TMP_FILE_EXT;

        return fileName;
    }

    public DBAccess getDBHelper() {
        return mDBAccess;
    }

    public DownloadPool setDBHelper(DBAccess dbAccess) {
        if (mDBAccess == dbAccess)
            return this;

        if (mDBAccess != null) {
            try {
                mDBAccess.recycle();
            } catch (Exception e) {
            }
        }
        this.mDBAccess = dbAccess;
        return this;
    }

    /**
     * Use this to init, can avoid call multi method.
     *
     * @param context
     */
    public synchronized void init(Context context) {
        if (context == null)
            throw new NullPointerException("context can't be null.");

        setDBHelper(DBAccess.getInstance().setDBHelper(new DBSqliteHelper(context)));
    }

    public long getUpdateDBTime() {
        return updateTime;
    }

    /**
     * set download update db time. default is {@link #DEFAULTUPDATETIME}.
     *
     * @param timeSpec
     */
    public DownloadPool setUpdateDBTime(int timeSpec) {
        if (timeSpec > 100)
            updateTime = timeSpec;
        else {
            updateTime = DEFAULTUPDATETIME;
        }
        return this;
    }

    /**
     * 是否使用小内存模式下载
     *
     * @return
     */
    public boolean isUseSmartDownload() {
        return useSmartDownload;
    }

    /**
     * 小内存模式, 会自动将需要下载的写入数据库, 需要时再进行下载, 如果 {@link #isAutoResumeItems()} 为true,
     * 則會繼續所有為完成的下載[除人為暫停外], 反之, 則只繼續未開始下載的條目...
     *
     * @param useSmartDownload
     * @return
     */
    public DownloadPool setUseSmartDownload(boolean useSmartDownload) {
        this.useSmartDownload = useSmartDownload;
        return this;
    }

    public boolean isAutoResumeItems() {
        return autoResumeItems;
    }

    /**
     * 是否自动下载未完成条目, 对人为暂停的条目无效..
     *
     * @param autoResume
     * @return
     */
    public DownloadPool setAutoResumeItems(boolean autoResume) {
        if (this.autoResumeItems != autoResume) {
            this.autoResumeItems = autoResume;
        }

        try {
            taskQueueLocker.lock();
            taskQueueEmptyCondition.signal();
        } finally {
            taskQueueLocker.unlock();
        }

        return this;
    }

    /**
     * 当前是否有任务正在下载, 此方法返回的结果在 {@link #useSmartDownload} 下 不可靠
     *
     * @return
     */
    public boolean isDownloading() {
        if (tasks == null)
            return false;
        return !tasks.isEmpty();
    }

    public String getAutoResumeFilter() {
        return autoResumeFilter;
    }

    /**
     * 设置 过滤条件. {@link #isUseSmartDownload()} 为 true 的情况下才有效.
     *
     * @param filter
     */
    public void setAutoResumeFilter(String filter) {
        this.autoResumeFilter = filter;
    }

    /**
     * 继续下载时是否存在...
     *
     * @param index 表示第几次查询. 值在0,1 时有效, 超过则自动退出. 防止在自动下载模式下, TASK 条目不正常时停止下载.
     */
    private void resumeCacheItems(int index) {
        if (getDBHelper() == null || index > 1)
            return;

        if (!isAutoResumeItems()) {
            // 磁盘无空余空间的时候, 会自动在3分钟之后再重新尝试. 表示 180s 再进行尝试....
            if (SystemClock.elapsedRealtime() - lastNoSpace <= 180000)
                return;
        }

        StringBuffer buffer = new StringBuffer();
        buffer.append(DBSqliteHelper.COL_STATUS);
        String[] args;

        if (isAutoResumeItems()) {
            if (index == 0) {
                buffer.append(" not in (?,?,?,?) ");
                args = new String[]{String.valueOf(DBAccess.STATUS_DOWNLOAD_SUCCESS),
                        String.valueOf(DBAccess.STATUS_DOWNLOAD_PAUSED),
                        String.valueOf(DBAccess.STATUS_DOWNLOADING),
                        String.valueOf(DBAccess.STATUS_DOWNLOAD_PENDING)};
            } else {
                buffer.append(" not in (?) ");
                args = new String[]{String.valueOf(DBAccess.STATUS_DOWNLOAD_SUCCESS)};
            }
        } else {
            buffer.append(" =? ");
            args = new String[]{String.valueOf(DBAccess.STATUS_NOTSTART)};
        }

        if (!StringUtils.isNullOrEmpty(autoResumeFilter)) {
            buffer.append(" and ").append(autoResumeFilter);
        }

        // 使用最后访问时间作为排序的第一条件,是为了防止 多个条目在锁死的情况下, 一直获取相同的条目....

        StringBuilder orderBy = new StringBuilder().append(DBSqliteHelper.COL_TIME_LAST_ACCESS).append(" asc,")
                .append(DBSqliteHelper.COL_PRIORITY).append(" desc, ")
                .append(DBSqliteHelper.COL_TIME_START).append(" asc, ")
                .append(DBSqliteHelper.COL_ID).append(" asc ");

        int limit = MAX_CACHE_ITEMS - (taskQueue == null ? 0 : taskQueue.size());

        List<DbFeed> cacheItems = getDBHelper().findDownload(buffer.toString(),
                orderBy.toString(), null, limit, args);

        if (!cacheItems.isEmpty()) {
            addItemToQueue(cacheItems.toArray(new DbFeed[cacheItems.size()]));
        } else {
            if (isAutoResumePaused())
                resumeCacheItems(index + 1);
        }
    }

    /**
     * 此处的pause 只是暂停继续下载其他的条目, 当前已在下载的不受影响
     */
    public void pause() {
        isPause = true;
    }

    /**
     * 恢复继续下载
     */
    public void resume() {
        isPause = false;
    }

    public boolean isPause() {
        return isPause;
    }

    public List<String> getDownloadingItems() {
        Iterator<DownloadTask> ite = tasks.iterator();
        List<String> items = new ArrayList<String>(tasks.size());
        while (ite.hasNext()) {
            DownloadTask task = ite.next();
            if (task != null)
                items.add(task.getDbFeed().getDownloadUrl());
        }
        return items;
    }

    /**
     * 暫停下載
     *
     * @param url
     */
    public synchronized void pause(String url) {
        if (StringUtils.isNullOrEmpty(url))
            return;

        BaseFeed feed = removeTaskFromQueueAndList(url, false);
        onTaskStateChanged(feed, DBAccess.STATUS_DOWNLOAD_PAUSED);

        if (feed != null) {
            log("item Paused:" + url, false);
        }
    }

    /**
     * 继续未完成下载
     *
     * @param url
     */
    public synchronized void resume(String url) {
        if (StringUtils.isNullOrEmpty(url))
            return;

        if (isExistInDownloadQueue(url)) {
            log("resume But Item exist:" + url, false);

            // 此时不需要再改变状态, 已经进入了状态维护流程.
//            onTaskStateChanged(url, null, "", "", DownloadDbAccess.STATUS_DOWNLOAD_PENDING);

            return;
        }

        DbFeed item = getDBHelper().findTask(url);
        if (item != null) {
            onTaskStateChanged(item, DBAccess.STATUS_DOWNLOAD_PENDING);
            if (!isExistInDownloadQueue(url)) {
                onAddTaskQueue(new DownloadTask(item), true);
                // log("item resumed:" + url, false);
            }
        }
    }

    /**
     * 如果任务不存在, 则直接返回. 调用此函数之后, 将会清空下载信息, 然后重新从0开始下载
     *
     * @param url
     */
    public synchronized void retryDownload(String url) {
        if (StringUtils.isNullOrEmpty(url))
            return;

        log("retryItem:" + url, false);

        DbFeed item = getDBHelper().findTask(url);
        if (item == null) {
            return;
        }

        deleteDownTask(url);
        add(item, true);
    }

    /**
     * 判断下载列表中是否存在, 包含已下载列表.
     *
     * @param url
     * @return
     */
    public boolean exist(String url) {
        if (StringUtils.isNullOrEmpty(url))
            return false;

        return isExistInDownloadQueue(url) || (getDBHelper() != null && getDBHelper().isTaskExist(url));
    }

    /**
     * 判断当前下载的路径是否已经在下载列表中. 不会去查询下载数据库.....
     *
     * @param url
     * @return
     */
    private boolean isExistInDownloadQueue(String url) {
        if (StringUtils.isNullOrEmpty(url))
            return false;

        synchronized (locker) {

            Iterator<DownloadTask> ite = taskQueue.iterator();

            while (ite.hasNext()) {
                DownloadTask task = ite.next();
                if (task != null && task.getDbFeed().getDownloadUrl().equalsIgnoreCase(url))
                    return true;
            }

            ite = tasks.iterator();
            while (ite.hasNext()) {
                DownloadTask task = ite.next();
                if (task != null && task.getDbFeed().getDownloadUrl().equalsIgnoreCase(url))
                    return true;
            }
            return false;
        }
    }

    public int getMaxTaskInSameTime() {
        return MAX_TASK;
    }

    /**
     * 设置同时进行的任务数... 默认是1. 不写入数据库的条目是这个的两倍
     *
     * @param max
     * @return
     */
    public DownloadPool setMaxTaskInSameTime(int max) {
        if (max > 0)
            MAX_TASK = max;
        return this;
    }

    public void addDownloadListener(IDownloadListener listener) {
        if (!listeners.contains(listener)) {
            synchronized (listenerObj) {
                listeners.add(listener);
            }
        }
    }

    public void removeAllListeners() {
        synchronized (listenerObj) {
            if (listeners != null)
                listeners.clear();
        }
    }

    public void removeDownloadListener(IDownloadListener listener) {
        synchronized (listenerObj) {
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
    }

    private void addItemToQueue(DbFeed... items) {
        if (items == null || items.length == 0)
            return;

        waitingAdd = true;
        Outer:
        for (DbFeed item : items) {

            synchronized (locker) {
                for (Iterator<DownloadTask> ite = taskQueue.iterator(); ite.hasNext(); ) {
                    DownloadTask task = ite.next();
                    if (task != null && task.getDbFeed().getDownloadUrl().equalsIgnoreCase(item
                            .getDownloadUrl())) {
                        continue Outer;
                    }
                }
            }

            synchronized (locker) {
                for (Iterator<DownloadTask> ite = tasks.iterator(); ite.hasNext(); ) {
                    DownloadTask task = ite.next();
                    if (task != null && task.getDbFeed().getDownloadUrl().equalsIgnoreCase(item.getDownloadUrl())) {
                        continue Outer;
                    }
                }
            }

            onAddTaskQueue(new DownloadTask(item), true);
        }

        waitingAdd = false;
    }

    /**
     * 繼續之前未完成的下載.
     */
    public synchronized void resumeUnFinishedDownload() {
        int limit = isUseSmartDownload() ? MAX_CACHE_ITEMS : 0;

        List<String> argList = new ArrayList<String>();
        argList.add(String.valueOf(DBAccess.STATUS_DOWNLOAD_SUCCESS));
        argList.add(String.valueOf(DBAccess.STATUS_DOWNLOAD_PAUSED));

        StringBuffer buffer = new StringBuffer();
        buffer.append(DBSqliteHelper.COL_STATUS);
        buffer.append(" not in (?,?");
        if (isUseSmartDownload()) {
            buffer.append(",?");
            argList.add(String.valueOf(DBAccess.STATUS_NOTSTART));
        }
        buffer.append(")");

        List<DbFeed> items = getDBHelper().findDownload(buffer.toString(),
                DBSqliteHelper.COL_ID.concat(" asc "), null, limit,
                argList.toArray(new String[]{}));

        addItemToQueue(items.toArray(new DbFeed[]{}));
        log("resumingDownloadList: " + items.size(), false);
    }

    public void add(DbFeed item, boolean deleteExist) {
        if (item == null || StringUtils.isNullOrEmpty(item.getDownloadUrl()))
            return;

        TaskFeed feed = new TaskFeed();
        feed.setDownloadUrl(item.getDownloadUrl());
        feed.setPriority(item.getPriority());
        feed.setDeleteExistFile(deleteExist);
        feed.setServers(item.getServers());
        feed.setSaveDir(item.getSaveDir());
        feed.setFileName(item.getFileName());
        feed.setUserData(item.getUserData());
        feed.setUserData2(item.getUserData2());

        add(feed);
    }

    public synchronized void add(TaskFeed... entities) {
        if (entities == null || entities.length == 0)
            return;

        isAdding = true;
//        log("----batch adding ...", true);

        DbFeed[] items = new DbFeed[entities.length];
        int length = items.length;

        for (int i = 0; i < length; i++) {
            TaskFeed entity = entities[i];
            if (entity == null || !entity.isValid())
                continue;

            DownloadTask task = new DownloadTask(entity);
            if (entity.isWriteToDb())
                items[i] = getDownloadDBItemFromTask(task);

            onAddTaskQueue(task, entity.isWriteToDb());
//            log("----adding " + task.userData2 + " " + task.saveDir + task.tmpFileName, true);
        }

        firstWriteToDb(items);
        isAdding = false;
//        log("----batch adding finished.. ...", true);
    }

    DbFeed getDownloadDBItemFromTask(DownloadTask task) {
        return task == null ? null : task.getDbFeed();
    }

    /**
     * 将下载的TASK 写入到数据库中
     */
    void firstWriteToDb(DbFeed... item) {
        getDBHelper().addTask(item);
    }

    private void onAddTaskQueue(DownloadTask task, boolean writeToDb) {
        if (task == null)
            return;

        synchronized (locker) {
            if (!writeToDb || (!isUseSmartDownload() || taskQueue.size() + tasks.size() < MAX_CACHE_ITEMS)) {
                taskQueue.add(task);

                try {
                    taskQueueLocker.lock();
//                    LogUtils.d(TAG, "item added.. call signal..");
                    taskQueueEmptyCondition.signal();
                } finally {
                    taskQueueLocker.unlock();
                }
            }
        }

        // log("item added: " + task.dbFeed.getDownloadUrl(), false);
    }

    /**
     * 先从下载池中移除， 然后删除数据库记录, 临时文件会被删除.
     *
     * @param downloadUrl
     * @param deleteFile  是否删除已下载文件
     */
    public void deleteDownTask(String downloadUrl, boolean deleteFile) {
        if (StringUtils.isNullOrEmpty(downloadUrl))
            return;

        log("deleteTask: " + downloadUrl, false);

        BaseFeed feed = removeTaskFromQueueAndList(downloadUrl, true);

        if (feed == null) {
            feed = getDBHelper().findTask(downloadUrl);
        }

        // 确保临时文件被删除
        if (feed != null) {
            String tmp = getTmpFileName(feed.getFileName());
            FileUtils.delete(new File(feed.getSaveDir(), tmp).getAbsolutePath());
        }

        getDBHelper().deleteTask(downloadUrl);

        if (feed != null && deleteFile) {
            FileUtils.delete(new File(feed.getSaveDir(), feed.getFileName()), false);
        }
    }

    /**
     * 先从下载池中移除， 然后删除数据库记录, 然后删除相应的文件
     *
     * @param downloadUrl
     */
    public void deleteDownTask(String downloadUrl) {
        deleteDownTask(downloadUrl, true);
    }

    /**
     * 才能够等待队列中移除.
     *
     * @param downloadUrl
     * @param deleteTmpFile
     * @return
     */
    private BaseFeed removeFromQueue(String downloadUrl, boolean deleteTmpFile) {

        if (StringUtils.isNullOrEmpty(downloadUrl))
            return null;

        synchronized (locker) {
            BaseFeed feed = null;

            for (Iterator<DownloadTask> ite = taskQueue.iterator(); ite.hasNext(); ) {
                DownloadTask tmp = ite.next();
                if (tmp != null && tmp.getDbFeed().getDownloadUrl().equalsIgnoreCase(downloadUrl)) {
                    feed = tmp.getDbFeed();

                    ite.remove();
                    if (deleteTmpFile) {
                        tmp.deleteTmpFile();
                    }
                    break;
                }
            }
            return feed;
        }
    }

    /**
     * 从正在下载列表中移除.
     *
     * @param downloadUrl
     * @param deleteTmpFile
     * @return
     */
    private BaseFeed removeFromList(String downloadUrl, boolean deleteTmpFile) {
        if (StringUtils.isNullOrEmpty(downloadUrl))
            return null;

        synchronized (locker) {
            BaseFeed feed = null;

            int m = tasks.size();
            for (int j = 0; j < m; j++) {
                DownloadTask tmp;
                try {
                    tmp = tasks.get(j);
                } catch (Exception e) {
                    continue;
                }

                if (tmp != null && tmp.getDbFeed().getDownloadUrl().equalsIgnoreCase(downloadUrl)) {
                    feed = tmp.getDbFeed();
                    tmp.stopDownload();
                    if (deleteTmpFile) {
                        tmp.deleteTmpFile();
                    }
                    tasks.remove(j);
                    break;
                }
            }
            return feed;
        }
    }

    /**
     * return the local file path.
     *
     * @param downloadUrl
     * @param deleteTmpFile
     * @return
     */
    private BaseFeed removeTaskFromQueueAndList(String downloadUrl, boolean deleteTmpFile) {

        if (StringUtils.isNullOrEmpty(downloadUrl))
            return null;

        BaseFeed feed = removeFromQueue(downloadUrl, deleteTmpFile);
        if (feed == null) {
            feed = removeFromList(downloadUrl, deleteTmpFile);
        }

        return feed;
    }

    /**
     * 停止下载, 并删除下载的临时文件, 还会删除数据库的记录...
     *
     * @param removeNoDBTask 临时下载条目是否在移除范围之内
     */
    public void removeByUserData(String userData, boolean isUserData1, boolean fuzzyMatch,
                                 boolean removeNoDBTask) {

        synchronized (locker) {
            isPause = true;

            if (userData == null)
                userData = "";

            Iterator<DownloadTask> ite = taskQueue.iterator();
            while (ite.hasNext()) {
                DownloadTask task = ite.next();
                if (task == null)
                    continue;
                if (!task.writeToDB && !removeNoDBTask)
                    continue;

                boolean remove = false;
                if (fuzzyMatch) {
                    remove = isUserData1 ? task.getDbFeed().getUserData().contains(userData) : task
                            .getDbFeed().getUserData2().contains(userData);
                } else {
                    remove = isUserData1 ? task.getDbFeed().getUserData().equals(userData) : task
                            .getDbFeed().getUserData2().equals(userData);
                }

                if (remove) {
                    ite.remove();
                    FileUtils.delete(task.getSaveFileName());
                    getDBHelper().deleteTask(task.getDbFeed().getDownloadUrl());
                }
            }

            Iterator<DownloadTask> iterator = tasks.listIterator();
            while (iterator.hasNext()) {
                DownloadTask task = iterator.next();
                if (task == null)
                    continue;
                if (!task.writeToDB && !removeNoDBTask)
                    continue;

                boolean remove = false;
                if (fuzzyMatch) {
                    remove = isUserData1 ? task.getDbFeed().getUserData().contains(userData) : task
                            .getDbFeed().getUserData2().contains(userData);
                } else {
                    remove = isUserData1 ? task.getDbFeed().getUserData().equals(userData) : task
                            .getDbFeed().getUserData2().equals(userData);
                }

                if (!remove)
                    continue;

                iterator.remove();

                task.stopDownload();
                FileUtils.delete(task.getSaveFileName());
                getDBHelper().deleteTask(task.getDbFeed().getDownloadUrl());
            }

            isPause = false;
        }

        LogManager.v(TAG, "after remove,queue/task=" + taskQueue.size() + "/" + tasks.size());
    }

    /**
     * <p/>
     * <br/> Note:<br/> same to
     * {@link #removeByUserData(String, boolean, boolean, boolean)}, with parameter true.
     */
    public void removeByUserData(String userData, boolean isUserData1, boolean fuzzyMatch) {
        removeByUserData(userData, isUserData1, fuzzyMatch, true);
    }

    public void resumeAll() {
        isPause = false;
    }

    /**
     * 移除那些不需要写入数据库的下载任务....  同时删除文件和临时文件...
     */
    public void deleteCache() {
        synchronized (locker) {
            Iterator<DownloadTask> ite = taskQueue.iterator();
            while (ite.hasNext()) {

                DownloadTask task = ite.next();
                if (task == null || task.writeToDB)
                    continue;

                ite.remove();
                FileUtils.delete(task.getSaveFileName());
                task.deleteTmpFile();
            }
        }

        synchronized (locker) {
            Iterator<DownloadTask> ite = tasks.listIterator();
            while (ite.hasNext()) {
                DownloadTask task = ite.next();
                if (task == null)
                    continue;

                ite.remove();
                task.stopDownload();
                FileUtils.delete(task.getSaveFileName());
                task.deleteTmpFile();
            }
        }
    }

    /**
     * 初始化下载. 多次调用此方法会多次初始化, 每次初始化时都会等待上一次的结束掉, 所以多次是可能会导致等待很长的时间.
     */
    public void initDownload() {
        if (taskChecker != null) {
            stopped = true;
            while (!taskChecker.finished) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            taskChecker = null;
        }

        stopped = false;
        MiscUtils.getExecutor().execute(taskChecker = new TaskCheckerRunnable());
    }

    /**
     * 如果调用了此方法，之后再想启用下载到时候得重新调用 {@link #initDownload()} , 此方法不删除临时文件.
     */
    public void stopDownload() {
        if (stopped)
            return;

        stopped = true;
        synchronized (locker) {
            taskQueue.clear();
            try {
                taskQueueLocker.lock();
//                    LogUtils.d(TAG, "item added.. call signal..");
                taskQueueEmptyCondition.signal();
            } finally {
                taskQueueLocker.unlock();
            }

            Iterator<DownloadTask> ite = tasks.iterator();
            while (ite.hasNext()) {
                DownloadTask task = ite.next();
                if (task != null) {
                    task.stopDownload();
                    onTaskStateChanged(task.getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_UNKNOW);
                }
                ite.remove();
            }
        }
    }

    /**
     * 移除等待队列中所有未开始的任务. 并将状态更改为写磁盘错误 [等待队列中, 没有创建临时文件的任务]
     */
    private void removedAllUnStartedTask() {
        synchronized (locker) {

            for (Iterator<DownloadTask> ite = taskQueue.iterator(); ite.hasNext(); ) {
                DownloadTask tmp = ite.next();
                if (tmp != null && !tmp.isTempFileExist()) {
                    onTaskStateChanged(tmp.getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_NOSTORAGE);
                    ite.remove();
                }
            }
        }
    }

    /**
     * 下载结束之后將任務從鏈錶中移除.
     *
     * @param task
     */
    private void removeTask(DownloadTask task) {
        if (tasks == null || task == null)
            return;

        synchronized (locker) {
            Iterator<DownloadTask> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                DownloadTask tmp = iterator.next();
                if (tmp != null && tmp.getDbFeed().getDownloadUrl().equalsIgnoreCase(task.getDbFeed().getDownloadUrl())) {
                    iterator.remove();
                    break;
                }
            }
        }
//        log("task has been removed: " + task.getDbFeed().getDownloadUrl(), false);
    }

    private void log(String msg, boolean isInfo) {
        if (isInfo)
            LogManager.i(TAG, msg);
        else {
            LogManager.v(TAG, msg);
        }
    }

    protected void onTaskStart(DownloadTask task) {
        if (listeners == null || listeners.isEmpty() || task == null)
            return;

        synchronized (listenerObj) {
            for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                IDownloadListener tmp = ite.next();
                if (tmp == null)
                    continue;

                tmp.onStateChanged(task.getDbFeed(), DBAccess.STATUS_DOWNLOADING);
            }
        }
    }

    protected void onTaskFinished(BaseFeed feed) {
        if (feed == null) return;
//        log("taskFinished: " + saveDir + "/" + tmpFileName + "  " + url, false);

        onTaskStateChanged(feed, DBAccess.STATUS_DOWNLOAD_SUCCESS);

        if (listeners != null) {
            synchronized (listenerObj) {
                for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                    IDownloadListener tmp = ite.next();
                    if (tmp == null)
                        continue;

                    tmp.onTaskFinish(feed);
                }
            }
        }
    }

    protected void onProgress(BaseFeed feed, long downloaded, long totalSize) {
        if (listeners == null || listeners.isEmpty() || feed == null)
            return;

        synchronized (listenerObj) {
            for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                IDownloadListener tmp = ite.next();
                if (tmp == null)
                    continue;

                tmp.onProgress(feed, downloaded, totalSize);
            }
        }
    }

    protected void onTaskStateChanged(BaseFeed feed, int status) {
        if (feed == null) return;

        if (getDBHelper() != null) {
            getDBHelper().updateTaskState(feed.getDownloadUrl(), status);
        }

        if (listeners == null || listeners.isEmpty())
            return;

        synchronized (listenerObj) {
            for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                IDownloadListener tmp = ite.next();
                if (tmp == null)
                    continue;

                tmp.onStateChanged(feed, status);
            }
        }
    }

    protected void onMessage(BaseFeed feed, int msgCode, String msg) {
        log(msg, false);
        if (listeners == null || listeners.isEmpty() || feed == null)
            return;

        synchronized (listenerObj) {
            for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                IDownloadListener tmp = ite.next();
                if (tmp == null)
                    continue;

                tmp.onMessage(feed, msgCode, msg);
            }
        }
    }

    protected boolean onStart(BaseFeed feed) {
        if (listeners == null || listeners.isEmpty() || feed == null)
            return true;

        synchronized (listenerObj) {
            boolean result = true;
            for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                IDownloadListener tmp = ite.next();
                if (tmp == null)
                    continue;

                result &= tmp.onStart(feed);
            }
            return result;
        }

        // log("onStart=" + url + "|userData=" + userData + "|servers="
        // + StringUtils.combine(servers, ";"), false);
    }

    public boolean isAutoResumePaused() {
        return autoResumePaused;
    }

    public DownloadPool setAutoResumePaused(boolean autoResumePaused) {
        this.autoResumePaused = autoResumePaused;
        return this;
    }

    private static class SingletonHolder {
        private final static DownloadPool INSTANCE = new DownloadPool();
    }

    private class TaskCheckerRunnable implements Runnable {
        boolean finished = false;

        @Override
        public void run() {
            finished = false;
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            log("download task checker has been started... " + stopped, true);

            while (!stopped) {
                int writeDBCount = 0;
                synchronized (locker) {
                    Iterator<DownloadTask> ite = tasks.iterator();
                    while (ite.hasNext()) {
                        DownloadTask task = ite.next();
                        if (task != null && task.writeToDB)
                            writeDBCount++;
                    }
                }
                while (writeDBCount < MAX_TASK && tasks.size() < MAX_TASK * 3 && !isPause
                        && !waitingAdd && !isAdding) {

                    synchronized (locker) {
                        final DownloadTask task = taskQueue.poll();
                        if (task == null)
                            break;

                        if (task.writeToDB) {
                            writeDBCount++;
                        } else {
                            // 如果 任务的有效时间超出了....那么就自动移除...
                            if (task.lastTime > 0
                                    && (System.currentTimeMillis() - task.getDbFeed().getAddTime() > task.lastTime)) {
                                LogManager.e(TAG, "task has been over. " + task.lastTime + "  "
                                        + task.getDbFeed().getDownloadUrl());
                                continue;
                            }
                        }

                        if (onStart(task.getDbFeed())) {
                            tasks.add(task);

                            MiscUtils.getExecutor().execute(new Runnable() {

                                @Override
                                public void run() {
//                                    log("startDownload:" + task.getDbFeed().getDownloadUrl(), true);

                                    task.startDownload();
                                }
                            });
                        } else {
                            log("start download but removed. " + task.getDbFeed().getDownloadUrl() + "  userdata2="
                                    + task.getDbFeed().getUserData2(), true);
                        }
                    }

//                    log("checker queue/task, size=" + taskQueue.size() + "/" + tasks.size(), false);
                }

                if (taskQueue.isEmpty()) {
                    if (isUseSmartDownload())
                        resumeCacheItems(0);

                    if (taskQueue.isEmpty()) {
                        try {
                            taskQueueLocker.lock();

//                            LogManager.d(TAG, "taskQueue empty,start await .... " + taskQueue
// .size());
                            try {
//                                if (isAutoResumeItems())
//                                    taskQueueEmptyCondition.await(10, TimeUnit.SECONDS);
                                taskQueueEmptyCondition.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        } finally {
                            taskQueueLocker.unlock();
//                            LogManager.d(TAG, "taskQueue not empty,exit await. " + taskQueue.size
// ());
                        }
//                        try {
////                            log("start sleeping 10s.....", true);
//
//                            Thread.sleep(10000);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
                    }
                }
            }

            finished = true;
            log("download task checker has been finished. ", true);
        }
    }

    /**
     * TODO use multi-thread to download file, default one task use 3 thread to
     * download file.
     */
    public class DownloadTask implements Comparable<DownloadTask> {

        /**
         * Task locker, use for different task to update data from same object.
         * if downloadThread need to update data, should use this object to lock
         * the object.
         */
        final Object mTaskLocker = new Object();
        /**
         * 真正的下载地址 當前只支持HTTP, HTTPS. <br/>
         * <font color='red'> 可以是相对地址, 相对地址, servers 参数则不能为空... 如是绝对地址, servers
         * 参数必须为空.</font>
         */
        public String downURL;

        /**
         * 自己下载的数据库记录ID，
         */
        int refId = DBAccess.INVALIDVALUE;
        boolean downloadUrlRight = false;
        List<DownloadingFeed> items;
        DownloadThread[] threads;
        /**
         * temp file name in download.
         */
        String tmpFileName;
        volatile boolean stop = false;
        final static int IO_SINGLE_READ_2_END = -2;
        /**
         * -1 表示 从未开始下载过.<br/>
         * -2 表示 文件大小未找到, 将开启单线程一直读到文件结束.
         */
        private long fileSize = -1;
        private boolean writeToDB = true;
        /**
         * 记录任务的持续时间, 只有在 {@link #writeToDB} 为false的情况下才有用.
         */
        private long lastTime = 0;

        /**
         * downloaded size in 1s.
         */
        volatile long downloadedInSec = 0;
        /**
         * last check speed time.
         */
        volatile long lastSpeedCmpTime = 0;

        public DbFeed getDbFeed() {
            return dbFeed;
        }

        public void setDbFeed(DbFeed dbFeed) {
            this.dbFeed = dbFeed;
        }

        private DbFeed dbFeed;

        DownloadTask(TaskFeed entity) {
            if (entity == null)
                return;

            this.writeToDB = entity.isWriteToDb();
            setDbFeed(new DbFeed());
            getDbFeed().setDownloadUrl(entity.getDownloadUrl());
            getDbFeed().setDeleteExistFile(entity.isDeleteExistFile());
            getDbFeed().setSaveDir(entity.getSaveDir());
            getDbFeed().setUserData2(entity.getUserData2());
            getDbFeed().setUserData(entity.getUserData());
            getDbFeed().setServers(entity.getServers());
            getDbFeed().setPriority(entity.getPriority());
            getDbFeed().setMaxRetryTimes(entity.getMaxRetryTimes());
            getDbFeed().setRetryWaitMills(entity.getRetryWaitMills());
            getDbFeed().setThreadCount(entity.getThreadCount());
            getDbFeed().setIgnoreSize(entity.getIgnoreSize());
            getDbFeed().setLimitSpeed(entity.getLimitSpeed());
            getDbFeed().setAddTime(System.currentTimeMillis());
            getDbFeed().setFileName(entity.getFileName());
            getDbFeed().setFileName(findFileName());
            downloadedInSec = 0;
        }

        /**
         * 繼續之前為下載完成的任務.
         */
        DownloadTask(DbFeed item) {
            setDbFeed(item);
            downloadedInSec = 0;
        }

        protected boolean isTempFileExist() {
            if (StringUtils.isNullOrEmpty(getDbFeed().getFileName()))
                return false;

            String tmp = getDbFeed().getFileName();
            if (!(tmp.startsWith(".") && tmp.endsWith(TMP_FILE_EXT))) {
                tmp = getTmpFileName(tmp);
            }

            return (new File(tmp)).exists();
        }

        private void analysisDownloadable() {
            if (getDbFeed().getServers() == null || getDbFeed().getServers().isEmpty()) {
                downloadUrlRight = URLUtils.canConnect(downURL);
            } else {
                // TODO 后续需加上两个的地址进行判断... 比如 两个都带前缀的....
                int m = getDbFeed().getServers().size();
                for (int i = 0; i < m; i++) {
                    String str = getDbFeed().getServers().get(i).concat(getDbFeed().getDownloadUrl());
                    if (URLUtils.canConnect(str)) {
                        downloadUrlRight = true;
                        downURL = str;
                        break;
                    }
                }
            }
        }

        /**
         * 单个任务的下载进度更新
         *
         * @param id
         * @param curPos
         */
        protected void onTaskSaveProgress(int id, long curPos) {
            if (getDBHelper() != null) {
                getDBHelper().updateDb(id, curPos);
            }
        }

        /**
         * 任务下载失败
         * <p/>
         * //TODO 此处是否应该通知上一级目录, 当前下载已经失败, 让上级继续下一个下载
         *
         * @param feed
         * @param reason
         */
        private synchronized void onTaskFailed(BaseFeed feed, int reason) {
            if (feed == null) return;
            onTaskStateChanged(feed, reason);
            removeTaskFromQueueAndList(feed.getDownloadUrl(), false);
        }

        /**
         * 单个任务的子线程下载结束通知
         *
         * @param id
         * @param status
         */
        private synchronized void onTaskItemFinished(final int id, final int status) {
            if (status == DBAccess.STATUS_DOWNLOAD_SUCCESS) {
                boolean allFinished = isAllItemFinished();

                if (allFinished) {
                    taskFinished();
                }
            } else {
                // TODO 没有存储空间的时候 直接退出， 其他失败原因就重试下载. 不能读写磁盘时也应该重试
                if (status == DBAccess.STATUS_DOWNLOAD_ERROR_WRITEFILE) {
                    log("写文件失败, 将直接退出下载.", false);
                    onTaskFailed(getDbFeed(), status);
                } else if (status == DBAccess.STATUS_DOWNLOAD_PAUSED) {
                    // log("item stopped: " + id);
                } else if (status == DBAccess.STATUS_DOWNLOAD_ERROR_NOSTORAGE) {
                    onTaskFailed(getDbFeed(), status);
                } else {
                    if (stop)
                        return;

                    DownloadingFeed item = getItemById(id);
                    if (item == null || item.getRetryCounter() >= getDbFeed().getMaxRetryTimes()) {
                        // 以失败 结束。 不会再进行尝试

                        log("task failed:" + (item == null ? "ItemIsNull" : ("" + item.getRetryCounter())), false);
                        onTaskFailed(getDbFeed(), status);
                        return;
                    }
                    log("enter retry id: " + item.getId() + ",count:" + item.getRetryCounter(), false);
                    // 进入重试下载中
                    onRetryDownloadItem(id);
                }
            }
        }

        private void taskFinished() {
            removeTask(DownloadTask.this);

            // TODO 对文件重新命名
            new File(getDbFeed().getSaveDir(), tmpFileName).renameTo(new File(getDbFeed().getSaveDir(),
                    getDbFeed().getFileName()));

            if (getDBHelper() != null) {
                getDBHelper().taskEnd(getDbFeed().getDownloadUrl());
            }

            onTaskFinished(getDbFeed());
        }

        private boolean isAllItemFinished() {
            boolean allFinished = true;

            synchronized (mTaskLocker) {
                for (int i = 0; i < items.size(); i++) {
                    DownloadingFeed item = items.get(i);
                    allFinished = (item.getCurPos() >= item.getEndPos());
                    if (!allFinished) {
                        break;
                    }
                }
            }

            return allFinished;
        }

        /**
         * 删除下载的临时文件
         */
        public void deleteTmpFile() {
            File file;
            String tmp = getDbFeed().getFileName();
            if (tmp.startsWith(".") && tmp.endsWith(TMP_FILE_EXT)) {
                file = new File(getDbFeed().getSaveDir(), tmp);
            } else {
                tmp = getTmpFileName(tmp);
                file = new File(getDbFeed().getSaveDir(), tmp);
            }
            if (file.exists()) {
                file.delete();
                log("tempFileDeleted:" + file.getAbsolutePath(), true);
            }
        }

        /**
         * 获取文件大小, 如果失败会返回0.
         *
         * @return
         */
        public long getFileSize() {
            return fileSize;
        }

        /**
         * 返回是否已经停止下载
         */
        boolean isStopped() {
            return stop;
        }

        /**
         * 停止下载
         */
        public synchronized void stopDownload() {
            stop = true;
            if (threads == null)
                return;

            for (int i = 0; i < threads.length; i++) {
                if (threads[i] == null)
                    continue;

                threads[i].stopDownload();
                try {
                    threads[i].join(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }

                log("end stopdownload:" + threads[i].item.getId(), false);
            }
        }

        /**
         * use this method to get local disk file name.
         *
         * @return
         */
        String getSaveFileName() {
            File fi = new File(getDbFeed().getSaveDir(), getDbFeed().getFileName());
            return fi.getAbsolutePath();
        }

        private void onAnalysisURL() {
            if (listeners == null)
                return;

            synchronized (listenerObj) {
                for (Iterator<IDownloadListener> ite = listeners.iterator(); ite.hasNext(); ) {
                    IDownloadListener tmp = ite.next();
                    if (tmp == null)
                        continue;

                    tmp.onStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ANALYSIS_URL);
                }
            }
        }

        /**
         * 地址不能下载时就将自己给移除掉. 同时更改状态
         */
        private void onAnalysisURLFailed() {
            onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_HTTP);
            removeTask(this);
//            log("onAnalysisURLFailed " + getDbFeed().getDownloadUrl(), false);
        }

        /**
         * 重複調用此方法可能會導致出錯.
         */
        public synchronized void startDownload() {
            synchronized (mTaskLocker) {
                if (stop)
                    return;

                downURL = URLUtils.formatUrl(getDbFeed().getDownloadUrl());

                onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_PENDING);

                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put(DBSqliteHelper.COL_TIME_LAST_ACCESS, System.currentTimeMillis());
                getDBHelper().updateDBKey(getDbFeed().getDownloadUrl(), map);

                if (refId == DBAccess.INVALIDVALUE) {
                    // TODO look download ref ID from database.
                    refId = mDBAccess.findRefId(getDbFeed().getDownloadUrl());
                }

                onAnalysisURL();

                analysisDownloadable();

                // log("downloadUrlRight=" + downloadUrlRight + "|getDbFeed().getDownloadUrl():" +
                // getDbFeed().getDownloadUrl() + "|download url: " + downURL, false);

                if (!downloadUrlRight) {
                    onAnalysisURLFailed();
                    return;
                }

                // TODO 文件大小 小于0 说明是第一次开始下载...
                if (getFileSize() == -1) {
                    findFileSize();
                    if (refId != DBAccess.INVALIDVALUE)
                        getDBHelper().updateDbFileSize(refId, getFileSize() == IO_SINGLE_READ_2_END ? 0 :
                                getFileSize());
                }
                if (getFileSize() == -1) {
                    removeTask(this);
                    onMessage(getDbFeed(), IDownloadListener.MSG_CODE_GET_FILE_SIZE_FAILD,
                            "获取文件大小失败了");

                    log("task fileSize is 0: " + getDbFeed().getDownloadUrl(), true);

                    onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_HTTP);
                    return;
                }

                tmpFileName = getTmpFileName(getDbFeed().getFileName());

                // 獲取當前文件的下載記錄.
                items = getDBHelper().findItems(getDbFeed().getDownloadUrl());
                if (stop)
                    return;

                // TODO 此处应该检测磁盘的剩余空间大小， 再继续下载的情况下，
                // 此处应该检测文件是否存在，如果数据库存在下载记录但是文件不存在，则删除数据库的记录，強制重新開始下載

                File saveFile = new File(getDbFeed().getSaveDir(), tmpFileName);

                if (items.isEmpty()) {
                    if (getFileSize() != IO_SINGLE_READ_2_END && getFileSize() != 0) {
                        int val = FileUtils.createNewFile(saveFile, getFileSize());
                        if (val == FileUtils.CREATE_NEW_FILE_NO_FREESPACE) {
                            lastNoSpace = SystemClock.elapsedRealtime();
                            onMessage(getDbFeed(), IDownloadListener.MSG_CODE_STORAGE_NOT_ENOUGH,
                                    "磁盘剩余空间不足");
                            onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_NOSTORAGE);

                            removedAllUnStartedTask();

                            removeTask(this);
                            return;
                        } else if (val == FileUtils.CREATE_NEW_FILE_FAILED) {
                            onMessage(getDbFeed(), IDownloadListener.MSG_CODE_CREATE_FILE_FAILED,
                                    "文件创建失败");

                            onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_WRITEFILE);

                            removeTask(this);
                            return;
                        }
                    }

                    // 此时文件尚未开始下载, 对文件进行分片
                    startUnstartFile();
                } else {
                    threads = new DownloadThread[items.size()];
                    // TODO 继续下载时，文件被删除了。
                    if (!saveFile.exists()) {
                        // 此时强制删除下载条目，然后对重新添加
                        onFileDeleted(saveFile);
                        return;
                    }
                }

                if (stop) {
                    return;
                }

                // 如果數據庫裏面沒有當前文件的下載記錄, 則創建新的記錄, 再等到下載完成之後再將數據庫裡的信息刪除掉.
                // 賦值給新線程. 然後開始下載.

                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setLastTaskItem((items.get(i).getEndPos() >= getFileSize() - 1));
                    threads[i] = new DownloadThread(saveFile, items.get(i), downURL);
                    MiscUtils.getExecutor().execute(threads[i]);
                }

                onTaskStart(this);
            }
        }

        void onTaskProgress() {
            int download = 0;
            for (DownloadingFeed item : items) {
                if (item != null)
                    download += (item.getCurPos() - item.getStartPos() + 1);
            }

            onProgress(getDbFeed(), download, getFileSize());
        }

        /**
         * 将还没有开始下载的文件进行分段并写入数据库
         */
        private void startUnstartFile() {
            if (stop)
                return;

            int threadCount = getDbFeed().getThreadCount();
            if (getFileSize() < getDbFeed().getIgnoreSize() && getDbFeed().getIgnoreSize() > 0) {
                // if file size is less than 4M, will use one thread.
                threadCount = 1;
            }

            threads = new DownloadThread[threadCount];

            int blockSize = (int) Math.ceil((getFileSize()) / (threadCount * 1f));
            if (blockSize < 0) blockSize = 0;

            for (int i = 0; i < threads.length; i++) {
                DownloadingFeed item = new DownloadingFeed();

                item.setRefUserData2(getDbFeed().getUserData2());
                item.setRefUserData(getDbFeed().getUserData());
                item.setRefDId(refId);
                item.setStartPos(i * blockSize);
                item.setCurPos(i * blockSize);

                if (i == threads.length - 1) {
                    item.setEndPos(getFileSize());
                } else {
                    item.setEndPos((i + 1) * blockSize);
                }

                if (item.getEndPos() >= getFileSize()) {
                    item.setEndPos(getFileSize());
                }

                items.add(item);
            }

            getDBHelper().addTaskItems(items);
        }

        /**
         * TODO 当下载的文件被中途删除时， 此时应该强制删除数据库记录，然后重新开始下载
         */
        private void onFileDeleted(File saveFile) {

            onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_UNKNOW);

            log("onFileDeleted|" + saveFile.getPath(), false);
            retryDownload(getDbFeed().getDownloadUrl());
        }

        /**
         * 读取文件信息，包括文件名和文件大小
         */
        private String findFileName() {
            String fileName = getDbFeed().getFileName();
            if (StringUtils.isNullOrEmpty(fileName)) {
                fileName = FileUtils.removeInvalidSeprator(URLUtils.analysisFileName(getDbFeed().getDownloadUrl()));
            }

            String tt = new String(fileName);
            int counter = 0;
            while (true) {
                counter++;
                boolean needContinue = false;

                if (getDBHelper().isTaskDownloadNameExist(getDbFeed().getSaveDir(), tt)) {
                    needContinue = true;
                } else {
                    File fi = new File(getDbFeed().getSaveDir(), tt);
                    if (fi.exists()) {
                        if (getDbFeed().isDeleteExistFile()) {
                            fi.delete();
                            break;
                        }
                        needContinue = true;
                    }
                }

                if (needContinue) {
                    tt = counter + fileName;
                } else {
                    break;
                }
            }
            return tt;
        }

        private void findFileSize() {
            if (!URLUtil.isNetworkUrl(downURL)) {
                log("findFileSize not invalid network URL.", false);
                onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOAD_ERROR_PROTOCOL_NOT_FOUND);
                return;
            }

            try {
                HttpURLConnection conn = URLUtils.getNormalCon(downURL);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    fileSize = conn.getContentLength();
                    if (getFileSize() == -1) {
                        fileSize = IO_SINGLE_READ_2_END;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                LogManager.e(TAG, e);
            }

//            log("FileSize:" + fileSize + " tmpFileName:" + tmpFileName, false);
        }

        private DownloadingFeed getItemById(final int curId) {
            DownloadingFeed item = null;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getId() == curId) {
                    item = items.get(i);
                    break;
                }
            }
            return item;
        }

        /**
         * 尝试重新下载。
         *
         * @param curId item裏面到ID值。
         */
        private void onRetryDownloadItem(final int curId) {
            MiscUtils.getExecutor().execute(new Runnable() {

                public void run() {
                    try {
                        Thread.sleep(getDbFeed().getRetryWaitMills());
                    } catch (InterruptedException e) {
                        LogManager.d(TAG + " retryDownload", e.toString());
                    }

                    if (stop)
                        return;

                    DownloadingFeed item = getItemById(curId);
                    synchronized (mTaskLocker) {
                        item.setRetryCounter(item.getRetryCounter() + 1);
                    }

                    DownloadThread retryThread = new DownloadThread(new File(
                            DownloadTask.this.getDbFeed().getSaveDir(), getDbFeed().getFileName()), item, DownloadTask.this.downURL);

                    synchronized (locker) {
                        for (int i = 0; i < threads.length; i++) {
                            if (threads[i].item.getId() == curId) {
                                threads[i].stopDownload();
                                threads[i] = retryThread;
                                break;
                            }
                        }
                    }

                    MiscUtils.getExecutor().execute(retryThread);
                }
            });
        }

        @Override
        public int compareTo(DownloadTask task) {
            if (task == null) return 1;

            return getDbFeed().getPriority() > task.getDbFeed().getPriority() ? 1 : ((getDbFeed().getPriority()
                    == task.getDbFeed().getPriority()) ? 0 : -1);
        }

        /**
         * 真正的下载线程, 应该在上一级目录检测是否需要开启当前线程进行下载. [当前位置等于结束位置时, 此时不应该开启下载线程] <br/>
         * <blockquote><font color='orange'>需要进行处理的情况有: 暂停 , 继续 , 连接server地址失败,
         * 下载出错, 写文件出错. SD卡中途给拔掉, 等情况.</font> <font
         * color='red'>注：这种下载方式不支持暂停，所以当外部调用暂停时，此线程应该终止掉。</font>
         */
        private final class DownloadThread extends Thread {

            /**
             * buffer size in byte.
             */
            final static int BUFFER_SIZE = 0x6000;
            /**
             * 保存的文件
             */
            private final File saveFile;
            public volatile boolean isFinished = false;
            DownloadingFeed item;
            String downPath;
            IDownloadProtocol downloadProtocol;
            /**
             * 停止信号量
             */
            private volatile boolean stopDownload = false;

            /**
             * @param downloadUrl :下载地址
             * @param saveFile    :下载路径
             * @param item
             */
            public DownloadThread(File saveFile, DownloadingFeed item, String downloadUrl) {
                this.saveFile = saveFile;
                this.item = item;
                this.downPath = downloadUrl;
                this.stopDownload = false;
            }

            /**
             * 停止下载
             */
            protected void stopDownload() {
                this.stopDownload = true;

                if (downloadProtocol != null) {
                    downloadProtocol.stopDownload();
                }
            }

            /**
             * @return {@link {@link DBAccess#STATUS_DOWNLOAD_ERROR_PROTOCOL_NOT_FOUND }}
             * 表示协议不被支持, 应该直接退出, 反之则是正常的.
             */
            int analysisProtocol() {
                int cd = DBAccess.STATUS_DOWNLOAD_ERROR_PROTOCOL_NOT_FOUND;

                if (URLUtil.isHttpsUrl(downPath) || URLUtil.isHttpUrl(downPath)) {
                    downloadProtocol = new HttpProtocol(DownloadTask.this, item, saveFile);
                    cd = ~cd;
                }
                return cd;
            }

            /**
             * 当前下载的位置
             */
            @Override
            public void run() {
                // 未下载完成
                int code;

                boolean resumeDownload = true;
                if (item.getEndPos() > 0) {
                    resumeDownload = (item.isLastTaskItem() && item.getCurPos() < item.getEndPos())
                            || (!item.isLastTaskItem() && item.getCurPos() <= item.getEndPos());
                }

                if (resumeDownload) {
                    onTaskStateChanged(getDbFeed(), DBAccess.STATUS_DOWNLOADING);

                    code = analysisProtocol();
                    if (downloadProtocol != null) {
                        downloadProtocol.setLimitSpeed(getDbFeed().getLimitSpeed());
                    }
                    if (code != DBAccess.STATUS_DOWNLOAD_ERROR_PROTOCOL_NOT_FOUND) {
                        code = (downloadProtocol != null) ? downloadProtocol.download()
                                : DBAccess.STATUS_DOWNLOAD_ERROR_PROTOCOL_NOT_FOUND;

                        if (code == DBAccess.STATUS_DOWNLOAD_SUCCESS) {
                            getDBHelper().updateDbFileSize(refId, item.getCurPos());
                        }
                    } else {
                        log("protocol not found:" + downPath, true);
                    }
                } else {
                    code = DBAccess.STATUS_DOWNLOAD_SUCCESS;
                }

                onTaskProgress();
                isFinished = true;
                onTaskItemFinished(item.getId(), code);
            }
        }
    }
}
