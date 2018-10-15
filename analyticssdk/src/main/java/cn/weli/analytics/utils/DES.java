package cn.weli.analytics.utils;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;

/**
 * @author ljg
 */
public class DES {
	/** 指定加密算法为DESede */
	private static String ALGORITHM = "DESede";
	
	private String desKey = "";
	private String flag = "";
	Cipher cipher_en = null;
	Cipher cipher_de = null;



    /** 上传统计报文时使用 */
	public DES() {
		try {
			desKey = "PiadX_d(a+;@#!@3A^&EE>OP";
			// DES算法要求有一个可信任的随机数�?
			SecureRandom sr = new SecureRandom();
			// 从原始密匙数据创建一个DESKeySpec对象
			DESedeKeySpec dks = new DESedeKeySpec(desKey.getBytes("utf-8"));
			// 创建�?��密匙工厂，然后用它把DESKeySpec转换成一个SecretKey对象
			SecretKeyFactory keyFactory = SecretKeyFactory
					.getInstance(ALGORITHM);
			SecretKey key = keyFactory.generateSecret(dks);
			// Cipher对象实际完成加密操作
			cipher_en = Cipher.getInstance(ALGORITHM);
			// 用密匙初始化Cipher对象
			cipher_en.init(Cipher.ENCRYPT_MODE, key, sr);

			cipher_de = Cipher.getInstance(ALGORITHM);
			cipher_de.init(Cipher.DECRYPT_MODE, key, sr);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 加密方法
	 * 
	 * @param str
	 * @param isNeedFlag 是否需要在加密字符串后连接flag
	 * @return
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws UnsupportedEncodingException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeySpecException
	 */
	public String encrypt(String str,boolean isNeedFlag) throws IllegalBlockSizeException,
			BadPaddingException, UnsupportedEncodingException {
		// 现在，获取数据并加密
		byte data[] = str.getBytes("utf-8");
		// 正式执行加密操作
		byte[] encryptedData = cipher_en.doFinal(data);
		if (isNeedFlag) {
			return byte2hex(encryptedData) + flag;
		} else {
			return byte2hex(encryptedData);
		}
	}

	
	/**
	 * 解密方法
	 * 
	 * @param encryptedData
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeySpecException
	 * @throws UnsupportedEncodingException
	 */
	public String decrypt(String encryptedData, String encoding)
			throws IllegalBlockSizeException, BadPaddingException,
			UnsupportedEncodingException {
		// 正式执行解密操作
		byte decryptedData[] = cipher_de.doFinal(hex2byte(encryptedData));
		return new String(decryptedData, encoding);
	}

	// 二进制转字符�?
	public String byte2hex(byte[] b) {
		StringBuffer sb = new StringBuffer();
		String tmp = "";
		for (int i = 0; i < b.length; i++) {
			tmp = Integer.toHexString(b[i] & 0XFF);
			if (tmp.length() == 1) {
				sb.append("0" + tmp);
			} else {
				sb.append(tmp);
			}
		}
		return sb.toString();
	}

	// 字符串转二进�?
	public byte[] hex2byte(String str) {
		if (str == null) {
			return null;
		}
		str = str.trim();
		int len = str.length();

		if (len == 0 || len % 2 == 1) {
			return null;
		}
		byte[] b = new byte[len / 2];
		try {
			for (int i = 0; i < str.length(); i += 2) {
				b[i / 2] = (byte) Integer
						.decode("0X" + str.substring(i, i + 2)).intValue();
			}
			return b;
		} catch (Exception e) {
			return null;
		}
	}

    /////////////////////////////////////////////////////

    /** 统计数据验证时使用 */
    public DES(String keygen){
        try {
            SecureRandom sr = new SecureRandom();
            DESedeKeySpec dks = new DESedeKeySpec(keygen.getBytes("utf-8"));
            SecretKeyFactory keyFactory = SecretKeyFactory
                    .getInstance(ALGORITHM);
            SecretKey key = keyFactory.generateSecret(dks);
            cipher_en = Cipher.getInstance(ALGORITHM);
            cipher_en.init(Cipher.ENCRYPT_MODE, key, sr);

            cipher_de = Cipher.getInstance(ALGORITHM);
            cipher_de.init(Cipher.DECRYPT_MODE, key, sr);
        } catch (Exception e) {
        }
    }

    public String encrypt(int id, int v, int c, int l, int s){
        String str = Integer.toString(id) + Integer.toString(v) + Integer.toString(c) + Integer.toString(l) + Integer.toString(s);
        try {
            return MD5.md5(encrypt(str, false));
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
            return null;
        } catch (BadPaddingException e) {
            e.printStackTrace();
            return null;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

}
