package com.adfree.yxt

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/** 接收钩子进程写入的拦截事件,存进模块自己的目录。exported=true 供乐校通进程调用。 */
class LogProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        runCatching {
            val type = values?.getAsString("type")
            val ctx = context
            if (type != null && ctx != null) Store.append(ctx.applicationContext, type)
        }
        return uri
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null
}
