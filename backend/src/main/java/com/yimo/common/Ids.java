package com.yimo.common;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 26 字符 ULID（Crockford Base32），带类型前缀。
 *
 * <p>结构：48 位毫秒时间戳 + 80 位随机数。字典序等于时间序，
 * 可以直接用于数据库排序，且无需中心协调即可生成唯一 ID。
 */
public final class Ids {

    private static final char[] CROCKFORD =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final BigInteger BASE = BigInteger.valueOf(32);
    private static final int LENGTH = 26;

    private Ids() {
    }

    public static String generate(String prefix) {
        return prefix + ulid();
    }

    public static String ulid() {
        long time = System.currentTimeMillis();

        // 16 字节 = 6 字节时间戳 + 10 字节随机数
        byte[] data = new byte[16];
        for (int i = 0; i < 6; i++) {
            data[i] = (byte) (time >>> (8 * (5 - i)));
        }
        byte[] random = new byte[10];
        ThreadLocalRandom.current().nextBytes(random);
        System.arraycopy(random, 0, data, 6, 10);

        return encode(data);
    }

    private static String encode(byte[] data) {
        BigInteger value = new BigInteger(1, data);
        char[] out = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            out[i] = CROCKFORD[value.mod(BASE).intValue()];
            value = value.shiftRight(5);
        }
        return new String(out);
    }

    public static String library() { return generate("lib_"); }
    public static String book()    { return generate("bk_"); }
    public static String chapter() { return generate("ch_"); }
    public static String entity()  { return generate("ent_"); }
    public static String review()  { return generate("rv_"); }
    public static String task()    { return generate("task_"); }
    public static String thread()  { return generate("th_"); }
}
