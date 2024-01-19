/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */
package codechicken.mixin.scala;

/**
 * This class is borrowed from scala-reflect found at {@code scala.reflect.internal.pickling.ByteCodecs}.
 * <p>
 * It is unmodified, except for its conversion from Scala to Java and minor syntactic cleanups.
 * <p>
 * Created by covers1624 on 18/1/24.
 */
public class ByteCodecs {

    /**
     * Increment each element by 1, then map 0x00 to 0xC0 0x80. Returns a fresh array.
     */
    public static byte[] avoidZero(byte[] src) {
        int srcLen = src.length;
        int count = 0;
        for (byte b : src) {
            if (b == 0x7F) {
                count++;
            }
        }
        byte[] dst = new byte[srcLen + count];
        int j = 0;
        for (byte in : src) {
            if (in == 0x7F) {
                dst[j++] = (byte) 0xC0;
                dst[j++] = (byte) 0x80;
            } else {
                dst[j++] = (byte) (in + 1);
            }
        }
        return dst;
    }

    /**
     * Map 0xC0 0x80 to 0x00, then subtract 1 from each element. In-place.
     */
    public static int regenerateZero(byte[] src) {
        int srcLen = src.length;
        int j = 0;
        for (int i = 0; i < srcLen; i++, j++) {
            int in = src[i] & 0xFF;
            if (in == 0xC0 && (src[i + 1] & 0xFF) == 0x80) {
                src[j] = 0x7F;
                i++;
            } else if (in == 0) {
                src[j] = 0x7F;
            } else {
                src[j] = (byte) (in - 1);
            }
        }
        return j;
    }

    /** Returns a new array */
    public static byte[] encode8to7(byte[] src) {
        int srclen = src.length;
        int dstlen = (srclen * 8 + 6) / 7;
        byte[] dst = new byte[dstlen];
        int i = 0;
        int j = 0;
        while (i + 6 < srclen) {
            int in = src[i] & 0xFF;
            dst[j] = (byte) (in & 0x7F);
            int out = in >>> 7;
            in = src[i + 1] & 0xFF;
            dst[j + 1] = (byte) (out | (in << 1) & 0x7F);
            out = in >>> 6;
            in = src[i + 2] & 0xFF;
            dst[j + 2] = (byte) (out | (in << 2) & 0x7F);
            out = in >>> 5;
            in = src[i + 3] & 0xFF;
            dst[j + 3] = (byte) (out | (in << 3) & 0x7F);
            out = in >>> 4;
            in = src[i + 4] & 0xFF;
            dst[j + 4] = (byte) (out | (in << 4) & 0x7F);
            out = in >>> 3;
            in = src[i + 5] & 0xFF;
            dst[j + 5] = (byte) (out | (in << 5) & 0x7F);
            out = in >>> 2;
            in = src[i + 6] & 0xFF;
            dst[j + 6] = (byte) (out | (in << 6) & 0x7F);
            out = in >>> 1;
            dst[j + 7] = (byte) out;
            i += 7;
            j += 8;
        }
        if (i < srclen) {
            int in = src[i] & 0xFF;
            dst[j] = (byte) (in & 0x7F);
            j += 1;
            int out = in >>> 7;
            if (i + 1 < srclen) {
                in = src[i + 1] & 0xFF;
                dst[j] = (byte) (out | (in << 1) & 0x7F);
                j += 1;
                out = in >>> 6;
                if (i + 2 < srclen) {
                    in = src[i + 2] & 0xFF;
                    dst[j] = (byte) (out | (in << 2) & 0x7F);
                    j += 1;
                    out = in >>> 5;
                    if (i + 3 < srclen) {
                        in = src[i + 3] & 0xFF;
                        dst[j] = (byte) (out | (in << 3) & 0x7F);
                        j += 1;
                        out = in >>> 4;
                        if (i + 4 < srclen) {
                            in = src[i + 4] & 0xFF;
                            dst[j] = (byte) (out | (in << 4) & 0x7F);
                            j += 1;
                            out = in >>> 3;
                            if (i + 5 < srclen) {
                                in = src[i + 5] & 0xFF;
                                dst[j] = (byte) (out | (in << 5) & 0x7F);
                                j += 1;
                                out = in >>> 2;
                            }
                        }
                    }
                }
            }
            if (j < dstlen) {
                dst[j] = (byte) out;
            }
        }
        return dst;
    }

    /** In-place */
    public static int decode7to8(byte[] src, int srclen) {
        int i = 0;
        int j = 0;
        int dstlen = (srclen * 7 + 7) / 8;
        while (i + 7 < srclen) {
            int out = src[i];
            byte in = src[i + 1];
            src[j] = (byte) (out | (in & 0x01) << 7);
            out = in >>> 1;
            in = src[i + 2];
            src[j + 1] = (byte) (out | (in & 0x03) << 6);
            out = in >>> 2;
            in = src[i + 3];
            src[j + 2] = (byte) (out | (in & 0x07) << 5);
            out = in >>> 3;
            in = src[i + 4];
            src[j + 3] = (byte) (out | (in & 0x0f) << 4);
            out = in >>> 4;
            in = src[i + 5];
            src[j + 4] = (byte) (out | (in & 0x1f) << 3);
            out = in >>> 5;
            in = src[i + 6];
            src[j + 5] = (byte) (out | (in & 0x3f) << 2);
            out = in >>> 6;
            in = src[i + 7];
            src[j + 6] = (byte) (out | in << 1);
            i += 8;
            j += 7;
        }
        if (i < srclen) {
            int out = src[i];
            if (i + 1 < srclen) {
                byte in = src[i + 1];
                src[j] = (byte) (out | (in & 0x01) << 7);
                j += 1;
                out = in >>> 1;
                if (i + 2 < srclen) {
                    in = src[i + 2];
                    src[j] = (byte) (out | (in & 0x03) << 6);
                    j += 1;
                    out = in >>> 2;
                    if (i + 3 < srclen) {
                        in = src[i + 3];
                        src[j] = (byte) (out | (in & 0x07) << 5);
                        j += 1;
                        out = in >>> 3;
                        if (i + 4 < srclen) {
                            in = src[i + 4];
                            src[j] = (byte) (out | (in & 0x0f) << 4);
                            j += 1;
                            out = in >>> 4;
                            if (i + 5 < srclen) {
                                in = src[i + 5];
                                src[j] = (byte) (out | (in & 0x1f) << 3);
                                j += 1;
                                out = in >>> 5;
                                if (i + 6 < srclen) {
                                    in = src[i + 6];
                                    src[j] = (byte) (out | (in & 0x3f) << 2);
                                    j += 1;
                                    out = in >>> 6;
                                }
                            }
                        }
                    }
                }
            }
            if (j < dstlen) {
                src[j] = (byte) out;
            }
        }
        return dstlen;
    }

    public static byte[] encode(byte[] xs) {
        return avoidZero(encode8to7(xs));
    }

    /**
     * Destructively decodes array xs and returns the length of the decoded array.
     * <p>
     * Sometimes returns (length+1) of the decoded array. Example:
     * <p>
     * scala> val enc = scala.reflect.internal.pickling.ByteCodecs.encode(Array(1,2,3))
     * enc: Array[Byte] = Array(2, 5, 13, 1)
     * <p>
     * scala> scala.reflect.internal.pickling.ByteCodecs.decode(enc)
     * res43: Int = 4
     * <p>
     * scala> enc
     * res44: Array[Byte] = Array(1, 2, 3, 0)
     * <p>
     * However, this does not always happen.
     */
    public static int decode(byte[] xs) {
        int len = regenerateZero(xs);
        return decode7to8(xs, len);
    }
}
