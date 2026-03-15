package com.vamp.haron.data.shizuku;

import com.vamp.haron.data.shizuku.ShizukuFileEntry;

interface IShizukuFileService {
    List<ShizukuFileEntry> listFiles(String path);
    boolean exists(String path);
    boolean isDirectory(String path);
    boolean copyFile(String srcPath, String destPath, boolean overwrite);
    boolean copyDirectoryRecursively(String srcPath, String destPath, boolean overwrite);
    boolean deleteRecursively(String path);
    boolean renameTo(String srcPath, String destPath);
    boolean mkdirs(String path);
    long calculateDirSize(String path);
    void destroy();
}
