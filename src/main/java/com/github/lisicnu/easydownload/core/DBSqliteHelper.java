package com.github.lisicnu.easydownload.core;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.github.lisicnu.log4android.LogManager;
import com.github.lisicnu.libDroid.util.StringUtils;

import java.util.HashMap;
import java.util.Iterator;

/**
 * <p/>
 * <p/>
 * Author: Eden Lee<p/>
 * Date: 2014/11/25 <p/>
 * Email: checkway@outlook.com <p/>
 * Version: 1.0 <p/>
 */
public class DBSqliteHelper extends SQLiteOpenHelper {

    public static final String DBNAME = ".ed";
    public static final String TABLE_ALL = "s";
    public static final String COL_ID = "_id";
    public static final String COL_SERVERS = "s1";
    public static final String COL_SOURCE = "s2";
    public static final String COL_SAVEDIR = "s3";
    public static final String COL_FILENAME = "s4";
    public static final String COL_FILESIZE = "s5";
    public static final String COL_TIME_START = "s6";
    public static final String COL_TIME_END = "s7";
    public static final String COL_STATUS = "s8";
    public static final String COL_USERDATA = "s9";
    public static final String COL_USERDATA2 = "s10";
    public static final String COL_TIME_LAST_ACCESS = "s11";
    public static final String COL_PRIORITY = "s12";
    public static final String COL_THREAD_COUNT = "s13";
    public static final String COL_IGNORE_SIZE = "s14";
    public static final String COL_MAX_RETRY = "s15";
    public static final String COL_RETRY_WAIT = "s16";
    public static final String COL_LIMIT_SPEED = "s17";
    public static final String COL_DELETE_EXIST = "s18";
    public static final String COL_ADD_TIME = "s19";
    /**
     * 临时下载表文件
     */
    public static final String TABLE_TMP = "t";
    public static final String T_COL_ID = "_id";
    public static final String T_COL_REFDID = "t1";
    public static final String T_COL_STARTPOS = "t2";
    public static final String T_COL_CURPOS = "t3";
    public static final String T_COL_ENDPOS = "t4";

    private static final int VERSION = 9;

    // version 4 加入了下载任务各种升级, 为确保后续版本正确, 直接将版本号升至5.
    // version 6 加入了下载的速度限制字段.
    // version 9 加入了删除已存在文件, 添加时间 字段.
    public DBSqliteHelper(Context context) {
        super(context, DBNAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        StringBuffer buffer = new StringBuffer(400);
        buffer.append("CREATE TABLE IF NOT EXISTS ");
        buffer.append(TABLE_ALL).append(" (");
        buffer.append(COL_ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ");
        buffer.append(COL_SERVERS).append(" nvarchar(2000), ");
        buffer.append(COL_SOURCE).append(" nvarchar(1000), ");
        buffer.append(COL_SAVEDIR).append(" nvarchar(256), ");
        buffer.append(COL_FILENAME).append(" nvarchar(256), ");
        buffer.append(COL_FILESIZE).append(" INTEGER, ");
        buffer.append(COL_TIME_START).append(" INTEGER, ");
        buffer.append(COL_TIME_END).append(" INTEGER, ");
        buffer.append(COL_STATUS).append(" INTEGER, ");
        buffer.append(COL_USERDATA).append(" nvarchar(2000), ");
        buffer.append(COL_USERDATA2).append(" nvarchar(2000), ");
        buffer.append(COL_TIME_LAST_ACCESS).append(" INTEGER default 0, ");
        buffer.append(COL_PRIORITY).append(" INTEGER default 100, ");
        buffer.append(COL_THREAD_COUNT).append(" INTEGER default 3, ");
        buffer.append(COL_IGNORE_SIZE).append(" INTEGER default 4194314, ");
        buffer.append(COL_MAX_RETRY).append(" INTEGER default 30, ");
        buffer.append(COL_RETRY_WAIT).append(" INTEGER default 3000, ");
        buffer.append(COL_LIMIT_SPEED).append(" INTEGER default 0, ");
        buffer.append(COL_ADD_TIME).append(" INTEGER default 0, ");
        buffer.append(COL_DELETE_EXIST).append("  BOOL default false) ");

        db.execSQL(buffer.toString());
        buffer.delete(0, buffer.length());

        buffer.append("CREATE TABLE IF NOT EXISTS ");
        buffer.append(TABLE_TMP).append(" (");
        buffer.append(T_COL_ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT,");
        buffer.append(T_COL_REFDID).append(" INTEGER, ");
        buffer.append(T_COL_STARTPOS).append(" INTEGER, ");
        buffer.append(T_COL_CURPOS).append(" INTEGER, ");
        buffer.append(T_COL_ENDPOS).append(" INTEGER )");

        db.execSQL(buffer.toString());

        buffer.delete(0, buffer.length());
        buffer.append("CREATE INDEX 'index1' ON ").append(TABLE_ALL);
        buffer.append("('").append(COL_SOURCE).append("')");

        db.execSQL(buffer.toString());

        createIndex(db);
    }


    void addCol(SQLiteDatabase db, String tableName, String col, String type) {
        if (db != null) {
            db.execSQL(new StringBuffer().append("alter table ").append(tableName)
                    .append(" add column ").append(col).append(" ").append(type).toString());
        }
    }

    void createIndex(SQLiteDatabase db, String index, String tableName, String indexStr) {
        if (db != null)
            db.execSQL(new StringBuffer().append("create index if not exists ").append(index)
                    .append(" on ").append(tableName).append("(").append(indexStr).append(")")
                    .toString());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion <= 1) {
            db.execSQL("drop table ".concat(TABLE_ALL));
            db.execSQL("drop table ".concat(TABLE_TMP));
            onCreate(db);
            return;
        }
        Cursor cursor = null;
        try {
            HashMap<String, String> maps = new HashMap<String, String>();
            maps.put(COL_TIME_LAST_ACCESS, " INTEGER default 0");
            maps.put(COL_PRIORITY, " INTEGER default 100");
            maps.put(COL_MAX_RETRY, " INTEGER default 30");
            maps.put(COL_RETRY_WAIT, " INTEGER default 3000");
            maps.put(COL_THREAD_COUNT, " INTEGER default 3");
            maps.put(COL_IGNORE_SIZE, " INTEGER default 4194314");
            maps.put(COL_LIMIT_SPEED, " INTEGER default 0");
            maps.put(COL_DELETE_EXIST, " BOOL default false");
            maps.put(COL_ADD_TIME, " INTEGER default 0");

            cursor = db.rawQuery("select sql from sqlite_master where type='table' and " +
                    "name=?", new String[]{TABLE_ALL});

            if (cursor != null && cursor.moveToFirst()) {
                String sql = cursor.getString(0);
                if (!StringUtils.isNullOrEmpty(sql)) {
                    Iterator<String> ite = maps.keySet().iterator();
                    while (ite.hasNext()) {
                        String st = ite.next() + " ";
                        if (sql.contains(st)) {
                            ite.remove();
                        }
                    }
                }
            }
            for (String st : maps.keySet()) {
                addCol(db, TABLE_ALL, st, maps.get(st));
            }
        } catch (Exception e) {
            LogManager.e("oncreatedb", e);
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        createIndex(db);
    }

    void createIndex(SQLiteDatabase db) {

        createIndex(db, "usedata2_index", TABLE_ALL, COL_USERDATA2);
        createIndex(db, "usedata_index", TABLE_ALL, COL_USERDATA);
        createIndex(db, "downloadsrc_index", TABLE_ALL, COL_SOURCE);

        db.execSQL("REINDEX usedata2_index");
        db.execSQL("REINDEX usedata_index");
        db.execSQL("REINDEX downloadsrc_index");
        db.execSQL("ANALYZE");
        // db.execSQL("VACUUM");
        // db.execSQL("ANALYZE");
    }
}
