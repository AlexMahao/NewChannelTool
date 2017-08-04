package com.spearbothy.channel.writer;


import com.spearbothy.channel.common.ApkSignBlockUtil;
import com.spearbothy.channel.common.ApkSigningBlock;
import com.spearbothy.channel.common.ApkSigningPayload;
import com.spearbothy.channel.common.Pair;
import com.spearbothy.channel.common.SignatureNotFoundException;
import com.spearbothy.channel.common.log.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Set;

public final class ApkSignBlockWriter {

    public static void put(File apkFile, int id, String string) throws IOException, SignatureNotFoundException {

        RandomAccessFile fIn = null;
        FileChannel fileChannel = null;
        fIn = new RandomAccessFile(apkFile, "rw");
        fileChannel = fIn.getChannel();
        final long commentLength = ApkSignBlockUtil.getCommentLength(fileChannel);
        Log.d("EOCD: comment length: " + commentLength);
        final long centralDirStartOffset = ApkSignBlockUtil.findCentralDirStartOffset(fileChannel, commentLength);
        Log.d("EOCD:central directory start offset:" + centralDirStartOffset);
        Pair<ByteBuffer, Long> apkSigningBlockAndOffset = ApkSignBlockUtil.findApkSigningBlock(fileChannel, centralDirStartOffset);

        ByteBuffer apkSigningBlock = apkSigningBlockAndOffset.getFirst();
        long apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();
        Log.d("apk signing block  start offset:" + apkSigningBlockOffset);
        // 获取签名快中对应的id-value
        final Map<Integer, ByteBuffer> originIdValues = ApkSignBlockUtil.findIdValues(apkSigningBlock);
        Log.d("apk sign block v2 is exist, next ");
        final ByteBuffer apkSignatureSchemeV2Block = originIdValues.get(ApkSignBlockUtil.APK_SIGNATURE_SCHEME_V2_BLOCK_ID);
        if (apkSignatureSchemeV2Block == null) {
            throw new IOException(
                    "No APK Signature Scheme v2 block in APK Signing Block");
        }
        if (string != null) {
            Log.d("添加自定义id-value:" + id);
            originIdValues.put(id, string2ByteBuffer(string));
        }
        Log.d("构造新的apkSignBlock");
        final ApkSigningBlock newApkSigningBlock = new ApkSigningBlock();
        final Set<Map.Entry<Integer, ByteBuffer>> entrySet = originIdValues.entrySet();
        for (Map.Entry<Integer, ByteBuffer> entry : entrySet) {
            final ApkSigningPayload payload = new ApkSigningPayload(entry.getKey(), entry.getValue());
            newApkSigningBlock.addPayload(payload);
        }

        if (apkSigningBlockOffset != 0 && centralDirStartOffset != 0) {
            Log.d("读取central directory");
            fIn.seek(centralDirStartOffset);
            byte[] centralDirBytes = new byte[(int) (fileChannel.size() - centralDirStartOffset)];
            fIn.read(centralDirBytes);
            Log.d("移动到apk sign Bloc offset");
            fileChannel.position(apkSigningBlockOffset);
            Log.d("写入apk sign block");
            final long length = newApkSigningBlock.writeApkSigningBlock(fIn);
            Log.d("重新写入central directory");
            fIn.write(centralDirBytes);
            fIn.setLength(fIn.getFilePointer());

            Log.d("reset central directory start offset");
            // 6 = 2(Comment length) + 4 (Offset of start of central directory, relative to start of archive)
            fIn.seek(fileChannel.size() - commentLength - 6);
            final ByteBuffer temp = ByteBuffer.allocate(4);
            temp.order(ByteOrder.LITTLE_ENDIAN);
            int offsetStart = (int) (centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset));
            temp.putInt(offsetStart);
            Log.d("update central directory offset = " + offsetStart);
            // 8 = size of block in bytes (excluding this field) (uint64)
            temp.flip();
            fIn.write(temp.array());
            Log.d(" apk sign block writer ---- over");
        }
    }

    static ByteBuffer string2ByteBuffer(String string) throws UnsupportedEncodingException {
        final byte[] bytes = string.getBytes(ApkSignBlockUtil.DEFAULT_CHARSET);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes, 0, bytes.length);
        byteBuffer.flip();
        return byteBuffer;
    }
}
