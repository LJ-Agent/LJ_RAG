package com.rag.common.util;

import java.io.InputStream;
import java.security.MessageDigest;

public class Md5Util {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 流式计算文件MD5，支持大文件。
     */
    public static String md5(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }
}
