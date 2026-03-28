/**
 * JNI bridge between lwext4 (C) and Android/libaums (Java/Kotlin).
 *
 * Architecture:
 *   Kotlin Ext4UsbBridge → JNI → lwext4 C library
 *   lwext4 bread/bwrite → JNI callback → libaums UsbBlockDevice read/write
 *
 * Thread safety: single-threaded access assumed (one USB device at a time).
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#include "ext4.h"
#include "ext4_mbr.h"
#include "ext4_blockdev.h"

#define TAG "Ext4JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ---- Global state ---- */

static JavaVM *g_jvm = NULL;
static jobject g_block_device = NULL;  /* Java IBlockDevice instance */
static jmethodID g_read_method = NULL;
static jmethodID g_write_method = NULL;
static jmethodID g_get_block_size_method = NULL;
static jmethodID g_get_block_count_method = NULL;

static uint32_t g_block_size = 512;
static uint64_t g_block_count = 0;
static uint8_t *g_block_buf = NULL;
static int g_mounted = 0;

/* ---- JNI env helper ---- */

static JNIEnv* get_env(void) {
    JNIEnv *env = NULL;
    if (g_jvm) {
        (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
        if (!env) {
            (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
        }
    }
    return env;
}

/* ---- lwext4 blockdev callbacks ---- */

static int jni_bdev_open(struct ext4_blockdev *bdev) {
    LOGD("bdev_open: block_size=%u, block_count=%llu",
         (unsigned)g_block_size, (unsigned long long)g_block_count);
    return EOK;
}

static uint32_t g_bread_count = 0;

static int jni_bdev_bread(struct ext4_blockdev *bdev, void *buf,
                          uint64_t blk_id, uint32_t blk_cnt) {
    g_bread_count++;
    if (g_bread_count <= 10 || (g_bread_count % 100 == 0)) {
        LOGD("bread[%u]: blk=%llu, cnt=%u, part_offset=%llu",
             g_bread_count, (unsigned long long)blk_id, blk_cnt,
             (unsigned long long)bdev->part_offset);
    }
    JNIEnv *env = get_env();
    if (!env || !g_block_device || !g_read_method) {
        LOGE("bread: no JNI env or block device");
        return EIO;
    }

    uint32_t byte_count = blk_cnt * g_block_size;
    jbyteArray jbuf = (*env)->NewByteArray(env, byte_count);
    if (!jbuf) {
        LOGE("bread: NewByteArray failed for %u bytes", byte_count);
        return ENOMEM;
    }

    jboolean ok = (*env)->CallBooleanMethod(env, g_block_device, g_read_method,
                                            (jlong)blk_id, (jint)blk_cnt, jbuf);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, jbuf);
        LOGE("bread: Java exception at blk=%llu cnt=%u",
             (unsigned long long)blk_id, blk_cnt);
        return EIO;
    }

    if (!ok) {
        (*env)->DeleteLocalRef(env, jbuf);
        LOGE("bread: read failed at blk=%llu cnt=%u",
             (unsigned long long)blk_id, blk_cnt);
        return EIO;
    }

    (*env)->GetByteArrayRegion(env, jbuf, 0, byte_count, (jbyte*)buf);
    (*env)->DeleteLocalRef(env, jbuf);
    return EOK;
}

static int jni_bdev_bwrite(struct ext4_blockdev *bdev, const void *buf,
                           uint64_t blk_id, uint32_t blk_cnt) {
    JNIEnv *env = get_env();
    if (!env || !g_block_device || !g_write_method) {
        LOGE("bwrite: no JNI env or block device");
        return EIO;
    }

    uint32_t byte_count = blk_cnt * g_block_size;
    jbyteArray jbuf = (*env)->NewByteArray(env, byte_count);
    if (!jbuf) {
        LOGE("bwrite: NewByteArray failed for %u bytes", byte_count);
        return ENOMEM;
    }

    (*env)->SetByteArrayRegion(env, jbuf, 0, byte_count, (const jbyte*)buf);

    jboolean ok = (*env)->CallBooleanMethod(env, g_block_device, g_write_method,
                                            (jlong)blk_id, (jint)blk_cnt, jbuf);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, jbuf);
        LOGE("bwrite: Java exception at blk=%llu cnt=%u",
             (unsigned long long)blk_id, blk_cnt);
        return EIO;
    }

    (*env)->DeleteLocalRef(env, jbuf);
    return ok ? EOK : EIO;
}

static int jni_bdev_close(struct ext4_blockdev *bdev) {
    LOGD("bdev_close");
    return EOK;
}

/* ---- lwext4 blockdev structs ---- */

static struct ext4_blockdev_iface g_bdev_iface = {
    .open  = jni_bdev_open,
    .bread = jni_bdev_bread,
    .bwrite = jni_bdev_bwrite,
    .close = jni_bdev_close,
    .lock  = NULL,
    .unlock = NULL,
    .ph_bsize = 512,
    .ph_bcnt  = 0,
    .ph_bbuf  = NULL,
    .ph_refctr = 0,
    .bread_ctr = 0,
    .bwrite_ctr = 0,
    .p_user = NULL,
};

static struct ext4_blockdev g_bdev = {
    .bdif = &g_bdev_iface,
    .part_offset = 0,
    .part_size = 0,
};

/* ---- JNI_OnLoad ---- */

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGD("JNI_OnLoad");
    return JNI_VERSION_1_6;
}

/* ---- JNI exports ---- */

/**
 * Initialize the ext4 bridge with a Java IBlockDevice implementation.
 * IBlockDevice must have:
 *   boolean readBlocks(long blockId, int blockCount, byte[] buffer)
 *   boolean writeBlocks(long blockId, int blockCount, byte[] buffer)
 *   int getBlockSize()
 *   long getBlockCount()
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeInit(
    JNIEnv *env, jclass clazz, jobject blockDevice
) {
    if (g_mounted) {
        LOGE("nativeInit: already mounted, unmount first");
        return JNI_FALSE;
    }

    /* Get IBlockDevice methods */
    jclass bdClass = (*env)->GetObjectClass(env, blockDevice);
    g_read_method = (*env)->GetMethodID(env, bdClass, "readBlocks", "(JI[B)Z");
    g_write_method = (*env)->GetMethodID(env, bdClass, "writeBlocks", "(JI[B)Z");
    g_get_block_size_method = (*env)->GetMethodID(env, bdClass, "getBlockSize", "()I");
    g_get_block_count_method = (*env)->GetMethodID(env, bdClass, "getBlockCount", "()J");

    if (!g_read_method || !g_write_method ||
        !g_get_block_size_method || !g_get_block_count_method) {
        LOGE("nativeInit: IBlockDevice methods not found");
        return JNI_FALSE;
    }

    g_block_device = (*env)->NewGlobalRef(env, blockDevice);
    g_block_size = (uint32_t)(*env)->CallIntMethod(env, g_block_device, g_get_block_size_method);
    g_block_count = (uint64_t)(*env)->CallLongMethod(env, g_block_device, g_get_block_count_method);

    LOGD("nativeInit: blockSize=%u, blockCount=%llu, totalMB=%llu",
         g_block_size, (unsigned long long)g_block_count,
         (unsigned long long)(g_block_count * g_block_size / 1024 / 1024));

    /* Allocate physical block buffer */
    if (g_block_buf) free(g_block_buf);
    g_block_buf = (uint8_t*)malloc(g_block_size);
    if (!g_block_buf) {
        LOGE("nativeInit: malloc failed for block buffer");
        return JNI_FALSE;
    }

    /* Configure lwext4 blockdev */
    g_bdev_iface.ph_bsize = g_block_size;
    g_bdev_iface.ph_bcnt = g_block_count;
    g_bdev_iface.ph_bbuf = g_block_buf;

    g_bdev.part_offset = 0;
    g_bdev.part_size = g_block_count * g_block_size;

    return JNI_TRUE;
}

/**
 * Scan MBR for ext4 partitions. Returns partition index (0-3) or -1.
 */
JNIEXPORT jint JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeScanMbr(
    JNIEnv *env, jclass clazz
) {
    if (!g_block_device) {
        LOGE("scanMbr: not initialized");
        return -1;
    }

    struct ext4_mbr_bdevs bdevs;
    int rc = ext4_mbr_scan(&g_bdev, &bdevs);
    if (rc != EOK) {
        LOGE("scanMbr: ext4_mbr_scan failed, rc=%d", rc);
        return -1;
    }

    int part_count = 0;
    for (int i = 0; i < 4; i++) {
        if (bdevs.partitions[i].part_size > 0) part_count++;
    }
    LOGD("scanMbr: found %d partitions", part_count);
    for (int i = 0; i < 4; i++) {
        if (bdevs.partitions[i].part_size == 0) continue;
        uint64_t size_mb = bdevs.partitions[i].part_size / 1024 / 1024;
        LOGD("  partition[%d]: offset=%llu, size=%llu MB",
             i,
             (unsigned long long)bdevs.partitions[i].part_offset,
             (unsigned long long)size_mb);
    }

    /* Return LARGEST partition (most likely the data partition, not boot) */
    int best = -1;
    uint64_t best_size = 0;
    for (int i = 0; i < 4; i++) {
        if (bdevs.partitions[i].part_size > best_size) {
            best_size = bdevs.partitions[i].part_size;
            best = i;
        }
    }
    if (best >= 0) {
        g_bdev.part_offset = bdevs.partitions[best].part_offset;
        g_bdev.part_size = bdevs.partitions[best].part_size;
        LOGD("scanMbr: selected partition %d (offset=%llu, size=%llu MB)",
             best,
             (unsigned long long)g_bdev.part_offset,
             (unsigned long long)(g_bdev.part_size / 1024 / 1024));
        return best;
    }
    return -1;
}

/**
 * Select a specific partition by index.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeSelectPartition(
    JNIEnv *env, jclass clazz, jint index
) {
    struct ext4_mbr_bdevs bdevs;
    int rc = ext4_mbr_scan(&g_bdev, &bdevs);
    if (rc != EOK || index < 0 || index >= 4 || bdevs.partitions[index].part_size == 0) {
        return JNI_FALSE;
    }
    g_bdev.part_offset = bdevs.partitions[index].part_offset;
    g_bdev.part_size = bdevs.partitions[index].part_size;
    return JNI_TRUE;
}

/**
 * Mount the selected partition.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeMount(
    JNIEnv *env, jclass clazz, jboolean readOnly
) {
    if (g_mounted) {
        LOGE("mount: already mounted");
        return JNI_FALSE;
    }

    g_bread_count = 0;
    LOGD("mount: part_offset=%llu, part_size=%llu (%llu MB), ph_bsize=%u, ph_bcnt=%llu",
         (unsigned long long)g_bdev.part_offset,
         (unsigned long long)g_bdev.part_size,
         (unsigned long long)(g_bdev.part_size / 1024 / 1024),
         g_bdev_iface.ph_bsize,
         (unsigned long long)g_bdev_iface.ph_bcnt);

    int rc = ext4_device_register(&g_bdev, "usb");
    if (rc != EOK) {
        LOGE("mount: ext4_device_register failed, rc=%d", rc);
        return JNI_FALSE;
    }
    LOGD("mount: device registered OK");

    /* Read superblock manually to check magic before mount */
    {
        uint8_t sb_buf[2048];
        /* Superblock is at offset 1024 from partition start = 2 physical blocks from part start */
        uint64_t sb_blk = g_bdev.part_offset / g_block_size + 2;
        int sb_rc = jni_bdev_bread(&g_bdev, sb_buf, sb_blk, 2);
        if (sb_rc == EOK) {
            uint16_t magic = sb_buf[0x38] | (sb_buf[0x39] << 8);
            uint32_t block_size_log = sb_buf[0x18] | (sb_buf[0x19] << 8) | (sb_buf[0x1A] << 16) | (sb_buf[0x1B] << 24);
            LOGD("mount: superblock check: magic=0x%04X (expected 0xEF53), block_size_log=%u (block_size=%u)",
                 magic, block_size_log, 1024 << block_size_log);
            LOGD("mount: superblock first 16 bytes: %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
                 sb_buf[0], sb_buf[1], sb_buf[2], sb_buf[3],
                 sb_buf[4], sb_buf[5], sb_buf[6], sb_buf[7],
                 sb_buf[8], sb_buf[9], sb_buf[10], sb_buf[11],
                 sb_buf[12], sb_buf[13], sb_buf[14], sb_buf[15]);
        } else {
            LOGE("mount: failed to read superblock for debug, rc=%d", sb_rc);
        }
    }

    rc = ext4_mount("usb", "/usb/", readOnly ? true : false);
    if (rc != EOK) {
        LOGE("mount: ext4_mount failed, rc=%d (ENOTSUP=95, ENODEV=19, EIO=5, EINVAL=22)", rc);
        ext4_device_unregister("usb");
        return JNI_FALSE;
    }

    LOGD("mount: success (readOnly=%d)", (int)readOnly);
    g_mounted = 1;

    /* Start journal recovery if read-write */
    if (!readOnly) {
        rc = ext4_recover("/usb/");
        if (rc != EOK) {
            LOGD("mount: journal recovery rc=%d (non-fatal)", rc);
        }
        rc = ext4_journal_start("/usb/");
        if (rc != EOK) {
            LOGD("mount: journal start rc=%d (non-fatal)", rc);
        }
    }

    return JNI_TRUE;
}

/**
 * Unmount.
 */
JNIEXPORT void JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeUnmount(
    JNIEnv *env, jclass clazz
) {
    if (!g_mounted) return;

    ext4_journal_stop("/usb/");
    ext4_umount("/usb/");
    ext4_device_unregister("usb");
    g_mounted = 0;

    if (g_block_device) {
        (*env)->DeleteGlobalRef(env, g_block_device);
        g_block_device = NULL;
    }
    if (g_block_buf) {
        free(g_block_buf);
        g_block_buf = NULL;
    }

    LOGD("unmount: done");
}

/**
 * List directory entries. Returns String[] of "type|name|size|mtime"
 * type: 'd' = directory, 'f' = file, 'l' = symlink
 */
JNIEXPORT jobjectArray JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeListDir(
    JNIEnv *env, jclass clazz, jstring jpath
) {
    if (!g_mounted) {
        LOGE("listDir: not mounted!");
        return NULL;
    }

    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    LOGD("listDir: path=%s, g_mounted=%d, part_offset=%llu, part_size=%llu",
         path, g_mounted,
         (unsigned long long)g_bdev.part_offset,
         (unsigned long long)g_bdev.part_size);

    ext4_dir dir;
    int rc = ext4_dir_open(&dir, path);
    if (rc != EOK) {
        LOGE("listDir: ext4_dir_open(%s) failed, rc=%d (ENOENT=2, ENOTDIR=20, EIO=5, EACCES=13)", path, rc);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }
    LOGD("listDir: dir opened OK");

    /* First pass: count entries */
    int count = 0;
    const ext4_direntry *de;
    while ((de = ext4_dir_entry_next(&dir)) != NULL) {
        if (de->name_length == 0) continue;
        count++;
    }

    ext4_dir_close(&dir);

    /* Second pass: collect entries */
    rc = ext4_dir_open(&dir, path);
    if (rc != EOK) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, stringClass, NULL);
    int idx = 0;

    while ((de = ext4_dir_entry_next(&dir)) != NULL && idx < count) {
        if (de->name_length == 0) continue;

        char name[256];
        int nlen = de->name_length < 255 ? de->name_length : 255;
        memcpy(name, de->name, nlen);
        name[nlen] = '\0';

        /* Skip . and .. */
        if (strcmp(name, ".") == 0 || strcmp(name, "..") == 0) continue;

        char type = 'f';
        if (de->inode_type == EXT4_DE_DIR) type = 'd';
        else if (de->inode_type == EXT4_DE_SYMLINK) type = 'l';

        /* Get file size and mtime */
        char fullpath[512];
        snprintf(fullpath, sizeof(fullpath), "%s%s%s",
                 path,
                 (path[strlen(path)-1] == '/') ? "" : "/",
                 name);

        uint64_t fsize = 0;
        uint32_t mtime = 0;

        if (type == 'f') {
            ext4_file f;
            if (ext4_fopen(&f, fullpath, "rb") == EOK) {
                fsize = ext4_fsize(&f);
                ext4_fclose(&f);
            }
        }

        /* Get mtime via inode */
        {
            uint32_t mt = 0;
            ext4_mtime_get(fullpath, &mt);
            mtime = mt;
        }

        char entry[768];
        snprintf(entry, sizeof(entry), "%c|%s|%llu|%u",
                 type, name, (unsigned long long)fsize, mtime);

        jstring jentry = (*env)->NewStringUTF(env, entry);
        (*env)->SetObjectArrayElement(env, result, idx++, jentry);
        (*env)->DeleteLocalRef(env, jentry);
    }

    ext4_dir_close(&dir);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    /* Trim array if we skipped . and .. */
    if (idx < count) {
        jobjectArray trimmed = (*env)->NewObjectArray(env, idx, stringClass, NULL);
        for (int i = 0; i < idx; i++) {
            jobject el = (*env)->GetObjectArrayElement(env, result, i);
            (*env)->SetObjectArrayElement(env, trimmed, i, el);
            (*env)->DeleteLocalRef(env, el);
        }
        return trimmed;
    }

    return result;
}

/**
 * Read file contents into byte array.
 * Returns null on error. For large files use nativeReadFileChunk.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeReadFile(
    JNIEnv *env, jclass clazz, jstring jpath, jlong maxSize
) {
    if (!g_mounted) return NULL;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    ext4_file f;
    int rc = ext4_fopen(&f, path, "rb");
    if (rc != EOK) {
        LOGE("readFile: open(%s) failed, rc=%d", path, rc);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    uint64_t fsize = ext4_fsize(&f);
    if (maxSize > 0 && fsize > (uint64_t)maxSize) fsize = (uint64_t)maxSize;
    if (fsize > 64 * 1024 * 1024) {
        LOGE("readFile: file too large (%llu bytes), use chunks", (unsigned long long)fsize);
        ext4_fclose(&f);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, (jsize)fsize);
    if (!result) {
        ext4_fclose(&f);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    uint8_t *buf = (uint8_t*)malloc((size_t)fsize);
    if (!buf) {
        ext4_fclose(&f);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    size_t read_cnt = 0;
    rc = ext4_fread(&f, buf, (size_t)fsize, &read_cnt);
    ext4_fclose(&f);

    if (rc != EOK) {
        LOGE("readFile: read(%s) failed, rc=%d", path, rc);
        free(buf);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, result, 0, (jsize)read_cnt, (jbyte*)buf);
    free(buf);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    LOGD("readFile: %s, %zu bytes", path, read_cnt);
    return result;
}

/**
 * Read a chunk of a file (for large file streaming/copy).
 */
JNIEXPORT jint JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeReadFileChunk(
    JNIEnv *env, jclass clazz, jstring jpath, jlong offset,
    jbyteArray jbuf, jint bufLen
) {
    if (!g_mounted) return -1;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    ext4_file f;
    int rc = ext4_fopen(&f, path, "rb");
    if (rc != EOK) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }

    ext4_fseek(&f, (uint64_t)offset, SEEK_SET);

    uint8_t *buf = (uint8_t*)malloc(bufLen);
    if (!buf) {
        ext4_fclose(&f);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }

    size_t read_cnt = 0;
    rc = ext4_fread(&f, buf, (size_t)bufLen, &read_cnt);
    ext4_fclose(&f);

    if (rc != EOK && read_cnt == 0) {
        free(buf);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }

    (*env)->SetByteArrayRegion(env, jbuf, 0, (jsize)read_cnt, (jbyte*)buf);
    free(buf);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (jint)read_cnt;
}

/**
 * Write file from byte array.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeWriteFile(
    JNIEnv *env, jclass clazz, jstring jpath, jbyteArray jdata
) {
    if (!g_mounted) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    ext4_file f;
    int rc = ext4_fopen(&f, path, "wb");
    if (rc != EOK) {
        LOGE("writeFile: open(%s) failed, rc=%d", path, rc);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return JNI_FALSE;
    }

    jsize len = (*env)->GetArrayLength(env, jdata);
    jbyte *data = (*env)->GetByteArrayElements(env, jdata, NULL);

    size_t written = 0;
    rc = ext4_fwrite(&f, data, (size_t)len, &written);
    ext4_fclose(&f);

    (*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    LOGD("writeFile: %s, %zu bytes, rc=%d", path, written, rc);
    return (rc == EOK) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Write file with specific mode ("wb" = create/truncate, "ab" = append).
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeWriteFileMode(
    JNIEnv *env, jclass clazz, jstring jpath, jbyteArray jdata, jstring jmode
) {
    if (!g_mounted) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    const char *mode = (*env)->GetStringUTFChars(env, jmode, NULL);

    ext4_file f;
    int rc = ext4_fopen(&f, path, mode);
    if (rc != EOK) {
        LOGE("writeFileMode: open(%s, %s) failed, rc=%d", path, mode, rc);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        (*env)->ReleaseStringUTFChars(env, jmode, mode);
        return JNI_FALSE;
    }

    /* For append mode, seek to end */
    if (mode[0] == 'a') {
        ext4_fseek(&f, 0, SEEK_END);
    }

    jsize len = (*env)->GetArrayLength(env, jdata);
    jbyte *data = (*env)->GetByteArrayElements(env, jdata, NULL);

    size_t written = 0;
    rc = ext4_fwrite(&f, data, (size_t)len, &written);
    ext4_fclose(&f);

    (*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    (*env)->ReleaseStringUTFChars(env, jmode, mode);

    return (rc == EOK) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Create directory.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeMkdir(
    JNIEnv *env, jclass clazz, jstring jpath
) {
    if (!g_mounted) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    int rc = ext4_dir_mk(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (rc == EOK) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Remove file or empty directory.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeRemove(
    JNIEnv *env, jclass clazz, jstring jpath
) {
    if (!g_mounted) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    /* Try file first, then directory */
    int rc = ext4_fremove(path);
    if (rc != EOK) {
        rc = ext4_dir_rm(path);
    }

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (rc == EOK) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Rename/move file.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeRename(
    JNIEnv *env, jclass clazz, jstring joldPath, jstring jnewPath
) {
    if (!g_mounted) return JNI_FALSE;
    const char *oldPath = (*env)->GetStringUTFChars(env, joldPath, NULL);
    const char *newPath = (*env)->GetStringUTFChars(env, jnewPath, NULL);

    int rc = ext4_frename(oldPath, newPath);

    (*env)->ReleaseStringUTFChars(env, joldPath, oldPath);
    (*env)->ReleaseStringUTFChars(env, jnewPath, newPath);
    return (rc == EOK) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get file size.
 */
JNIEXPORT jlong JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeFileSize(
    JNIEnv *env, jclass clazz, jstring jpath
) {
    if (!g_mounted) return -1;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    ext4_file f;
    int rc = ext4_fopen(&f, path, "rb");
    if (rc != EOK) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return -1;
    }

    uint64_t size = ext4_fsize(&f);
    ext4_fclose(&f);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (jlong)size;
}

/**
 * Check if path is a directory.
 */
JNIEXPORT jboolean JNICALL
Java_com_vamp_haron_data_usb_ext4_Ext4Native_nativeIsDirectory(
    JNIEnv *env, jclass clazz, jstring jpath
) {
    if (!g_mounted) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    ext4_dir dir;
    int rc = ext4_dir_open(&dir, path);
    if (rc == EOK) {
        ext4_dir_close(&dir);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return JNI_TRUE;
    }

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return JNI_FALSE;
}
