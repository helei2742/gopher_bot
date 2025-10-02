package cn.com.vortexa.gopher.util;


import cn.com.vortexa.web3.EthWalletUtil;
import cn.com.vortexa.web3.dto.WalletInfo;
import cn.hutool.core.lang.Pair;
import com.google.protobuf.Any;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import cosmos.auth.v1beta1.Auth;
import cosmos.auth.v1beta1.QueryOuterClass;
import cosmos.tx.v1beta1.TxOuterClass;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * @author helei
 * @since 2025-10-02
 */
public class GopherWalletUtil {

    public static String toMicroUnit(double amount, int precision) {
        BigDecimal bd = BigDecimal.valueOf(amount);
        BigDecimal factor = BigDecimal.TEN.pow(precision);
        BigDecimal micro = bd.multiply(factor);
        return micro.setScale(0, RoundingMode.DOWN).toBigInteger().toString();
    }

    public static String buildQueryBalanceData(String address, String denom) throws IOException {
        int size = 1 + CodedOutputStream.computeStringSizeNoTag(address)
                + 1 + CodedOutputStream.computeStringSizeNoTag(denom);

        byte[] buffer = new byte[size];
        CodedOutputStream cos = CodedOutputStream.newInstance(buffer);

        cos.writeString(1, address);
        cos.writeString(2, denom);
        cos.flush();

        return bytesToHex(buffer);
    }

    public static String buildQueryAccountData(String address) throws IOException {
        QueryOuterClass.QueryAccountRequest req = QueryOuterClass.QueryAccountRequest.newBuilder()
                .setAddress(address)
                .build();
        return bytesToHex(req.toByteArray());
    }

    public static Pair<Long, Long> resolveAccountValue(String base64Value) throws InvalidProtocolBufferException {
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Value);
        TxOuterClass.TxBody txBody = TxOuterClass.TxBody.parseFrom(bytes);
        Any msgAny = txBody.getMessagesList().getFirst();
        byte[] inner = msgAny.getValue().toByteArray();
        Auth.BaseAccount baseAccount = Auth.BaseAccount.parseFrom(inner);
        return Pair.of(baseAccount.getAccountNumber(), baseAccount.getSequence());
    }

    public static Pair<Double, String> resolveBalanceValue(String base64Value) {
        // 1. Base64 解码
        byte[] bytes = Base64.getDecoder().decode(base64Value);

        // 2. 解析 protobuf 格式（field 1 是 Coin）
        // Coin 的结构是：
        // 1: denom (string)
        // 2: amount (string)
        // 我们简单解析出两个字符串

        int index = 0;
        // 跳过 field tag
        index++; // 0a
        int coinLength = bytes[index++];

        // 子消息 Coin
        byte[] coinBytes = new byte[coinLength];
        System.arraycopy(bytes, index, coinBytes, 0, coinLength);

        index = 0;
        // 读取 denom
        index++; // 0a
        int denomLen = coinBytes[index++];
        String denom = new String(coinBytes, index, denomLen, StandardCharsets.UTF_8);
        index += denomLen;

        // 读取 amount
        index++; // 12
        int amountLen = coinBytes[index++];
        String amount = new String(coinBytes, index, amountLen, StandardCharsets.UTF_8);

        // 4. 如果 denom 是以 u 开头（最小单位），做单位换算
        if (denom.startsWith("u")) {
            double value = Double.parseDouble(amount) / 1_000_000.0;
            return Pair.of(value, denom.substring(1).toUpperCase());
        } else {
            return Pair.of(Double.parseDouble(amount), denom);
        }
    }

    public static WalletInfo generateGopherWallet() throws UnreadableWalletException {
        return generateGopherWalletFormMnemonic(EthWalletUtil.generateMnemonic());
    }

    public static WalletInfo generateGopherWalletFormMnemonic(String mnemonic) throws UnreadableWalletException {
        DeterministicSeed seed = new DeterministicSeed(mnemonic, null, "", 0);
        DeterministicKeyChain chain = DeterministicKeyChain.builder().seed(seed).build();

        // ✅ 派生路径：m/44'/118'/0'/0/0（Cosmos 系标准）
        List<ChildNumber> path = HDUtils.parsePath("44H/118H/0H/0/0");
        DeterministicKey key = chain.getKeyByPath(path, true);

        byte[] pubKey = key.getPubKey();

        // ✅ 1. 对公钥做 SHA256
        SHA256Digest sha256 = new SHA256Digest();
        sha256.update(pubKey, 0, pubKey.length);
        byte[] shaOut = new byte[32];
        sha256.doFinal(shaOut, 0);

        // ✅ 2. 对 SHA256 结果做 RIPEMD160
        RIPEMD160Digest ripemd160 = new RIPEMD160Digest();
        ripemd160.update(shaOut, 0, shaOut.length);
        byte[] addressBytes = new byte[20];
        ripemd160.doFinal(addressBytes, 0);

        // ✅ 3. Bech32 编码，前缀 "gopher"
        String address = Bech32.encode("gopher", Bech32.toWords(addressBytes));

        return WalletInfo.builder()
                .mnemonic(mnemonic)
                .privateKey(key.getPrivateKeyAsHex())
                .address(address)
                .publicKey(bytesToHex(pubKey))
                .build();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ====== ✅ Bech32 工具类实现 ======
    static class Bech32 {
        private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
        private static final int[] GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

        public static String encode(String hrp, byte[] data) {
            int[] checksum = createChecksum(hrp, data);
            byte[] combined = new byte[data.length + checksum.length];
            System.arraycopy(data, 0, combined, 0, data.length);
            for (int i = 0; i < checksum.length; i++) {
                combined[data.length + i] = (byte) checksum[i];
            }
            StringBuilder sb = new StringBuilder(hrp.length() + 1 + combined.length);
            sb.append(hrp);
            sb.append('1');
            for (byte b : combined) {
                sb.append(CHARSET.charAt(b));
            }
            return sb.toString();
        }

        public static byte[] toWords(byte[] bytes) {
            int value = 0;
            int bits = 0;
            java.util.List<Byte> ret = new java.util.ArrayList<>();
            for (byte b : bytes) {
                value = (value << 8) | (b & 0xff);
                bits += 8;
                while (bits >= 5) {
                    ret.add((byte) ((value >> (bits - 5)) & 31));
                    bits -= 5;
                }
            }
            if (bits > 0) {
                ret.add((byte) ((value << (5 - bits)) & 31));
            }
            byte[] out = new byte[ret.size()];
            for (int i = 0; i < ret.size(); i++) out[i] = ret.get(i);
            return out;
        }

        private static int[] createChecksum(String hrp, byte[] data) {
            int[] values = new int[hrpExpand(hrp).length + data.length + 6];
            System.arraycopy(hrpExpand(hrp), 0, values, 0, hrpExpand(hrp).length);
            for (int i = 0; i < data.length; i++) {
                values[hrpExpand(hrp).length + i] = data[i];
            }
            int polymod = polymod(values) ^ 1;
            int[] ret = new int[6];
            for (int i = 0; i < 6; i++) {
                ret[i] = (polymod >> (5 * (5 - i))) & 31;
            }
            return ret;
        }

        private static int[] hrpExpand(String hrp) {
            int len = hrp.length();
            int[] ret = new int[len * 2 + 1];
            for (int i = 0; i < len; i++) {
                ret[i] = hrp.charAt(i) >> 5;
            }
            ret[len] = 0;
            for (int i = 0; i < len; i++) {
                ret[len + 1 + i] = hrp.charAt(i) & 31;
            }
            return ret;
        }

        private static int polymod(int[] values) {
            int chk = 1;
            for (int v : values) {
                int top = chk >> 25;
                chk = ((chk & 0x1ffffff) << 5) ^ v;
                for (int i = 0; i < 5; i++) {
                    if (((top >> i) & 1) == 1) {
                        chk ^= GENERATOR[i];
                    }
                }
            }
            return chk;
        }
    }
}
