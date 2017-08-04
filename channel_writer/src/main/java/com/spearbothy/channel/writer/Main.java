package com.spearbothy.channel.writer;


import com.spearbothy.channel.common.ApkSignBlockUtil;
import com.spearbothy.channel.common.SignatureNotFoundException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahao on 17-8-3.
 */
public class Main {

    public static void main(String[] args) {
        List<String> channelList = FileUtil.readChannel("channel_writer/channel.txt");
        List<String> errorList = new ArrayList<>();
        System.out.println(channelList);
        File apkFile = new File("channel_writer/app-release.apk");

        if (!apkFile.exists()) {
            return;
        }
        File dirFile = new File(FileUtil.DIR);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File channelFile;
        for (String channel : channelList) {
            System.out.println("开始注入渠道:" + channel);
            System.out.println(channel + "--start:" + System.currentTimeMillis());
            channelFile = new File(dirFile, FileUtil.getChannelFileName(apkFile, channel));
            FileUtil.copyFile(apkFile, channelFile);
            try {
                ApkSignBlockWriter.put(channelFile, ApkSignBlockUtil.APK_SIGNATURE_CHANNEL_ID, channel);
            } catch (SignatureNotFoundException e) {
                errorList.add(channel);
                e.printStackTrace();
            } catch (IOException e) {
                errorList.add(channel);
                e.printStackTrace();
            }
            System.out.println(channel + "--end:" + System.currentTimeMillis());
        }
        System.out.println("渠道失败文件：" + errorList);
    }
}
