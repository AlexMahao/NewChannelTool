package com.spearbothy.channel.common.log;

/**
 * Created by mahao on 17-8-4.
 */

public class Log {

    public static final int LEVEL_ERROR = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_DEBUG = 2;

    private static Log sLog = new Log();

    private static int sLevel = LEVEL_INFO;

    public static void i(String msg) {
        if (sLevel >= LEVEL_INFO) {
            sLog.log(msg, false);
        }
    }

    public static void d(String msg) {
        if (sLevel >= LEVEL_DEBUG) {
            sLog.log(msg, false);
        }
    }

    public static void e(String msg) {
        if (sLevel >= LEVEL_ERROR) {
            sLog.log(msg, true);
        }
    }

    public void log(String msg, boolean isError) {
        if (isError) {
            System.err.println(msg);
        } else {
            System.out.println(msg);
        }
    }

    public static void setLog(Log log) {
        sLog = log;
    }

    public static Log getLog() {
        return sLog;
    }

    public static void setLevel(int level) {
        sLevel = level;
    }
}
