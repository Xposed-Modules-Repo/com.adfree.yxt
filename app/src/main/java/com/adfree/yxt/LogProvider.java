package com.adfree.yxt;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** 接收钩子进程写入的拦截事件,存进模块自己的目录。exported=true 供易校通进程调用。 */
public class LogProvider extends ContentProvider {
    public boolean onCreate() { return true; }

    public Uri insert(Uri uri, ContentValues values) {
        try {
            if (values != null && getContext() != null) {
                String type = values.getAsString("type");
                if (type != null) Store.append(getContext().getApplicationContext(), type);
            }
        } catch (Throwable ignore) {}
        return uri;
    }

    public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
    public int delete(Uri u, String s, String[] sa) { return 0; }
    public String getType(Uri u) { return null; }
}
