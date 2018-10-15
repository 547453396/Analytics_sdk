package cn.weli.analytics.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Will on 2014/5/14.
 */
public class MD5 {

    public static String DEFAULT_YEK = "YEK_3@F+)jd%sUesfC^>dK.dlc";

    public static String md5(String s) throws NoSuchAlgorithmException {
        if (s == null)
            return null;
        StringBuffer stringbuffer;
        byte abyte0[] = s.getBytes();
        MessageDigest messagedigest = MessageDigest.getInstance("MD5");
        messagedigest.reset();
        messagedigest.update(abyte0);
        byte abyte1[] = messagedigest.digest();
        stringbuffer = new StringBuffer();
        for (int i = 0; i < abyte1.length; i++)
            stringbuffer.append(String.format("%02X",
                    new Object[] { Byte.valueOf(abyte1[i]) }));

        return stringbuffer.toString();

    }

    /**
     * 获取md5字符串
     */
    public static String getMD5(byte[] input) {
        return bytesToHexString(MD5(input));
    }

    /**
     * 计算给定 byte [] 串的 MD5
     */
    public static byte[] MD5(byte[] input) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (md != null) {
            md.update(input);
            return md.digest();
        } else
            return null;
    }

    /**
     * Converts a byte array into a String hexidecimal characters
     * <p/>
     * null returns null
     */
    private static String bytesToHexString(byte[] bytes) {
        if (bytes == null)
            return null;
        String table = "0123456789abcdef";
        StringBuilder ret = new StringBuilder(2 * bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            int b;
            b = 0x0f & (bytes[i] >> 4);
            ret.append(table.charAt(b));
            b = 0x0f & bytes[i];
            ret.append(table.charAt(b));
        }
        return ret.toString();
    }

}
