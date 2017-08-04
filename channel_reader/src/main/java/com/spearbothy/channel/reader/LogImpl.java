package com.spearbothy.channel.reader;

import com.spearbothy.channel.common.log.Log;

/**
 * Created by mahao on 17-8-4.
 */

public class LogImpl extends Log {

    private final String TAG = "channel_tool";

    @Override
    public void log(String msg, boolean isError) {
        if (isError) {
            android.util.Log.e(TAG, msg);
        } else {
            android.util.Log.i(TAG, msg);
        }
    }

    public static void init() {
        if (!(getLog() instanceof LogImpl)) {
            setLog(new LogImpl());
        }
    }
}
