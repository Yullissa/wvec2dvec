package com.yidian.wordvec2docvec.utils;

import lombok.extern.log4j.Log4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.PrintWriter;
import java.io.StringWriter;

@Log4j
public class StringTools {
    /**
     * 半角转全角
     * @param input String.
     * @return 全角字符串.
     */
    public static String ToSBC(String input) {
        char c[] = input.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == ' ') {
                c[i] = '\u3000';
            } else if (c[i] < '\177') {
                c[i] = (char) (c[i] + 65248);
            }
        }
        return new String(c);
    }

    /**
     * 全角转半角
     * @param input String.
     * @return 半角字符串
     */
    public static String ToDBC(String input) {
        char c[] = input.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == '\u3000') {
                c[i] = ' ';
            } else if (c[i] > '\uFF00' && c[i] < '\uFF5F') {
                c[i] = (char) (c[i] - 65248);
            }
        }
        String returnString = new String(c);
        return returnString;
    }

    public static double signatureScore(String doc1Sig, String doc2Sig) {
        if(doc1Sig != null && doc2Sig != null && !doc1Sig.isEmpty() && !doc2Sig.isEmpty()) {
            if(!doc1Sig.equalsIgnoreCase("ffffffffffffffff") && !doc2Sig.equalsIgnoreCase("ffffffffffffffff")) {
                try {
                    byte[] e = Hex.decodeHex(doc1Sig.toCharArray());
                    byte[] bytes2 = Hex.decodeHex(doc2Sig.toCharArray());
                    if(e.length != bytes2.length) {
                        log.warn(String.format("Length of signature does not match. %s vs %s", new Object[]{doc1Sig, doc2Sig}));
                        return 0.0D;
                    } else {
                        int num = 0;

                        for(int i = 0; i < e.length; ++i) {
                            byte xor = (byte)(e[i] ^ bytes2[i]);
                            byte x1 = (byte)((xor & 85) + (xor >> 1 & 85));
                            byte x2 = (byte)((x1 & 51) + (x1 >> 2 & 51));
                            num += (x2 & 15) + (x2 >> 4 & 15);
                        }
                        return (double)(64 - num) * 1.0D / 64.0D;
                    }
                } catch (DecoderException var9) {
                    log.error("", var9);
                    return 0.0D;
                }
            } else {
                return 0.0D;
            }
        } else {
            return 0.0D;
        }
    }

    /**
     *
     * @功能说明:在日志文件中，打印异常堆栈
     * @param e
     * @return:String
     */
    public static String LogExceptionStack(Throwable e) {
        StringWriter errorsWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(errorsWriter));
        return errorsWriter.toString();
    }

    public static void main(String[] args) {
//        String QJstr = "。。。 ... 「 」 + - — # . ： : 丨 【 】 、 的 了 是 着 在 ， , ! ！ “ ” 《 》 ( ) … ? ？· \" '";
//        Set<String> mp = Sets.newLinkedHashSet();
//        String[] part = QJstr.split(" ");
//        for(String token: part){
//            mp.add(token);
//        }
//        String result = StringTools.ToDBC(QJstr);
//        part = result.split(" ");
//        for(String token: part){
//            mp.add(token);
//        }
//        result = StringTools.ToSBC(QJstr);
//        part = result.split(" ");
//        for(String token: part){
//            mp.add(token);
//        }
//        StringBuilder sb = new StringBuilder();
//        for(String token: mp){
//            sb.append(token);
//            sb.append(" ");
//        }
//        System.out.println(sb.toString());
        System.out.println(signatureScore("8e54be837ecb655e", "8e54be837ecb655d"));
    }

}
