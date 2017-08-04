package com.spearbothy.channel.writer;


import com.spearbothy.channel.common.ApkSignBlockUtil;
import com.spearbothy.channel.common.SignatureNotFoundException;
import com.spearbothy.channel.common.log.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahao on 17-8-3.
 */
public class Main {

    static final SimpleDateFormat sdf = new SimpleDateFormat("ss.SSS");

    public static void main(String[] args) {
        Log.i("读取渠道文件");
        List<String> channelList = FileUtil.readChannel("channel_writer/channel.txt");
        List<String> errorList = new ArrayList<>();
        Log.i("\t渠道列表：" + channelList);
        File apkFile = new File("channel_writer/app-release.apk");
        Log.d("获取apk path:" + apkFile.exists());
        if (!apkFile.exists()) {
            Log.e(apkFile + " not exist!!");
            return;
        }
        File dirFile = new File(FileUtil.DIR);
        Log.d("渠道文件目录：" + dirFile.getAbsolutePath());
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        Log.i("-----------------------------------------");
        File channelFile;
        for (String channel : channelList) {
            Log.i("开始注入渠道：" + channel);
            long startTime = System.currentTimeMillis();
            channelFile = new File(dirFile, FileUtil.getChannelFileName(apkFile, channel));
            Log.i("\t拷贝文件：from--" + apkFile.getAbsolutePath() + "---to--" + channelFile.getAbsolutePath());
            FileUtil.copyFile(apkFile, channelFile);
            try {
                Log.i("\t写入渠道信息：id=" + ApkSignBlockUtil.APK_SIGNATURE_CHANNEL_ID + ",channel=" + channel);
                ApkSignBlockWriter.put(channelFile, ApkSignBlockUtil.APK_SIGNATURE_CHANNEL_ID, channel);
            } catch (SignatureNotFoundException e) {
                errorList.add(channel);
                Log.e(channel + "  fail !!!");
                Log.d(e.getMessage());
            } catch (IOException e) {
                errorList.add(channel);
                Log.e(channel + "  fail !!!");
                Log.d(e.getMessage());
            }
            long dTime = System.currentTimeMillis() - startTime;
            Log.i(channel + "渠道注入完毕，耗时：" + sdf.format(dTime) + "ms");
        }
        Log.i("-----------------------------------------");
        Log.i("渠道注入失败列表：" + errorList);
    }
}
