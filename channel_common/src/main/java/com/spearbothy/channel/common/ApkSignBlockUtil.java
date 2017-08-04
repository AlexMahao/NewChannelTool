package com.spearbothy.channel.common;


import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by mahao on 17-8-2.
 */
public class ApkSignBlockUtil {
    /**
     * APK Signing Block Magic Code: magic “APK Sig Block 42” (16 bytes)
     * "APK Sig Block 42" : 41 50 4B 20 53 69 67 20 42 6C 6F 63 6B 20 34 32
     */
    public static final long APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L; // LITTLE_ENDIAN, High
    public static final long APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L; // LITTLE_ENDIAN, Low
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final int ZIP_EOCD_REC_MIN_SIZE = 22;
    public static final int ZIP_EOCD_REC_SIG = 0x06054b50;
    private static final int ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET = 20;
    // 摘要的最大长度，对应
    private static final int UINT16_MAX_VALUE = 0xffff;
    private static final int APK_SIG_BLOCK_MIN_SIZE = 32;
    public static final int APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;

    public static final int APK_SIGNATURE_CHANNEL_ID = 0x88888888;

    /**
     * 获取comment长度
     */
    public static long getCommentLength(final FileChannel fileChannel) throws IOException {
        // End of central directory record (EOCD)
        // Offset    Bytes     Description[23]
        // 0           4       End of central directory signature = 0x06054b50
        // 4           2       Number of this disk
        // 6           2       Disk where central directory starts
        // 8           2       Number of central directory records on this disk
        // 10          2       Total number of central directory records
        // 12          4       Size of central directory (bytes)
        // 16          4       Offset of start of central directory, relative to start of archive
        // 20          2       Comment length (n)
        // 22          n       Comment
        // For a zip with no archive comment, the
        // end-of-central-directory record will be 22 bytes long, so
        // we expect to find the EOCD marker 22 bytes from the end.
        final long archiveSize = fileChannel.size();
        if (archiveSize < ZIP_EOCD_REC_MIN_SIZE) {
            throw new IOException("APK too small for ZIP End of Central Directory (EOCD) record");
        }
        final long maxCommentLength = Math.min(archiveSize - ZIP_EOCD_REC_MIN_SIZE, UINT16_MAX_VALUE);

        final long eocdWithEmptyCommentStartPosition = archiveSize - ZIP_EOCD_REC_MIN_SIZE;

        for (int expectedCommentLength = 0; expectedCommentLength <= maxCommentLength; expectedCommentLength++) {
            final long eocdStartPos = eocdWithEmptyCommentStartPosition - expectedCommentLength;

            final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            fileChannel.position(eocdStartPos);
            fileChannel.read(byteBuffer);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            if (byteBuffer.getInt(0) == ZIP_EOCD_REC_SIG) {
                final ByteBuffer commentLengthByteBuffer = ByteBuffer.allocate(2);
                fileChannel.position(eocdStartPos + ZIP_EOCD_COMMENT_LENGTH_FIELD_OFFSET);
                fileChannel.read(commentLengthByteBuffer);
                commentLengthByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

                final int actualCommentLength = commentLengthByteBuffer.getShort(0);
                if (actualCommentLength == expectedCommentLength) {
                    return actualCommentLength;
                }
            }
        }
        throw new IOException("ZIP End of Central Directory (EOCD) record not found");
    }

    public static long findCentralDirStartOffset(final FileChannel fileChannel, final long commentLength) throws IOException {
        // End of central directory record (EOCD)
        // Offset    Bytes     Description[23]
        // 0           4       End of central directory signature = 0x06054b50
        // 4           2       Number of this disk
        // 6           2       Disk where central directory starts
        // 8           2       Number of central directory records on this disk
        // 10          2       Total number of central directory records
        // 12          4       Size of central directory (bytes)
        // 16          4       Offset of start of central directory, relative to start of archive
        // 20          2       Comment length (n)
        // 22          n       Comment
        // For a zip with no archive comment, the
        // end-of-central-directory record will be 22 bytes long, so
        // we expect to find the EOCD marker 22 bytes from the end.

        final ByteBuffer zipCentralDirectoryStart = ByteBuffer.allocate(4);
        zipCentralDirectoryStart.order(ByteOrder.LITTLE_ENDIAN);
        fileChannel.position(fileChannel.size() - commentLength - 6); // 6 = 2 (Comment length) + 4 (Offset of start of central directory, relative to start of archive)
        fileChannel.read(zipCentralDirectoryStart);
        final long centralDirStartOffset = zipCentralDirectoryStart.getInt(0);
        return centralDirStartOffset;
    }

    public static Pair<ByteBuffer, Long> findApkSigningBlock(final FileChannel fileChannel) throws IOException, SignatureNotFoundException {
        long commentLength = getCommentLength(fileChannel);
        long centralDirStartOffset = findCentralDirStartOffset(fileChannel, commentLength);
        return findApkSigningBlock(fileChannel, centralDirStartOffset);
    }

    public static Pair<ByteBuffer, Long> findApkSigningBlock(final FileChannel fileChannel, final long centralDirOffset) throws IOException, SignatureNotFoundException {

        // Find the APK Signing Block. The block immediately precedes the Central Directory.

        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes payload
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic

        if (centralDirOffset < APK_SIG_BLOCK_MIN_SIZE) {
            throw new SignatureNotFoundException(
                    "APK too small for APK Signing Block. ZIP Central Directory offset: "
                            + centralDirOffset);
        }
        // Read the magic and offset in file from the footer section of the block:
        // * uint64:   size of block
        // * 16 bytes: magic
        fileChannel.position(centralDirOffset - 24);
        final ByteBuffer footer = ByteBuffer.allocate(24);
        fileChannel.read(footer);
        footer.order(ByteOrder.LITTLE_ENDIAN);
        if ((footer.getLong(8) != APK_SIG_BLOCK_MAGIC_LO)
                || (footer.getLong(16) != APK_SIG_BLOCK_MAGIC_HI)) {
            throw new SignatureNotFoundException(
                    "No APK Signing Block before ZIP Central Directory");
        }
        // Read and compare size fields
        final long apkSigBlockSizeInFooter = footer.getLong(0);
        if ((apkSigBlockSizeInFooter < footer.capacity())
                || (apkSigBlockSizeInFooter > Integer.MAX_VALUE - 8)) {
            throw new SignatureNotFoundException(
                    "APK Signing Block size out of range: " + apkSigBlockSizeInFooter);
        }
        final int totalSize = (int) (apkSigBlockSizeInFooter + 8);
        final long apkSigBlockOffset = centralDirOffset - totalSize;
        if (apkSigBlockOffset < 0) {
            throw new SignatureNotFoundException(
                    "APK Signing Block offset out of range: " + apkSigBlockOffset);
        }
        fileChannel.position(apkSigBlockOffset);
        final ByteBuffer apkSigBlock = ByteBuffer.allocate(totalSize);
        fileChannel.read(apkSigBlock);
        apkSigBlock.order(ByteOrder.LITTLE_ENDIAN);
        final long apkSigBlockSizeInHeader = apkSigBlock.getLong(0);
        if (apkSigBlockSizeInHeader != apkSigBlockSizeInFooter) {
            throw new SignatureNotFoundException(
                    "APK Signing Block sizes in header and footer do not match: "
                            + apkSigBlockSizeInHeader + " vs " + apkSigBlockSizeInFooter);
        }
        return Pair.of(apkSigBlock, apkSigBlockOffset);
    }

    public static Map<Integer, ByteBuffer> findIdValues(final ByteBuffer apkSigningBlock) throws SignatureNotFoundException {
        checkByteOrderLittleEndian(apkSigningBlock);
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes pairs
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic
        // 切出内容部分
        final ByteBuffer pairs = sliceFromTo(apkSigningBlock, 8, apkSigningBlock.capacity() - 24);

        final Map<Integer, ByteBuffer> idValues = new LinkedHashMap<>(); // keep order

        int entryCount = 0;
        while (pairs.hasRemaining()) {
            entryCount++;
            if (pairs.remaining() < 8) {
                throw new SignatureNotFoundException(
                        "Insufficient data to read size of APK Signing Block entry #" + entryCount);
            }
            final long lenLong = pairs.getLong();
            if ((lenLong < 4) || (lenLong > Integer.MAX_VALUE)) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount
                                + " size out of range: " + lenLong);
            }
            final int len = (int) lenLong;
            final int nextEntryPos = pairs.position() + len;
            if (len > pairs.remaining()) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount + " size out of range: " + len
                                + ", available: " + pairs.remaining());
            }
            final int id = pairs.getInt();
            idValues.put(id, getByteBuffer(pairs, len - 4));

            pairs.position(nextEntryPos);
        }

        return idValues;
    }


    private static ByteBuffer sliceFromTo(final ByteBuffer source, final int start, final int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end < start: " + end + " < " + start);
        }
        final int capacity = source.capacity();
        if (end > source.capacity()) {
            throw new IllegalArgumentException("end > capacity: " + end + " > " + capacity);
        }
        final int originalLimit = source.limit();
        final int originalPosition = source.position();
        try {
            source.position(0);
            source.limit(end);
            source.position(start);
            final ByteBuffer result = source.slice();
            result.order(source.order());
            return result;
        } finally {
            source.position(0);
            source.limit(originalLimit);
            source.position(originalPosition);
        }
    }

    private static void checkByteOrderLittleEndian(final ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer byte order must be little endian");
        }
    }

    private static ByteBuffer getByteBuffer(final ByteBuffer source, final int size)
            throws BufferUnderflowException {
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size);
        }
        final int originalLimit = source.limit();
        final int position = source.position();
        final int limit = position + size;
        if ((limit < position) || (limit > originalLimit)) {
            throw new BufferUnderflowException();
        }
        source.limit(limit);
        try {
            final ByteBuffer result = source.slice();
            result.order(source.order());
            source.position(limit);
            return result;
        } finally {
            source.limit(originalLimit);
        }
    }
}
