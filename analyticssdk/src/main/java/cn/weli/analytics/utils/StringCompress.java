package cn.weli.analytics.utils;

/**
 * Created by liheng on 15/12/3.
 */

import android.text.TextUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class StringCompress {

    public static String DEFAULT_CHARSET = "UTF-8";

    /***
     * 压缩字符串
     * @param source 压缩原始字符串
     * @return 返回压缩之后的字符串
     * @throws IOException
     */
    public static String compressUTF8(String source) throws IOException {
        return compress(source,DEFAULT_CHARSET);
    }

    /***
     * 字符串压缩
     * @param source 原始字符串
     * @param  sourceCharset 原始字符串的编码字符集
     * @return 返回压缩的字符串
     * @throws IOException
     */
    public static String compress(String source,String sourceCharset) throws IOException {
        if (TextUtils.isEmpty(source)) return source;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(source.getBytes(sourceCharset));
        gzip.close();
        return Base64.encode(out.toByteArray());
    }

    /***
     * 字符串解压
     * @param src 原始字符串
     * @param charset 目标字符串编码字符集
     * @return 返回解压的字符串
     * @throws IOException
     */
    public static String uncompress(String src,String charset) throws IOException {
        if(TextUtils.isEmpty(src)) return src;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.decode(src));
        GZIPInputStream gzipInputStream = new GZIPInputStream(in);
        byte[] buffer = new byte[256];
        int n;
        while ((n = gzipInputStream.read(buffer))>= 0) {
            out.write(buffer, 0, n);
        }
        return out.toString(charset);
    }

    /***
     * 解压字符串
     * @param src 原始字符串
     * @return 解压的字符串
     * @throws IOException
     */
    public static String uncompressUTF8(String src)throws IOException{
        return uncompress(src,DEFAULT_CHARSET);
    }


}
