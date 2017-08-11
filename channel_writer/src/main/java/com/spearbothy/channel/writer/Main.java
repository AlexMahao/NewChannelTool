package com.spearbothy.channel.writer;


import com.spearbothy.channel.common.ApkSignBlockUtil;
import com.spearbothy.channel.common.log.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mahao on 17-8-3.
 */
public class Main {

    static final SimpleDateFormat sdf = new SimpleDateFormat("ss.SSS");

    public static void main(String[] args) {
        Log.i("读取渠道文件");
        List<String> channelList = FileUtil.readChannel("channel_writer/channel.txt");
        List<String> errorList = new ArrayList<>();
        Map<String, String> v2ErrorList = new HashMap<>();
        Map<String, String> v1ErrorList = new HashMap<>();
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

                if (!verifyApk(channel, channelFile, v1ErrorList, v2ErrorList)) {
                    throw new Exception("签名验证失败！！！");
                }
            } catch (Exception e) {
                channelFile.delete();
                errorList.add(channel);
                Log.e(channel + "  fail !!!");
                Log.d(e.getMessage());
            }
            long dTime = System.currentTimeMillis() - startTime;
            Log.i(channel + "渠道注入完毕，耗时：" + sdf.format(dTime) + "ms");
        }
        Log.i("-----------------------------------------");
        Log.i("渠道注入失败列表：" + errorList);
        Log.i("v1签名验证失败列表：");
        for (Map.Entry<String, String> entry : v1ErrorList.entrySet()) {
            Log.e("\t" + entry.getKey() + "---" + entry.getValue());
        }
        Log.i("v2签名验证失败列表：");
        for (Map.Entry<String, String> entry : v2ErrorList.entrySet()) {
            Log.e("\t" + entry.getKey() + "---" + entry.getValue());
        }
    }

    public static boolean verifyApk(String channel, File file, Map<String, String> v1, Map<String, String> v2) {
        boolean success = true;
        try {
            VerifyUtil.verifyV2Signature(file);
            Log.i("\tv2签名验证成功:" + channel);
        } catch (Exception e) {
            v2.put(channel, e.getMessage());
            success = false;
            Log.i("\tv2签名验证失败:" + channel);
        }
        try {
            VerifyUtil.verifyV1Signature(file);
            Log.i("\tv1签名验证成功:" + channel);
        } catch (Exception e) {
            v1.put(channel, e.getMessage());
            success = false;
            Log.i("\tv1签名验证失败:" + channel);
        }
        return success;
    }
}
