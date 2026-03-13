package com.vamp.haron.data.shizuku;

import com.vamp.haron.data.shizuku.ShizukuFileEntry;

interface IShizukuFileService {
    List<ShizukuFileEntry> listFiles(String path);
    boolean exists(String path);
    void destroy();
}
