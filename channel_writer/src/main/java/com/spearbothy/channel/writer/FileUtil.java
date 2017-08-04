package com.spearbothy.channel.writer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahao on 17-8-3.
 */
public class FileUtil {
    public static final String DIR = "channel";

    public static void copyFile(File source, File target) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getChannelFileName(File file, String channel) {
        System.out.println(file.getName());
        return file.getName().split("\\.")[0] + "_" + channel + ".apk";
    }

    public static List<String> readChannel(String path) {
        List<String> channelList = new ArrayList<>();
        File file = new File(path);
        // 如果文件不存在，则提示
        if (file.exists() && file.isFile()) {
            BufferedReader br = null;
            try {
                // 获取到渠道的输入流
                br = new BufferedReader(new FileReader(file));
                String line = null;
                while ((line = br.readLine()) != null) {
                    // 获取到渠道
                    channelList.add(line.trim());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (br != null) {
                        br.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            System.err.println("*********error: channel.txt文件不存在，请添加渠道文件***********");
        }
        return channelList;
    }
}
