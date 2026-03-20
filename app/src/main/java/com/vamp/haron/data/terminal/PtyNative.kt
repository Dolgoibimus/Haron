package com.vamp.haron.data.terminal

/**
 * JNI bridge to native PTY (pseudoterminal).
 * Creates a child process with a real PTY — enables Ctrl+C, vi, nano, htop.
 */
object PtyNative {

    init {
        System.loadLibrary("haron_pty")
    }

    /**
     * Create a subprocess attached to a PTY.
     * @param cmd command to execute (e.g. "sh")
     * @param cwd working directory
     * @param rows terminal rows
     * @param cols terminal columns
     * @return [pid, masterFd] or null on failure
     */
    @JvmStatic
    external fun createSubprocess(cmd: String, cwd: String?, rows: Int, cols: Int): IntArray?

    /**
     * Resize the PTY window.
     */
    @JvmStatic
    external fun setWindowSize(fd: Int, rows: Int, cols: Int)

    /**
     * Send a signal to the process (e.g. SIGINT=2, SIGTERM=15).
     */
    @JvmStatic
    external fun sendSignal(pid: Int, signal: Int)

    /**
     * Wait for process to exit. Returns exit code.
     */
    @JvmStatic
    external fun waitFor(pid: Int): Int

    // Signal constants
    const val SIGINT = 2
    const val SIGTERM = 15
    const val SIGKILL = 9
}
