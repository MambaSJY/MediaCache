package com.mamba.sam.db

import android.text.TextUtils
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @author Frank Shao
 * @created 09/03/2021
 * Description:
 */

@Entity
data class MediaMeta(
    @PrimaryKey val url: String,
    val length: Long = -1,
    val mime: String?
) {
    fun unKnownLength() = length < 0
    fun unKnownMine() = TextUtils.isEmpty(mime)
    fun unKnown() = unKnownLength() || unKnownMine()
}
