package com.github.lisicnu.easydownload.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.github.lisicnu.easydownload.feeds.BaseFeed;
import com.github.lisicnu.easydownload.feeds.DbFeed;
import com.github.lisicnu.easydownload.feeds.DownloadingFeed;
import com.github.lisicnu.easydownload.feeds.ProgressFeed;
import com.github.lisicnu.log4android.LogManager;
import com.github.lisicnu.libDroid.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class DBAccess {

    /**
     * 尚未开始, 已经添加进去了.. 但是尚未开始下载
     */
    public static final int STATUS_NOTSTART = 0;
    /**
     * 正在下载
     */
    public static final int STATUS_DOWNLOADING = 1;
    /**
     * 下载前的准备状态
     */
    public static final int STATUS_DOWNLOAD_PENDING = 2;
    /**
     * 下载中被人为停止
     */
    public static final int STATUS_DOWNLOAD_PAUSED = 3;
    /**
     * 已经成功下载
     */
    public static final int STATUS_DOWNLOAD_SUCCESS = 4;
    /**
     * 分析下载地址
     */
    public static final int STATUS_DOWNLOAD_ANALYSIS_URL = 10;
    /**
     * 下载失败 MASK
     */
    public static final int STATUS_DOWNLOAD_ERROR_MASK = 0xFF00;
    /**
     * http链接出错
     */
    public static final int STATUS_DOWNLOAD_ERROR_HTTP = 0xFF05;
    /**
     * 错误原因未知
     */
    public static final int STATUS_DOWNLOAD_ERROR_UNKNOW = 0xFF06;
    /**
     * 没有足够的存储空间
     */
    public static final int STATUS_DOWNLOAD_ERROR_NOSTORAGE = 0xFF07;
    /**
     * 尝试多次之后还是失败
     */
    public static final int STATUS_DOWNLOAD_ERROR_MAXRETRYTIMES = 0xFF08;
    /**
     * 写文件错误, 可能是文件不存在, 或者没有权限, 此时直接退出下载
     */
    public static final int STATUS_DOWNLOAD_ERROR_WRITEFILE = 0xFF09;
    /**
     * 分析下载地址时, 协议不被支持或者为识别
     */
    public static final int STATUS_DOWNLOAD_ERROR_PROTOCOL_NOT_FOUND = 0xFF10;

    public static final int INVALIDVALUE = -1;
    private static final String TAG = DBAccess.class.getSimpleName();
    private final Object mLocker = new Object();
    private DBSqliteHelper dbHelper;
    private SQLiteDatabase db;

    private DBAccess() {

    }

    /**
     * after call this method. call {@link #setDBHelper}. otherwise all
     * operation will failed.
     */
    public static DBAccess getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public DBSqliteHelper getDBHelper() {
        return dbHelper;
    }

    public DBAccess setDBHelper(DBSqliteHelper mDBAccess) {
        if (dbHelper == mDBAccess)
            return this;

        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
            }
        }
        if (dbHelper != null) {
            try {
                dbHelper.close();
            } catch (Exception e) {
            }
        }
        this.dbHelper = mDBAccess;
        db = dbHelper.getWritableDatabase();
        return this;
    }

    public SQLiteDatabase getDB() {
        return db;
    }

    /**
     * delete from download history table.[Note: Do not remove download temporary items.]
     *
     * @param where 如果 where 是空 或者null, 则表示删除所有的记录
     * @param args
     */
    public void deleteDownload(String where, Object[] args) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(where))
            return;

        synchronized (mLocker) {
            try {
                StringBuilder buffer = new StringBuilder();

                buffer.append("delete from ").append(DBSqliteHelper.TABLE_ALL);

                if (!StringUtils.isNullOrEmpty(where)) {
                    buffer.append(" where ").append(where);
                }

                db.execSQL(buffer.toString(), args);

            } catch (Exception e) {
                LogManager.e(TAG.concat("---deleteDownload"), e);
            }
        }
    }

    /**
     * delete task from download history, this will delete download temporary items.
     *
     * @param url 空或者 null  直接返回错误.
     * @return
     */
    public boolean deleteTask(String url) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(url))
            return false;

        synchronized (mLocker) {
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(128);

                buffer.append("select ").append(DBSqliteHelper.COL_ID);
                buffer.append(" from ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" where ").append(DBSqliteHelper.COL_SOURCE);
                buffer.append(" =? ");

                db.beginTransaction();
                cursor = db.rawQuery(buffer.toString(), new String[]{url});

                while (cursor.moveToNext()) {
                    int tmp = cursor.getInt(0);

                    buffer.delete(0, buffer.length());
                    buffer.append("delete from ").append(DBSqliteHelper.TABLE_TMP)
                            .append(" where ").append(DBSqliteHelper.T_COL_REFDID).append(" =?");
                    db.execSQL(buffer.toString(), new Object[]{tmp});
                }

                buffer.delete(0, buffer.length());

                buffer.append("delete from ").append(DBSqliteHelper.TABLE_ALL).append(" where ")
                        .append(DBSqliteHelper.COL_SOURCE).append("=?");

                db.execSQL(buffer.toString(), new Object[]{url});
                db.setTransactionSuccessful();
            } catch (Exception e) {
                LogManager.e(TAG, "deleteTask:".concat(e.toString()));
            } finally {
                db.endTransaction();
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
        }
        return true;
    }

    /**
     * update download task's columns value.<br/>
     * usage example:<br/>
     * <code>
     * String newServer = DownloadDbItem.parseServersToStr(tmpServers);<br/>
     * Map<String, Object> val = new HashMap<String, Object>();<br/>
     * val.put(DownloadDbHelper.COL_SERVERS, newServer);<br/>
     * </code>
     *
     * @param url    空或者null, 直接返回
     * @param values
     */
    public void updateDBKey(String url, Map<String, Object> values) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(url) || values == null || values
                .isEmpty())
            return;

        synchronized (mLocker) {
            try {
                StringBuilder buffer = new StringBuilder(64);
                buffer.append("update ").append(DBSqliteHelper.TABLE_ALL).append(" set ");

                Iterator<String> ite = values.keySet().iterator();
                int counter = 0;
                Object[] val = new Object[values.size() + 1];
                while (ite != null && ite.hasNext()) {
                    String key = ite.next();
                    buffer.append(key).append(" =? ");

                    val[counter] = values.get(key);
                    counter++;

                    if (!ite.hasNext()) {
                        break;
                    }

                    buffer.append(",");
                }

                val[val.length - 1] = url;

                buffer.append(" where ").append(DBSqliteHelper.COL_SOURCE);
                buffer.append(" =?");

                db.execSQL(buffer.toString(), val);
            } catch (Exception e) {
                LogManager.e(TAG, "updateDBKey:".concat(e.toString()));
            }
        }
    }

    /**
     * update file size in task.
     *
     * @param id       start with 0.
     * @param fileSize
     */
    protected void updateDbFileSize(int id, long fileSize) {
        if (getDB() == null || !getDB().isOpen())
            return;

        synchronized (mLocker) {
            try {
                StringBuilder buffer = new StringBuilder(64);
                buffer.append("update ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" set ").append(DBSqliteHelper.COL_FILESIZE);
                buffer.append(" =? where ").append(DBSqliteHelper.COL_ID);
                buffer.append(" =?");

                db.execSQL(buffer.toString(), new Object[]{fileSize, id});
            } catch (Exception e) {
                LogManager.e(TAG, "updateDbFileSize:".concat(e.toString()));
            }
        }
    }

    /**
     * 更新下载的服务器地址. can use {@link #updateDBKey(String, java.util.Map)} instead.
     *
     * @param id
     * @param servers
     */
    public void updateDbServers(int id, List<String> servers) {
        if (getDB() == null || !getDB().isOpen())
            return;

        synchronized (mLocker) {
            try {
                StringBuilder buffer = new StringBuilder(64);
                buffer.append("update ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" set ").append(DBSqliteHelper.COL_SERVERS);
                buffer.append(" =? where ").append(DBSqliteHelper.COL_ID);
                buffer.append(" =?");

                db.execSQL(buffer.toString(),
                        new Object[]{DbFeed.parseServersToStr(servers), id});
            } catch (Exception e) {
                LogManager.e(TAG, "updateDbServers:".concat(e.toString()));
            }
        }
    }

    /**
     * update download temporary table's download position.
     *
     * @param id
     * @param curPos
     */
    protected void updateDb(int id, long curPos) {
        if (getDB() == null || !getDB().isOpen())
            return;

        synchronized (mLocker) {
            try {
                StringBuilder buffer = new StringBuilder(50);
                buffer.append("update ").append(DBSqliteHelper.TABLE_TMP);
                buffer.append(" set ").append(DBSqliteHelper.T_COL_CURPOS);
                buffer.append(" =? where ").append(DBSqliteHelper.T_COL_ID);
                buffer.append(" =?");

                db.execSQL(buffer.toString(), new Object[]{curPos, id});
            } catch (Exception e) {
                LogManager.e(TAG, "updateDb:".concat(e.toString()));
            }
        }
    }

    /**
     * update task status.
     *
     * @param url
     * @param status the value see  STATUS_DOWNLOAD_XX for details.
     * @return
     */
    public boolean updateTaskState(String url, int status) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(url))
            return false;

        synchronized (mLocker) {
            try {
                StringBuilder buffer = new StringBuilder(256);
                buffer.append("update ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" set ").append(DBSqliteHelper.COL_STATUS);
                buffer.append(" =? ").append(" where ");
                buffer.append(DBSqliteHelper.COL_SOURCE).append(" =? ");

                db.execSQL(buffer.toString(), new Object[]{status, url});
            } catch (Exception e) {
                LogManager.e(TAG, "updateTaskState:".concat(e.toString()));
                return false;
            }
            return true;
        }
    }

    /**
     * task ended check.
     *
     * @param url 空或者 null 直接返回
     */
    protected void taskEnd(String url) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(url))
            return;

        synchronized (mLocker) {
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(256);
                buffer.append(" select ").append(DBSqliteHelper.COL_ID);
                buffer.append(" from ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" where ").append(DBSqliteHelper.COL_SOURCE);
                buffer.append(" =?");

                cursor = db.rawQuery(buffer.toString(), new String[]{url});
                while (cursor.moveToNext()) {
                    int tid = cursor.getInt(0);
                    buffer.delete(0, buffer.length());

                    buffer.append("delete from ").append(DBSqliteHelper.TABLE_TMP)
                            .append(" where ").append(DBSqliteHelper.T_COL_REFDID).append(" =?");

                    db.execSQL(buffer.toString(), new Object[]{tid});
                }

                buffer.delete(0, buffer.length());
                buffer.append("update ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" set ").append(DBSqliteHelper.COL_STATUS);
                buffer.append("=?, ").append(DBSqliteHelper.COL_TIME_END);
                buffer.append(" =? where ").append(DBSqliteHelper.COL_SOURCE);
                buffer.append(" =?");

                db.execSQL(
                        buffer.toString(),
                        new String[]{String.valueOf(STATUS_DOWNLOAD_SUCCESS),
                                String.valueOf(System.currentTimeMillis()), url}
                );
            } catch (Exception e) {
                LogManager.e(TAG, "taskEnd:".concat(e.toString()));
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
        }
    }

    /**
     * Check task exist or not.
     *
     * @param url
     * @return if exist return true. otherwise return false.
     */
    public boolean isTaskExist(String url) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(url))
            return false;

        synchronized (mLocker) {
            boolean result = false;
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(256);
                buffer.append("select count(*) from ");
                buffer.append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" where ").append(DBSqliteHelper.COL_SOURCE);
                buffer.append(" =?");

                cursor = db.rawQuery(buffer.toString(), new String[]{url});
                if (cursor.moveToNext() && (cursor.getInt(0) == 1)) {
                    result = true;
                }
            } catch (Exception e) {
                result = false;
                LogManager.e(TAG, "isTaskExist:".concat(e.toString()));
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return result;
        }
    }

    /**
     * release database resources. after released, all function will be failed before re-init.
     */
    public void recycle() {
        if (getDB() != null) {
            synchronized (mLocker) {
                SQLiteDatabase.releaseMemory();
                getDB().releaseReference();
                getDB().close();
                db = null;
            }
        }
        if (getDBHelper() != null) {
            getDBHelper().close();
            dbHelper = null;
        }
    }

    /**
     * find reference id from download task.
     *
     * @param downPath
     * @return
     */
    public int findRefId(String downPath) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(downPath))
            return INVALIDVALUE;

        synchronized (mLocker) {
            int val = INVALIDVALUE;
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder();
                buffer.append("select  ").append(DBSqliteHelper.COL_ID).append(" from ")
                        .append(DBSqliteHelper.TABLE_ALL).append(" where ")
                        .append(DBSqliteHelper.COL_SOURCE).append("=?");

                cursor = db.rawQuery(buffer.toString(), new String[]{downPath});

                if (cursor.moveToNext()) {
                    val = cursor.getInt(0);
                }
            } catch (Exception e) {
                // TODO: handle exception
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return val;
        }
    }

    /**
     * add task item to db. if task's download URL exist in database, this will be ignored.
     *
     * @param items
     */
    public void addTask(DbFeed... items) {
        if (getDB() == null || !getDB().isOpen() || items == null || items.length == 0)
            return;

        synchronized (mLocker) {
            db.beginTransaction();
            try {
                for (DbFeed item : items) {
                    if (item == null || isTaskExist(item.getDownloadUrl()))
                        continue;

                    ContentValues values = new ContentValues(20);
                    values.put(DBSqliteHelper.COL_TIME_START, item.getStartTime());
                    values.put(DBSqliteHelper.COL_TIME_END, item.getEndTime());
                    values.put(DBSqliteHelper.COL_FILENAME, item.getFileName());
                    values.put(DBSqliteHelper.COL_FILESIZE, item.getFileSize());
                    values.put(DBSqliteHelper.COL_USERDATA, item.getUserData());
                    values.put(DBSqliteHelper.COL_USERDATA2, item.getUserData2());
                    values.put(DBSqliteHelper.COL_SAVEDIR, item.getSaveDir());
                    values.put(DBSqliteHelper.COL_SOURCE, item.getDownloadUrl());
                    values.put(DBSqliteHelper.COL_SERVERS, item.getServersStr());
                    values.put(DBSqliteHelper.COL_TIME_LAST_ACCESS, item.getLastAccessTime());
                    values.put(DBSqliteHelper.COL_STATUS, item.getStatus());
                    values.put(DBSqliteHelper.COL_PRIORITY, item.getPriority());
                    values.put(DBSqliteHelper.COL_THREAD_COUNT, item.getThreadCount());
                    values.put(DBSqliteHelper.COL_IGNORE_SIZE, item.getIgnoreSize());
                    values.put(DBSqliteHelper.COL_MAX_RETRY, item.getMaxRetryTimes());
                    values.put(DBSqliteHelper.COL_LIMIT_SPEED, item.getLimitSpeed());
                    values.put(DBSqliteHelper.COL_DELETE_EXIST, item.isDeleteExistFile());
                    values.put(DBSqliteHelper.COL_RETRY_WAIT, item.getRetryWaitMills());
                    values.put(DBSqliteHelper.COL_ADD_TIME, item.getAddTime());

                    db.insert(DBSqliteHelper.TABLE_ALL, null, values);

                }
                db.setTransactionSuccessful();
            } catch (Exception e) {
                LogManager.e(getClass().getSimpleName(), "addTask:".concat(e.toString()));
            } finally {
                db.endTransaction();
            }
        }
    }

    /**
     * 获取每一个下载任务的当前信息.
     *
     * @param downPath download source.
     * @return 如果當前返回值的size=0, 表示木有找到當前的信息, 需要重新添加
     */
    public List<DbFeed> getTaskItems(String downPath) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(downPath))
            return new ArrayList<DbFeed>();

        List<DbFeed> items = new ArrayList<DbFeed>();

        synchronized (mLocker) {
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(256);
                buffer.append("select * from ").append(DBSqliteHelper.TABLE_ALL)
                        .append(" where ").append(DBSqliteHelper.COL_SOURCE).append(" =?");

                cursor = db.rawQuery(buffer.toString(), new String[]{downPath});
                items = parseItems(cursor);
            } catch (Exception e) {
                LogManager.e(TAG.concat("getTaskItems"), "downPath= ".concat(downPath).concat(":  ")
                        .concat(e.toString()));
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return items;
        }
    }

    private void findColIdx(Cursor cursor, HashMap<String, Integer> map, String colId) {
        map.put(colId, cursor.getColumnIndex(colId));
    }

    private List<DbFeed> parseItems(Cursor cursor) {
        List<DbFeed> items = new ArrayList<DbFeed>();
        if (cursor == null)
            return items;

        HashMap<String, Integer> maps = new HashMap<String, Integer>();
        findColIdx(cursor, maps, DBSqliteHelper.COL_ID);
        findColIdx(cursor, maps, DBSqliteHelper.COL_SERVERS);
        findColIdx(cursor, maps, DBSqliteHelper.COL_SOURCE);
        findColIdx(cursor, maps, DBSqliteHelper.COL_SAVEDIR);
        findColIdx(cursor, maps, DBSqliteHelper.COL_FILENAME);
        findColIdx(cursor, maps, DBSqliteHelper.COL_FILESIZE);
        findColIdx(cursor, maps, DBSqliteHelper.COL_TIME_START);
        findColIdx(cursor, maps, DBSqliteHelper.COL_TIME_END);
        findColIdx(cursor, maps, DBSqliteHelper.COL_STATUS);
        findColIdx(cursor, maps, DBSqliteHelper.COL_USERDATA);
        findColIdx(cursor, maps, DBSqliteHelper.COL_USERDATA2);
        findColIdx(cursor, maps, DBSqliteHelper.COL_PRIORITY);
        findColIdx(cursor, maps, DBSqliteHelper.COL_THREAD_COUNT);
        findColIdx(cursor, maps, DBSqliteHelper.COL_IGNORE_SIZE);
        findColIdx(cursor, maps, DBSqliteHelper.COL_MAX_RETRY);
        findColIdx(cursor, maps, DBSqliteHelper.COL_RETRY_WAIT);
        findColIdx(cursor, maps, DBSqliteHelper.COL_LIMIT_SPEED);
        findColIdx(cursor, maps, DBSqliteHelper.COL_DELETE_EXIST);
        findColIdx(cursor, maps, DBSqliteHelper.COL_ADD_TIME);

        while (cursor.moveToNext()) {
            DbFeed item = new DbFeed();

            if (maps.containsKey(DBSqliteHelper.COL_ID))
                item.setId(cursor.getInt(maps.get(DBSqliteHelper.COL_ID)));

            if (maps.containsKey(DBSqliteHelper.COL_SOURCE))
                item.setDownloadUrl(cursor.getString(maps.get(DBSqliteHelper.COL_SOURCE)));

            if (maps.containsKey(DBSqliteHelper.COL_SAVEDIR))
                item.setSaveDir(cursor.getString(maps.get(DBSqliteHelper.COL_SAVEDIR)));

            if (maps.containsKey(DBSqliteHelper.COL_FILENAME))
                item.setFileName(cursor.getString(maps.get(DBSqliteHelper.COL_FILENAME)));

            if (maps.containsKey(DBSqliteHelper.COL_USERDATA))
                item.setUserData(cursor.getString(maps.get(DBSqliteHelper.COL_USERDATA)));

            if (maps.containsKey(DBSqliteHelper.COL_USERDATA2))
                item.setUserData2(cursor.getString(maps.get(DBSqliteHelper.COL_USERDATA2)));

            if (maps.containsKey(DBSqliteHelper.COL_SERVERS))
                item.setServers(BaseFeed.parseServerToList(cursor.getString(maps.get
                        (DBSqliteHelper.COL_SERVERS))));

            if (maps.containsKey(DBSqliteHelper.COL_TIME_START))
                item.setStartTime(cursor.getLong(maps.get(DBSqliteHelper.COL_TIME_START)));

            if (maps.containsKey(DBSqliteHelper.COL_TIME_END))
                item.setEndTime(cursor.getLong(maps.get(DBSqliteHelper.COL_TIME_END)));

            if (maps.containsKey(DBSqliteHelper.COL_STATUS))
                item.setStatus(cursor.getInt(maps.get(DBSqliteHelper.COL_STATUS)));

            if (maps.containsKey(DBSqliteHelper.COL_FILESIZE))
                item.setFileSize(cursor.getLong(maps.get(DBSqliteHelper.COL_FILESIZE)));

            if (maps.containsKey(DBSqliteHelper.COL_PRIORITY))
                item.setPriority(cursor.getInt(maps.get(DBSqliteHelper.COL_PRIORITY)));

            if (maps.containsKey(DBSqliteHelper.COL_THREAD_COUNT))
                item.setThreadCount(cursor.getInt(maps.get(DBSqliteHelper.COL_THREAD_COUNT)));

            if (maps.containsKey(DBSqliteHelper.COL_IGNORE_SIZE))
                item.setIgnoreSize(cursor.getInt(maps.get(DBSqliteHelper.COL_IGNORE_SIZE)));

            if (maps.containsKey(DBSqliteHelper.COL_PRIORITY))
                item.setPriority(cursor.getInt(maps.get(DBSqliteHelper.COL_PRIORITY)));

            if (maps.containsKey(DBSqliteHelper.COL_RETRY_WAIT))
                item.setRetryWaitMills(cursor.getInt(maps.get(DBSqliteHelper.COL_RETRY_WAIT)));

            if (maps.containsKey(DBSqliteHelper.COL_MAX_RETRY))
                item.setMaxRetryTimes(cursor.getInt(maps.get(DBSqliteHelper.COL_MAX_RETRY)));

            if (maps.containsKey(DBSqliteHelper.COL_LIMIT_SPEED))
                item.setMaxRetryTimes(cursor.getInt(maps.get(DBSqliteHelper.COL_LIMIT_SPEED)));

            if (maps.containsKey(DBSqliteHelper.COL_DELETE_EXIST))
                item.setMaxRetryTimes(cursor.getInt(maps.get(DBSqliteHelper.COL_DELETE_EXIST)));

            if (maps.containsKey(DBSqliteHelper.COL_ADD_TIME))
                item.setMaxRetryTimes(cursor.getInt(maps.get(DBSqliteHelper.COL_ADD_TIME)));

            items.add(item);
        }
        return items;
    }

    public Cursor queryDownload(String select, String where, String orderBy, String groupBy,
                                int limit, String[] args) {
        if (getDB() == null || !getDB().isOpen())
            return null;

        synchronized (mLocker) {
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(256);
                buffer.append("select ");
                buffer.append((select == null || select.isEmpty()) ? "*" : select);
                buffer.append("  from ").append(DBSqliteHelper.TABLE_ALL);

                if (where != null && !where.isEmpty()) {
                    buffer.append(" where ").append(where);
                }
                if (groupBy != null && !groupBy.isEmpty()) {
                    buffer.append(" group by ").append(groupBy);
                }
                if (orderBy != null && !orderBy.isEmpty()) {
                    buffer.append(" order by ").append(orderBy);
                }

                if (limit > 0) {
                    buffer.append(" limit ").append(limit);
                }

                cursor = db.rawQuery(buffer.toString(), args);
            } catch (Exception e) {
                cursor = null;
                LogManager.e(getClass().getSimpleName(), "queryDownload:".concat(e.toString()));
            }
            return cursor;
        }
    }

    public Cursor queryDownload(String where, String orderby, String gruopBy, int limit,
                                String[] args) {

        return queryDownload(null, where, orderby, gruopBy, limit, args);
    }

    /**
     * @param where
     * @param orderby
     * @param groupBy
     * @param limit   取出记录的条数, <=0 表示全取
     * @param args
     * @return
     */
    public List<DbFeed> findDownload(String where, String orderby, String groupBy,
                                     int limit, String[] args) {

        if (getDB() == null || !getDB().isOpen())
            return new ArrayList<DbFeed>();

        synchronized (mLocker) {
            List<DbFeed> items = new ArrayList<DbFeed>();
            Cursor cursor = null;
            try {
                cursor = queryDownload(where, orderby, groupBy, limit, args);
                items = parseItems(cursor);
            } catch (Exception e) {
                LogManager.e(getClass().getSimpleName(), "findDownload:".concat(e.toString()));
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return items;
        }
    }

    /**
     * 查找下载条目,如果没有找到,则返回null
     *
     * @param url
     * @return
     */
    public DbFeed findTask(String url) {
        List<DbFeed> items = findDownload(DBSqliteHelper.COL_SOURCE.concat(" =?"),
                DBSqliteHelper.COL_ID.concat(" asc "), null, 1, new String[]{url});
        if (items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    /**
     * 根据下载任务的 url 找到数据库中存在的正在下载的记录
     *
     * @param url
     * @return
     */
    public List<DownloadingFeed> findItems(String url) {
        if (getDB() == null || !getDB().isOpen() || StringUtils.isNullOrEmpty(url))
            return new ArrayList<DownloadingFeed>();

        synchronized (mLocker) {
            List<DownloadingFeed> items = new ArrayList<DownloadingFeed>();

            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(256);
                buffer.append("select * from ").append(DBSqliteHelper.TABLE_TMP)
                        .append(" where ").append(DBSqliteHelper.T_COL_REFDID)
                        .append(" = ( select ").append(DBSqliteHelper.COL_ID);

                buffer.append(" from ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" where ").append(DBSqliteHelper.COL_SOURCE);
                buffer.append(" =?) ");

                cursor = db.rawQuery(buffer.toString(), new String[]{url});

                int[] cols = new int[cursor.getColumnCount()];
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = INVALIDVALUE;
                }

                int counter = 0;
                while (cursor.moveToNext()) {
                    DownloadingFeed item = new DownloadingFeed();
                    counter = 0;

                    if (cols[counter] == INVALIDVALUE) {
                        cols[counter] = cursor.getColumnIndex(DBSqliteHelper.T_COL_ID);
                    }
                    if (!cursor.isNull(cols[counter]))
                        item.setId(cursor.getInt(cols[counter]));
                    counter++;

                    if (cols[counter] == INVALIDVALUE) {
                        cols[counter] = cursor.getColumnIndex(DBSqliteHelper.T_COL_CURPOS);
                    }
                    if (!cursor.isNull(cols[counter]))
                        item.setCurPos(cursor.getInt(cols[counter]));
                    counter++;

                    if (cols[counter] == INVALIDVALUE) {
                        cols[counter] = cursor.getColumnIndex(DBSqliteHelper.T_COL_ENDPOS);
                    }
                    if (!cursor.isNull(cols[counter]))
                        item.setEndPos(cursor.getInt(cols[counter]));
                    counter++;

                    if (cols[counter] == INVALIDVALUE) {
                        cols[counter] = cursor.getColumnIndex(DBSqliteHelper.T_COL_REFDID);
                    }
                    if (!cursor.isNull(cols[counter]))
                        item.setRefDId(cursor.getInt(cols[counter]));
                    counter++;

                    items.add(item);
                }
                cursor.close();
                cursor = null;
            } catch (Exception e) {
                LogManager.e(getClass().getSimpleName(), "findItems:".concat(e.toString()));
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            return items;
        }
    }

    /**
     * 添加下载临时记录到数据库中. 添加完成之后会将 id 值赋值给 item中的id
     *
     * @param items
     * @return
     */
    protected boolean addTaskItems(List<DownloadingFeed> items) {
        if (getDB() == null || !getDB().isOpen() || items == null || items.isEmpty())
            return false;

        synchronized (mLocker) {
            boolean result = false;
            Cursor cursor = null;
            try {
                db.beginTransaction();

                StringBuilder buffer = new StringBuilder(256);
                for (DownloadingFeed item : items) {
                    buffer.delete(0, buffer.length());

                    buffer.append(" select count (*) from ").append(DBSqliteHelper.TABLE_TMP)
                            .append(" where ").append(DBSqliteHelper.T_COL_REFDID)
                            .append(" =? and ").append(DBSqliteHelper.T_COL_STARTPOS)
                            .append(" =?");

                    cursor = db.rawQuery(
                            buffer.toString(),
                            new String[]{String.valueOf(item.getRefDId()),
                                    String.valueOf(item.getStartPos())}
                    );

                    if (cursor != null && cursor.moveToNext()) {
                        if (cursor.getInt(0) > 0) {
                            cursor.close();
                            cursor = null;
                            continue;
                        }
                    }

                    ContentValues values = new ContentValues(4);
                    values.put(DBSqliteHelper.T_COL_REFDID, item.getRefDId());
                    values.put(DBSqliteHelper.T_COL_CURPOS, item.getCurPos());
                    values.put(DBSqliteHelper.T_COL_STARTPOS, item.getStartPos());
                    values.put(DBSqliteHelper.T_COL_ENDPOS, item.getEndPos());
                    db.insert(DBSqliteHelper.TABLE_TMP, null, values);

                    buffer.delete(0, buffer.length());

                    buffer.append(" select ").append(DBSqliteHelper.T_COL_ID);
                    buffer.append(" from ").append(DBSqliteHelper.TABLE_TMP);
                    buffer.append(" where ").append(DBSqliteHelper.T_COL_REFDID).append("=?");
                    buffer.append(" and ").append(DBSqliteHelper.T_COL_STARTPOS).append(" =?");

                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                    cursor = db.rawQuery(
                            buffer.toString(),
                            new String[]{String.valueOf(item.getRefDId()),
                                    String.valueOf(item.getStartPos())}
                    );

                    if (cursor.moveToNext()) {
                        item.setId(cursor.getInt(0));
                    }
                    cursor.close();
                    cursor = null;
                }

                db.setTransactionSuccessful();
                result = true;
            } catch (Exception e) {
                result = false;
                LogManager.e(getClass().getName(), "addTaskItems:".concat(e.toString()));
            } finally {
                db.endTransaction();
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return result;
        }
    }

    /**
     * 用来处理下载文件中的本地文件名是否已存在.
     *
     * @param saveDir
     * @param fileName
     * @return
     */
    public boolean isTaskDownloadNameExist(String saveDir, String fileName) {
        if (getDB() == null || !getDB().isOpen())
            return false;

        synchronized (mLocker) {
            boolean exist = false;
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(512);

                buffer.append("select count(*) from ").append(DBSqliteHelper.TABLE_ALL)
                        .append(" where ").append(DBSqliteHelper.COL_FILENAME)
                        .append(" =?  and ").append(DBSqliteHelper.COL_SAVEDIR).append("=?");
                if (saveDir == null) saveDir = "";
                if (fileName == null) fileName = "";

                cursor = db.rawQuery(buffer.toString(), new String[]{fileName, saveDir});

                if (cursor != null && cursor.moveToNext()) {
                    exist = (cursor.getInt(0) >= 1);
                }
            } catch (Exception e) {
                // TODO: handle exception
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return exist;
        }
    }

    public List<DbFeed> findFinishedTasks() {
        return findDownload(DBSqliteHelper.COL_STATUS.concat(" =? "),
                DBSqliteHelper.COL_TIME_END.concat(" desc, ").concat(DBSqliteHelper
                        .COL_PRIORITY).concat(" desc "),
                null, 0,
                new String[]{String.valueOf(STATUS_DOWNLOAD_SUCCESS)}
        );
    }

    public List<DbFeed> findAllTasks() {
        return findDownload(" 1=1 ",
                DBSqliteHelper.COL_TIME_END.concat(" desc,")
                        .concat(DBSqliteHelper.COL_PRIORITY).concat(" desc "),
                null, 0, null);
    }

    /**
     * because download position start with 0, so when count should plus one.
     * <p/>
     * Note:<br/>
     * 默认只查询正在下载的条目.
     * <p>Cols: downloaded size, refDId, fileName, downloadSource, downloadStatus,
     * fileSize[maybe less or equal than 0, 当文件大小找不到,但是能正常连通的时候,这时候文件大小为0.],
     * Priority, Id[TempTable's Id], userData, userData2, Servers </p>
     *
     * @param where
     * @param orderBy
     * @param groupby
     * @param limit
     * @param args
     * @return
     */
    public Cursor queryDownloading(String where, String orderBy, String groupby, int limit,
                                   String[] args) {

        if (getDB() == null || !getDB().isOpen())
            return null;

        synchronized (mLocker) {
            Cursor cursor = null;
            try {
                StringBuilder buffer = new StringBuilder(512);

                buffer.append("select ");

                buffer.append(" sum(");
                buffer.append(DBSqliteHelper.T_COL_CURPOS);
                buffer.append("-");
                buffer.append(DBSqliteHelper.T_COL_STARTPOS);
                buffer.append(") downloadSize, * ");
                buffer.append(" from (");

                buffer.append("select ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_ID).append(" sid, ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_FILENAME).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_SOURCE).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_STATUS).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_FILESIZE).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_PRIORITY).append(", ");

                buffer.append(DBSqliteHelper.TABLE_TMP).append('.');
                buffer.append(DBSqliteHelper.T_COL_ID).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_USERDATA).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_USERDATA2).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_SERVERS).append(", ");

                buffer.append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_SAVEDIR).append(", ");

                buffer.append(DBSqliteHelper.TABLE_TMP).append('.');
                buffer.append(DBSqliteHelper.T_COL_CURPOS).append(',');

                buffer.append(DBSqliteHelper.TABLE_TMP).append('.');
                buffer.append(DBSqliteHelper.T_COL_STARTPOS);

                buffer.append(" from ").append(DBSqliteHelper.TABLE_ALL);
                buffer.append(" left join ").append(DBSqliteHelper.TABLE_TMP);
                buffer.append(" on ").append(DBSqliteHelper.TABLE_ALL).append('.');
                buffer.append(DBSqliteHelper.COL_ID).append(" = ");
                buffer.append(DBSqliteHelper.TABLE_TMP).append('.');
                buffer.append(DBSqliteHelper.T_COL_REFDID);

                if (where != null && !where.isEmpty()) {
                    buffer.append(" where ").append(where);
                }
                if (groupby != null && !groupby.isEmpty()) {
                    buffer.append(" group by ").append(groupby);
                }
                if (orderBy != null && !orderBy.isEmpty()) {
                    buffer.append(" order by ").append(orderBy);
                } else {
                    buffer.append(" order by ").append(DBSqliteHelper.COL_PRIORITY).append
                            (" desc, ").append(DBSqliteHelper.COL_TIME_START).append(" asc ");
                }
                if (limit > 0) {
                    buffer.append(" limit ").append(limit);
                }
                buffer.append(")  group by  sid ");

                cursor = db.rawQuery(buffer.toString(), args);
            } catch (Exception e) {
                cursor = null;
                LogManager.e(TAG, "queryDownloading:".concat(e.toString()));
            }
            return cursor;
        }
    }

    public List<ProgressFeed> findDownloading(String where, String orderBy, String groupby,
                                              int limit, String[] args) {
        if (getDB() == null || !getDB().isOpen())
            return null;

        Cursor cursor = queryDownloading(where, orderBy, groupby, limit, args);
        synchronized (mLocker) {
            List<ProgressFeed> items = new ArrayList<ProgressFeed>();
            try {
                while (cursor.moveToNext()) {
                    ProgressFeed item = new ProgressFeed();

                    if (!cursor.isNull(0))
                        item.setDownloaded(cursor.getInt(0));

                    if (!cursor.isNull(1))
                        item.setRefDId(cursor.getInt(1));

                    if (!cursor.isNull(2))
                        item.setFileName(cursor.getString(2));

                    if (!cursor.isNull(3))
                        item.setSource(cursor.getString(3));

                    if (!cursor.isNull(4))
                        item.setStatus(cursor.getInt(4));

                    if (!cursor.isNull(5))
                        item.setTotal(cursor.getInt(5));

                    if (!cursor.isNull(6))
                        item.setPriority(cursor.getInt(6));

                    if (!cursor.isNull(7))
                        item.setId(cursor.getInt(7));

                    if (!cursor.isNull(8))
                        item.setUserData(cursor.getString(8));

                    if (!cursor.isNull(9))
                        item.setUserData2(cursor.getString(9));

                    if (!cursor.isNull(10))
                        item.setServers(cursor.getString(10));

                    items.add(item);
                }

                cursor.close();
                cursor = null;

            } catch (Exception e) {
                LogManager.e(TAG, "findDownloading:".concat(e.toString()));
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
            return items;
        }

    }

    /**
     * 查找所有没有下载成功的条目
     *
     * @return
     */
    public List<ProgressFeed> findNotSuccessItem() {

        StringBuilder buffer = new StringBuilder(128);
        buffer.append(DBSqliteHelper.TABLE_ALL).append(".").append(DBSqliteHelper.COL_STATUS)
                .append(" != ").append(STATUS_DOWNLOAD_SUCCESS);

        return findDownloading(buffer.toString(), null, DBSqliteHelper.TABLE_ALL.concat(".")
                .concat(DBSqliteHelper.COL_ID), 0, null);
    }

    private static class SingletonHolder {
        private final static DBAccess INSTANCE = new DBAccess();
    }
}
