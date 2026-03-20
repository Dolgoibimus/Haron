/*
 * Minimal PTY (pseudoterminal) JNI for Android.
 * Creates a child process attached to a PTY — enables Ctrl+C, vi, nano, htop.
 * Based on forkpty() from util.h.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <termios.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>

/* Android doesn't have forkpty in older NDK, so implement manually */
static int my_forkpty(int *master, struct winsize *ws) {
    int ptm = open("/dev/ptmx", O_RDWR);
    if (ptm < 0) return -1;

    if (grantpt(ptm) < 0 || unlockpt(ptm) < 0) {
        close(ptm);
        return -1;
    }

    char pts_name[64];
    if (ptsname_r(ptm, pts_name, sizeof(pts_name)) != 0) {
        close(ptm);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(ptm);
        return -1;
    }

    if (pid == 0) {
        /* Child */
        close(ptm);
        setsid();

        int pts = open(pts_name, O_RDWR);
        if (pts < 0) _exit(1);

        if (ws) ioctl(pts, TIOCSWINSZ, ws);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);
        if (pts > 2) close(pts);

        return 0; /* child returns 0 */
    }

    /* Parent */
    *master = ptm;
    return pid;
}

JNIEXPORT jintArray JNICALL
Java_com_vamp_haron_data_terminal_PtyNative_createSubprocess(
    JNIEnv *env, jclass clazz,
    jstring cmd, jstring cwd,
    jint rows, jint cols
) {
    const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char *cwd_str = cwd ? (*env)->GetStringUTFChars(env, cwd, NULL) : NULL;

    struct winsize ws = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) cols,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };

    int master_fd = -1;
    pid_t pid = my_forkpty(&master_fd, &ws);

    if (pid < 0) {
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
        if (cwd_str) (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);
        return NULL;
    }

    if (pid == 0) {
        /* Child process */
        if (cwd_str) chdir(cwd_str);

        /* Set environment */
        setenv("TERM", "xterm-256color", 1);
        setenv("HOME", "/data/data/com.vamp.haron/files", 1);
        char *storage = getenv("EXTERNAL_STORAGE");
        if (storage) setenv("HOME", storage, 1);

        /* Execute shell */
        execlp(cmd_str, cmd_str, "-l", (char *) NULL);
        _exit(127); /* exec failed */
    }

    /* Parent */
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
    if (cwd_str) (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);

    /* Return [pid, masterFd] */
    jintArray result = (*env)->NewIntArray(env, 2);
    jint values[2] = { (jint) pid, (jint) master_fd };
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT void JNICALL
Java_com_vamp_haron_data_terminal_PtyNative_setWindowSize(
    JNIEnv *env, jclass clazz,
    jint fd, jint rows, jint cols
) {
    struct winsize ws = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) cols,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_vamp_haron_data_terminal_PtyNative_sendSignal(
    JNIEnv *env, jclass clazz,
    jint pid, jint signal
) {
    kill((pid_t) pid, signal);
}

JNIEXPORT jint JNICALL
Java_com_vamp_haron_data_terminal_PtyNative_waitFor(
    JNIEnv *env, jclass clazz,
    jint pid
) {
    int status;
    waitpid((pid_t) pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}
