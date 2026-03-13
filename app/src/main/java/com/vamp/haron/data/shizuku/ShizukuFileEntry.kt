package com.vamp.haron.data.shizuku

import android.os.Parcel
import android.os.Parcelable
import com.vamp.haron.domain.model.FileEntry

data class ShizukuFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int
) : Parcelable {

    constructor(parcel: Parcel) : this(
        name = parcel.readString().orEmpty(),
        path = parcel.readString().orEmpty(),
        isDirectory = parcel.readInt() != 0,
        size = parcel.readLong(),
        lastModified = parcel.readLong(),
        childCount = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(path)
        parcel.writeInt(if (isDirectory) 1 else 0)
        parcel.writeLong(size)
        parcel.writeLong(lastModified)
        parcel.writeInt(childCount)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShizukuFileEntry> {
        override fun createFromParcel(parcel: Parcel): ShizukuFileEntry = ShizukuFileEntry(parcel)
        override fun newArray(size: Int): Array<ShizukuFileEntry?> = arrayOfNulls(size)
    }
}

fun ShizukuFileEntry.toFileEntry(): FileEntry {
    val ext = if (!isDirectory && '.' in name) name.substringAfterLast('.') else ""
    return FileEntry(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        extension = ext,
        isHidden = name.startsWith("."),
        childCount = childCount
    )
}
