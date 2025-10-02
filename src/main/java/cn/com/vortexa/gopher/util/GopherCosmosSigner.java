package cn.com.vortexa.gopher.util;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import cosmos.base.v1beta1.CoinOuterClass;
import cosmos.crypto.ed25519.Keys;
import cosmos.gov.v1beta1.Gov;
import cosmos.staking.v1beta1.Tx;
import cosmos.tx.signing.v1beta1.Signing;
import cosmos.tx.v1beta1.TxOuterClass;
import lombok.Getter;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

@Getter
public class GopherCosmosSigner {
    private String delegatorAddress;
    private String validatorAddress;
    private String amount;
    private String denom;
    private String msgTypeUrl;
    private long sequence;
    private long gasLimit;
    private String feeAmount;
    private String feeDenom;
    private String chainId;
    private long accountNumber;

    private long proposalId;
    private String voter;
    private Gov.VoteOption option;


    public static GopherCosmosSigner builder() {
        return new GopherCosmosSigner();
    }

    public GopherCosmosSigner delegatorAddress(String delegatorAddress) {
        this.delegatorAddress = delegatorAddress;
        return this;
    }

    public GopherCosmosSigner validatorAddress(String validatorAddress) {
        this.validatorAddress = validatorAddress;
        return this;
    }

    public GopherCosmosSigner amount(String amount) {
        this.amount = amount;
        return this;
    }

    public GopherCosmosSigner denom(String denom) {
        this.denom = denom;
        return this;
    }

    public GopherCosmosSigner msgTypeUrl(String msgTypeUrl) {
        this.msgTypeUrl = msgTypeUrl;
        return this;
    }

    public GopherCosmosSigner sequence(long sequence) {
        this.sequence = sequence;
        return this;
    }

    public GopherCosmosSigner gasLimit(long gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    public GopherCosmosSigner feeAmount(String feeAmount) {
        this.feeAmount = feeAmount;
        return this;
    }

    public GopherCosmosSigner feeDenom(String feeDenom) {
        this.feeDenom = feeDenom;
        return this;
    }

    public GopherCosmosSigner chainId(String chainId) {
        this.chainId = chainId;
        return this;
    }

    public GopherCosmosSigner accountNumber(long accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    public GopherCosmosSigner proposalId(long proposalId) {
        this.proposalId = proposalId;
        return this;
    }
    public GopherCosmosSigner voter(String voter) {
        this.voter = voter;
        return this;
    }
    public GopherCosmosSigner option(Gov.VoteOption option) {
        this.option = option;
        return this;
    }

    public String buildTxBase64(
            String mnemonic
    ) throws UnreadableWalletException {
        DeterministicSeed seed = new DeterministicSeed(mnemonic, null, "", 0);
        DeterministicKeyChain keyChain = DeterministicKeyChain.builder().seed(seed).build();
        List<ChildNumber> path = HDUtils.parsePath("44H/118H/0H/0/0"); // Cosmos BIP44
        DeterministicKey key = keyChain.getKeyByPath(path, true);

        // -------------------------------
        // 2️⃣ 构造 TxBody
        TxOuterClass.TxBody txBody = switch (msgTypeUrl) {
            case "/cosmos.gov.v1beta1.MsgVote" -> buildVote();
            case "/cosmos.staking.v1beta1.MsgDelegate" -> buildStake();
            case "/cosmos.staking.v1beta1.MsgUndelegate" -> buildUnStake();
            default -> throw new IllegalStateException("Unexpected value: " + msgTypeUrl);
        };
        // -------------------------------
        // 4️⃣ AuthInfo
        Keys.PubKey pubKey = Keys.PubKey.newBuilder()
                .setKey(ByteString.copyFrom(key.getPubKey()))
                .build();
        Any pubKeyAny = Any.newBuilder()
                .setTypeUrl("/cosmos.crypto.secp256k1.PubKey")
                .setValue(pubKey.toByteString())
                .build();

        TxOuterClass.AuthInfo authInfo = TxOuterClass.AuthInfo.newBuilder()
                .addSignerInfos(TxOuterClass.SignerInfo.newBuilder()
                        .setPublicKey(pubKeyAny)
                        .setModeInfo(TxOuterClass.ModeInfo.newBuilder()
                                .setSingle(TxOuterClass.ModeInfo.Single.newBuilder()
                                        .setMode(Signing.SignMode.SIGN_MODE_DIRECT)
                                        .build())
                                .build())
                        .setSequence(sequence)
                        .build())
                .setFee(TxOuterClass.Fee.newBuilder()
                        .addAmount(CoinOuterClass.Coin.newBuilder().setDenom(feeDenom).setAmount(feeAmount).build())
                        .setGasLimit(gasLimit)
                        .build())
                .build();
        // -------------------------------
        // 5️⃣ SignDoc
        TxOuterClass.SignDoc signDoc = TxOuterClass.SignDoc.newBuilder()
                .setBodyBytes(txBody.toByteString())
                .setAuthInfoBytes(authInfo.toByteString())
                .setChainId(chainId)
                .setAccountNumber(accountNumber)
                .build();

        byte[] signature = secp256k1SignCanonical(key.getPrivKeyBytes(), signDoc.toByteArray());

        // -------------------------------
        // 7️⃣ TxRaw
        TxOuterClass.TxRaw txRaw = TxOuterClass.TxRaw.newBuilder()
                .setBodyBytes(txBody.toByteString())
                .setAuthInfoBytes(authInfo.toByteString())
                .addSignatures(ByteString.copyFrom(signature))
                .build();
        return Base64.getEncoder().encodeToString(txRaw.toByteArray());
    }

    private TxOuterClass.TxBody buildStake() {
        Tx.MsgDelegate msg = Tx.MsgDelegate.newBuilder()
                .setDelegatorAddress(delegatorAddress)
                .setValidatorAddress(validatorAddress)
                .setAmount(CoinOuterClass.Coin.newBuilder().setDenom(denom).setAmount(amount).build())
                .build();

        // -------------------------------
        // 3️⃣ TxBody
        Any anyMsg = Any.newBuilder()
                .setTypeUrl(msgTypeUrl)
                .setValue(msg.toByteString())
                .build();
        return TxOuterClass.TxBody.newBuilder()
                .addMessages(anyMsg)
                .setMemo("")
                .setTimeoutHeight(0)
                .build();
    }

    private TxOuterClass.TxBody buildVote() {
        // 1️⃣ 构造 MsgVote
        cosmos.gov.v1beta1.Tx.MsgVote msgVote = cosmos.gov.v1beta1.Tx.MsgVote.newBuilder()
                .setProposalId(proposalId) // proposalId 是 long
                .setVoter(voter)
                .setOption(option) // VOTE_OPTION_YES / NO / ABSTAIN / NO_WITH_VETO
                .build();

        // 2️⃣ 打包成 Any，用于 TxBody
        Any anyMsg = Any.newBuilder()
                .setTypeUrl("/cosmos.gov.v1beta1.MsgVote")
                .setValue(msgVote.toByteString())
                .build();
        return TxOuterClass.TxBody.newBuilder()
                .addMessages(anyMsg)
                .setMemo("")
                .setTimeoutHeight(0)
                .build();
    }

    private TxOuterClass.TxBody buildUnStake() {
        Tx.MsgUndelegate msg = Tx.MsgUndelegate.newBuilder()
                .setDelegatorAddress(delegatorAddress)
                .setValidatorAddress(validatorAddress)
                .setAmount(CoinOuterClass.Coin.newBuilder().setDenom(denom).setAmount(amount).build())
                .build();

        // -------------------------------
        // 3️⃣ TxBody
        Any anyMsg = Any.newBuilder()
                .setTypeUrl(msgTypeUrl)
                .setValue(msg.toByteString())
                .build();
        return TxOuterClass.TxBody.newBuilder()
                .addMessages(anyMsg)
                .setMemo("")
                .setTimeoutHeight(0)
                .build();
    }

    private static byte[] secp256k1SignCanonical(byte[] privKey, byte[] msg) {
        // 1️⃣ SHA256
        byte[] digest = new byte[32];
        SHA256Digest sha256 = new SHA256Digest();
        sha256.update(msg, 0, msg.length);
        sha256.doFinal(digest, 0);

        // 2️⃣ curve
        SecP256K1Curve curve = new SecP256K1Curve();
        BigInteger n = curve.getOrder();
        BigInteger gx = new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
        BigInteger gy = new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);
        ECDomainParameters domain = new ECDomainParameters(curve, curve.createPoint(gx, gy), n);

        ECPrivateKeyParameters priv = new ECPrivateKeyParameters(new BigInteger(1, privKey), domain);
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, priv);
        BigInteger[] sig = signer.generateSignature(digest);

        BigInteger r = sig[0];
        BigInteger s = sig[1];

        // 3️⃣ canonicalization: s <= n/2
        if (s.compareTo(n.shiftRight(1)) > 0) {
            s = n.subtract(s);
        }

        byte[] rb = toFixedLength(r.toByteArray(), 32);
        byte[] sb = toFixedLength(s.toByteArray(), 32);
        byte[] result = new byte[64];
        System.arraycopy(rb, 0, result, 0, 32);
        System.arraycopy(sb, 0, result, 32, 32);
        return result;
    }

    private static byte[] toFixedLength(byte[] b, int length) {
        byte[] r = new byte[length];
        if (b.length > length) {
            System.arraycopy(b, b.length - length, r, 0, length);
        } else if (b.length < length) {
            System.arraycopy(b, 0, r, length - b.length, b.length);
        } else {
            r = b;
        }
        return r;
    }
}
