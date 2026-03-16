# ===== Haron ProGuard / R8 Rules =====

# ---------- Debug: сохранить номера строк в стек-трейсах ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Room (Haron) ----------
-keep class com.vamp.haron.data.db.entity.** { *; }
-keep interface com.vamp.haron.data.db.dao.** { *; }

# ---------- Room (ecosystem-core) ----------
-keep class com.vamp.core.model.** { *; }
-keep interface com.vamp.core.db.dao.** { *; }
-keep class com.vamp.core.db.EcosystemDatabase { *; }

# ---------- Domain models (data classes used across layers) ----------
-keep class com.vamp.haron.domain.model.** { *; }

# ---------- Reflection: StorageVolume.getPath() ----------
-keepclassmembers class android.os.storage.StorageVolume {
    public java.lang.String getPath();
    public java.io.File getDirectory();
}

# ---------- VLC ----------
-keep class org.videolan.** { *; }

# ---------- FFmpegKit ----------
-keep class com.arthenica.** { *; }
-keep class com.moizhassan.** { *; }

# ---------- 7-Zip-JBinding (native JNI) ----------
-keep class net.sf.sevenzipjbinding.** { *; }

# ---------- libaums (USB) ----------
-keep class me.jahnen.libaums.** { *; }

# ---------- JSch (SSH — reflection for crypto) ----------
-keep class com.jcraft.jsch.** { *; }

# ---------- smbj + dcerpc (SMB — reflection + BouncyCastle) ----------
-keep class com.hierynomus.** { *; }
-keep class com.rapid7.** { *; }

# ---------- BouncyCastle (crypto provider, reflection) ----------
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---------- Apache POI (.doc support — reflection) ----------
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# ---------- PDFBox (PDF text extraction — reflection) ----------
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ---------- Apache Commons (all — compress, io, codec, collections, math; POI depends on them) ----------
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# ---------- junrar ----------
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**

# ---------- zip4j ----------
-keep class net.lingala.zip4j.** { *; }

# ---------- Tesseract4Android (native JNI) ----------
-keep class cz.adaptech.tesseract4android.** { *; }

# ---------- Ktor (HTTP server — coroutines + reflection) ----------
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ---------- SLF4J (logging facade) ----------
-dontwarn org.slf4j.**

# ---------- Shizuku ----------
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class com.vamp.haron.data.shizuku.** { *; }

# ---------- Google Cast SDK ----------
-keep class com.google.android.gms.cast.** { *; }

# ---------- ML Kit ----------
-keep class com.google.mlkit.** { *; }

# ---------- ZXing (QR) ----------
-keep class com.google.zxing.** { *; }

# ---------- Google Drive API ----------
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.auth.**

# ---------- Dropbox SDK ----------
-keep class com.dropbox.core.** { *; }
-dontwarn com.dropbox.core.**

# ---------- OneDrive (direct HTTP, no SDK) ----------
# No keep rules needed — uses standard HttpURLConnection + org.json

# ---------- OkHttp (used by cloud SDKs) ----------
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- Glance (AppWidget) ----------
-keep class com.vamp.haron.presentation.widget.** { *; }

# ---------- javax.annotation (suppress warnings) ----------
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn javax.servlet.**
-dontwarn javax.xml.**

# ---------- kotlin-reflect / coroutines warnings ----------
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn kotlinx.coroutines.**

# ---------- Missing classes (desktop/server APIs not on Android) ----------
# Auto-generated from R8 missing_rules.txt
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn com.sun.jna.Memory
-dontwarn com.sun.jna.Pointer
-dontwarn com.sun.jna.platform.win32.BaseTSD$ULONG_PTR
-dontwarn com.sun.jna.platform.win32.Kernel32
-dontwarn com.sun.jna.platform.win32.User32
-dontwarn com.sun.jna.platform.win32.WinBase$SECURITY_ATTRIBUTES
-dontwarn com.sun.jna.platform.win32.WinBase
-dontwarn com.sun.jna.platform.win32.WinDef$HWND
-dontwarn com.sun.jna.platform.win32.WinDef$LPARAM
-dontwarn com.sun.jna.platform.win32.WinDef$LRESULT
-dontwarn com.sun.jna.platform.win32.WinDef$WPARAM
-dontwarn com.sun.jna.platform.win32.WinNT$HANDLE
-dontwarn com.sun.jna.platform.win32.WinUser$COPYDATASTRUCT
-dontwarn java.rmi.MarshalException
-dontwarn java.rmi.UnmarshalException
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
# mbassador event bus (used by smbj internally — reflection for handler constructors)
-keep class net.engio.mbassy.** { *; }
-dontwarn net.engio.mbassy.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.MessageProp
-dontwarn org.ietf.jgss.Oid
-dontwarn org.newsclub.net.unix.AFUNIXServerSocketChannel
-dontwarn org.newsclub.net.unix.AFUNIXSocketAddress
-dontwarn org.newsclub.net.unix.AFUNIXSocketChannel
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference

# ---------- JAudiotagger (audio tag writing) ----------
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ---------- libtorrent4j (torrent download) ----------
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
