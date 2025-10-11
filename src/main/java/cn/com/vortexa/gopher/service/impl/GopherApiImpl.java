package cn.com.vortexa.gopher.service.impl;


import cn.com.vortexa.account.entity.Web3Wallet;
import cn.com.vortexa.base.constants.HeaderKey;
import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.exception.BotInvokeException;
import cn.com.vortexa.common.constants.HttpMethod;
import cn.com.vortexa.common.util.CastUtil;
import cn.com.vortexa.common.util.http.RestApiClient;
import cn.com.vortexa.common.util.http.RestApiClientFactory;
import cn.com.vortexa.gopher.service.GopherApi;
import cn.com.vortexa.gopher.util.GopherCosmosSigner;
import cn.com.vortexa.gopher.util.GopherWalletUtil;
import cn.com.vortexa.web3.dto.WalletInfo;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.google.protobuf.InvalidProtocolBufferException;
import cosmos.gov.v1beta1.Gov;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author helei
 * @since 2025-10-02
 */
@Slf4j
public class GopherApiImpl implements GopherApi {
    public static final String GOPHER_WALLET_KEY = "gopherWallet";
    public static final String GOAI_BALANCE_KEY = "$GOAI";
    public static final String GOAI_DELEGATED_BALANCE_KEY = "$stake_GOAI";

    private static final String VOTE_URL = "https://gopher-testnet-validator.dev.masalabs.ai/cosmos/gov/v1/proposals";
    private static final String BASE_URL = "https://hub.gopher-ai.com/api";
    private static final String JSON_RPC_URL = "https://rpc-gopher-testnet-validator.dev.masalabs.ai/";
    private static final String CHAIN_ID = "gopher-testnet";
    private static final String VALIDATOR_ADDRESS = "gophervaloper1smqe67yyzwluucgf4chdta22gnl7ye7na3u63d";

    private static final String PATH_FAUCET = "/faucet";
    private static final String PATH_STAKE_PREPARE = "/staking/prepare-tx";
    private static final String PATH_UN_STAKE_PREPARE = "/staking/undelegate";
    private static final String PATH_STAKE_INFO = "/staking/info";

    private static final String ORIGIN_TOKEN_DENOM = "ugoai";


    @Override
    public WalletInfo autoGenerateGopherWallet(FullAccountContext fullAccountContext, AppendLogger logger) throws UnreadableWalletException {
        Object param = fullAccountContext.getParam(GOPHER_WALLET_KEY);
        if (param != null) {
            throw new IllegalArgumentException("gopher wallet already exist");
        }
        Web3Wallet wallet = fullAccountContext.getWallet();
        WalletInfo gopherWallet;
        if (wallet == null || StrUtil.isEmpty(wallet.getMnemonic())) {
            gopherWallet = GopherWalletUtil.generateGopherWallet();
        } else {
            gopherWallet = GopherWalletUtil.generateGopherWalletFormMnemonic(wallet.getMnemonic());
        }
        fullAccountContext.putParam(GOPHER_WALLET_KEY, JSONObject.toJSONString(gopherWallet));
        logger.info("gopher wallet generate success, address: " + gopherWallet.getAddress());
        return gopherWallet;
    }

    @Override
    public void faucet(
            FullAccountContext fullAccountContext,
            int faucetTimes,
            int retryTimes,
            int exceptionDelay,
            AppendLogger logger
    ) throws BotInvokeException, InterruptedException {
        WalletInfo walletInfo = checkAndGetGopherWallet(fullAccountContext);

        for (int j = 0; j < faucetTimes; j++) {
            boolean success = false;
            Throwable lastError = null;
            for (int i = 0; i < retryTimes; i++) {
                logger.debug("start faucet[%s/%s]...[%s/%s]".formatted(j + 1, faucetTimes, i + 1, retryTimes));
                try {
                    JSONObject result = faucet(fullAccountContext, walletInfo).get();
                    logger.info("faucet success: " + result);
                    success = true;
                    break;
                } catch (InterruptedException | ExecutionException e) {
                    logger.warn("faucet [%s/%s] fail, retry...".formatted(i + 1, retryTimes));
                    TimeUnit.MILLISECONDS.sleep(RandomUtil.randomLong(0, exceptionDelay));
                    lastError = e;
                }
            }
            if (!success) {
                log.error("faucet[%s/%s] error, %s".formatted(j + 1, faucetTimes, lastError));
            }
        }
    }

    @Override
    public Pair<Double, String> balanceQuery(FullAccountContext fullAccountContext, String denom) throws IOException, ExecutionException, InterruptedException {
        WalletInfo walletInfo = checkAndGetGopherWallet(fullAccountContext);
        String data = GopherWalletUtil.buildQueryBalanceData(
                walletInfo.getAddress(), denom
        );
        JSONObject result = jsonRpc(
                fullAccountContext,
                "abci_query",
                Map.of(
                        "data", data,
                        "path", "/cosmos.bank.v1beta1.Query/Balance",
                        "prove", true
                )
        ).get();
        return GopherWalletUtil.resolveBalanceValue(result.getJSONObject("response").getString("value"));
    }

    @NotNull
    @Override
    public Double queryStakedBalance(FullAccountContext fullAccountContext) throws InterruptedException, ExecutionException {
        WalletInfo walletInfo = checkAndGetGopherWallet(fullAccountContext);

        JSONObject data = request(
                fullAccountContext,
                PATH_STAKE_INFO,
                HttpMethod.GET,
                Map.of("address", walletInfo.getAddress()),
                null
        ).get();
        Double totalDelegated = Double.parseDouble(data.getString("totalDelegated"));
        fullAccountContext.putParam(GOAI_DELEGATED_BALANCE_KEY, totalDelegated);
        return totalDelegated;
    }

    @Override
    public Double goaiBalanceQuery(FullAccountContext fullAccountContext, AppendLogger logger) throws IOException, ExecutionException, InterruptedException {
        logger.info("Start GOAI balance query...");
        Pair<Double, String> pair = balanceQuery(fullAccountContext, "ugoai");
        fullAccountContext.putParam(GOAI_BALANCE_KEY, pair.getKey());
        logger.info("GOAI balance query success, " + pair);
        return pair.getKey();
    }

    @Override
    public Double stakedBalanceQuery(FullAccountContext fullAccountContext, AppendLogger logger) throws ExecutionException, InterruptedException {
        logger.info("Start delegated balance query...");
        Double totalDelegated = queryStakedBalance(fullAccountContext);
        logger.info("Total delegated: " + totalDelegated);
        return totalDelegated;
    }

    @Override
    public String stake(FullAccountContext fullAccountContext, int minPercent, int maxPercent, AppendLogger logger) throws IOException, ExecutionException, InterruptedException, UnreadableWalletException {
        WalletInfo walletInfo = checkAndGetGopherWallet(fullAccountContext);

        Double balance = balanceQuery(fullAccountContext, ORIGIN_TOKEN_DENOM).getKey();
        if (balance == null || balance < 1.1) {
            throw new IllegalArgumentException("No $GOAI balance, less than 1.1. " + balance);
        }
        double stakeBalance = balance * (RandomUtil.randomDouble(minPercent, maxPercent) / 100.0);
        stakeBalance = keep6Decimal(Math.min(balance - 0.1, Math.max(stakeBalance, 1)));

        logger.info("Start stake..." + stakeBalance);
        logger.debug("prepare stake tx...");
        CompletableFuture<GopherCosmosSigner> prepareStakeTx = prepareStakeTx(fullAccountContext, walletInfo, stakeBalance);
        logger.debug("query account number and sequence...");
        CompletableFuture<Pair<Long, Long>> queryAccountNumberAndSequence = queryAccountNumberAndSequence(fullAccountContext, walletInfo);

        GopherCosmosSigner signer = prepareStakeTx.get();
        Pair<Long, Long> accountNumberAndSequence = queryAccountNumberAndSequence.get();
        signer.accountNumber(accountNumberAndSequence.getKey())
                .sequence(accountNumberAndSequence.getValue());
        String txBase64 = signer.buildTxBase64(walletInfo.getMnemonic());
        logger.debug("tx base 64 generate success, start broadcast...");

        String txHash = broadcastTxBase64(fullAccountContext, txBase64).get();
        logger.info("stake success, amount[%s]-gasFee[%s] tx hash: %s".formatted(
                signer.getAmount(), signer.getFeeAmount(), txHash
        ));
        return txHash;
    }

    @Override
    public String unStake(FullAccountContext fullAccountContext, int minPercent, int maxPercent, AppendLogger logger) throws ExecutionException, InterruptedException, IOException, UnreadableWalletException {
        WalletInfo walletInfo = checkAndGetGopherWallet(fullAccountContext);
        double stakedBalance = queryStakedBalance(fullAccountContext);
        if (stakedBalance < 1) {
            throw new IllegalArgumentException("No staked balance, less than 1. " + stakedBalance);
        }
        double unStakeBalance = stakedBalance * (RandomUtil.randomDouble(minPercent, maxPercent) / 100.0);
        unStakeBalance = keep6Decimal(Math.min(unStakeBalance, Math.max(unStakeBalance, 1)));

        logger.info("Start un stake..." + unStakeBalance);

        logger.debug("prepare un stake tx...");
        CompletableFuture<GopherCosmosSigner> prepareUnStakeTx = prepareUnStakeTx(fullAccountContext, walletInfo, unStakeBalance);
        logger.debug("query account number and sequence...");
        CompletableFuture<Pair<Long, Long>> queryAccountNumberAndSequence = queryAccountNumberAndSequence(fullAccountContext, walletInfo);

        GopherCosmosSigner signer = prepareUnStakeTx.get();
        Pair<Long, Long> accountNumberAndSequence = queryAccountNumberAndSequence.get();
        signer.accountNumber(accountNumberAndSequence.getKey())
                .sequence(accountNumberAndSequence.getValue());
        String txBase64 = signer.buildTxBase64(walletInfo.getMnemonic());
        logger.debug("tx base 64 generate success, start broadcast...");

        String txHash = broadcastTxBase64(fullAccountContext, txBase64).get();
        logger.info("un stake success, amount[%s]-gasFee[%s] tx hash: %s".formatted(
                signer.getAmount(), signer.getFeeAmount(), txHash
        ));
        return txHash;
    }

    @Override
    public Map<String, String> vote(FullAccountContext fullAccountContext, AppendLogger logger) throws ExecutionException, InterruptedException {
        List<JSONObject> proposals = getActiveProposal(fullAccountContext).get();
        if (CollUtil.isEmpty(proposals)) {
            logger.warn("no proposal can vote...");
            return Map.of();
        }

        WalletInfo walletInfo = checkAndGetGopherWallet(fullAccountContext);
        logger.info("start vote...total: " + proposals.size());
        Map<String, String> voteResult = new HashMap<>();
        for (JSONObject proposal : proposals) {
            logger.debug("vote [%s]...".formatted(proposal.get("id")));
            try {
                Pair<Long, Long> accountNumberAndSequence = queryAccountNumberAndSequence(fullAccountContext, walletInfo).get();
                String txBase64 = buildVoteTxBase64(
                        walletInfo,
                        proposal.getString("id"),
                        accountNumberAndSequence.getKey(),
                        accountNumberAndSequence.getValue()
                );
                String txHash = broadcastTxBase64(fullAccountContext, txBase64).get();
                voteResult.put(proposal.getString("id"), txHash);
                logger.debug("vote [%s] success, %s".formatted(proposal.get("id"), txHash));
            } catch (Exception e) {
                logger.error("vote [%s] fail, %s".formatted(proposal.get("id"), e.getMessage()));
            }
        }
        return voteResult;
    }

    private String buildVoteTxBase64(
            WalletInfo walletInfo, String proposalId, Long accountNumber, Long sequence
    ) throws UnreadableWalletException {
        return GopherCosmosSigner.builder()
                .chainId(CHAIN_ID)
                .msgTypeUrl("/cosmos.gov.v1beta1.MsgVote")
                .proposalId(Long.parseLong(proposalId))
                .voter(walletInfo.getAddress())
                .option(RandomUtil.randomEle(
                        List.of(Gov.VoteOption.VOTE_OPTION_YES, Gov.VoteOption.VOTE_OPTION_NO, Gov.VoteOption.VOTE_OPTION_ABSTAIN,  Gov.VoteOption.VOTE_OPTION_NO_WITH_VETO)
                ))
                .accountNumber(accountNumber)
                .sequence(sequence)
                .gasLimit(200000)
                .feeAmount("5000")
                .feeDenom(ORIGIN_TOKEN_DENOM)
                .buildTxBase64(walletInfo.getMnemonic());
    }

    private CompletableFuture<List<JSONObject>> getActiveProposal(
            FullAccountContext fullAccountContext
    ) {
        return getRestApiClient(fullAccountContext).request(
                VOTE_URL,
                HttpMethod.GET,
                buildHeaders(fullAccountContext, null),
                new JSONObject(Map.of("proposal_status", "PROPOSAL_STATUS_VOTING_PERIOD")),
                null
        ).thenApply(response -> {
            JSONObject result = JSONObject.parseObject(response, Feature.DisableSpecialKeyDetect);
            JSONArray proposals = result.getJSONArray("proposals");
            return proposals.stream().map(item -> (JSONObject) item).toList();
        });
    }

    private CompletableFuture<GopherCosmosSigner> prepareUnStakeTx(
            FullAccountContext fullAccountContext, WalletInfo walletInfo, double unStakeBalance
    ) {
        return request(
                fullAccountContext,
                PATH_UN_STAKE_PREPARE,
                HttpMethod.POST,
                null,
                Map.of(
                        "delegatorAddress", walletInfo.getAddress(),
                        "validatorAddress", VALIDATOR_ADDRESS,
                        "amount", String.valueOf(unStakeBalance)
                )
        ).thenApply(data -> {
            JSONObject message = data.getJSONArray("messages").getJSONObject(0);
            JSONObject value = message.getJSONObject("value");
            return GopherCosmosSigner.builder()
                    .chainId(CHAIN_ID)
                    .msgTypeUrl(message.getString("typeUrl"))
                    .delegatorAddress(value.getString("delegatorAddress"))
                    .validatorAddress(value.getString("validatorAddress"))
                    .amount(value.getJSONObject("amount").getString("amount"))
                    .denom(value.getJSONObject("amount").getString("denom"))
                    .gasLimit(Long.parseLong(data.getString("gasEstimate")))
                    .feeAmount(data.getJSONObject("fee").getString("amount"))
                    .feeDenom(data.getJSONObject("fee").getString("denom"));
        });
    }

    private static CompletableFuture<String> broadcastTxBase64(
            FullAccountContext fullAccountContext, String txBase64
    ) {
        return jsonRpc(
                fullAccountContext,
                "broadcast_tx_sync",
                Map.of(
                        "tx", txBase64
                )
        ).thenApply(result -> result.getString("hash"));
    }

    private static CompletableFuture<GopherCosmosSigner> prepareStakeTx(
            FullAccountContext fullAccountContext, WalletInfo walletInfo, double amount
    ) {
        return request(
                fullAccountContext,
                PATH_STAKE_PREPARE,
                HttpMethod.POST,
                null,
                Map.of(
                        "type", "delegate",
                        "delegatorAddress", walletInfo.getAddress(),
                        "validatorAddress", VALIDATOR_ADDRESS,
                        "amount", GopherWalletUtil.toMicroUnit(amount, 6)
                )
        ).thenApply(data -> {
            JSONObject message = data.getJSONArray("messages").getJSONObject(0);
            JSONObject value = message.getJSONObject("value");
            return GopherCosmosSigner.builder()
                    .chainId(CHAIN_ID)
                    .msgTypeUrl(message.getString("typeUrl"))
                    .delegatorAddress(value.getString("delegatorAddress"))
                    .validatorAddress(value.getString("validatorAddress"))
                    .amount(value.getJSONObject("amount").getString("amount"))
                    .denom(value.getJSONObject("amount").getString("denom"))
                    .gasLimit(Long.parseLong(data.getString("gasEstimate")))
                    .feeAmount(data.getJSONObject("fee").getString("amount"))
                    .feeDenom(data.getJSONObject("fee").getString("denom"));
        });
    }

    private static CompletableFuture<Pair<Long, Long>> queryAccountNumberAndSequence(
            FullAccountContext fullAccountContext, WalletInfo walletInfo
    ) throws IOException {
        String address = walletInfo.getAddress();
        return jsonRpc(
                fullAccountContext,
                "abci_query",
                Map.of(
                        "data", GopherWalletUtil.buildQueryAccountData(address),
                        "path", "/cosmos.auth.v1beta1.Query/Account",
                        "prove", true
                )
        ).thenApply(result -> {
            try {
                return GopherWalletUtil.resolveAccountValue(result.getJSONObject("response").getString("value"));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static CompletableFuture<JSONObject> faucet(
            FullAccountContext fullAccountContext, WalletInfo gopherWallet
    ) {
        return request(
                fullAccountContext,
                PATH_FAUCET,
                HttpMethod.POST,
                null,
                Map.of("address", gopherWallet.getAddress())
        );
    }

    private static CompletableFuture<JSONObject> jsonRpc(
            FullAccountContext fullAccountContext,
            String method,
            Map<String, Object> params
    ) {
        JSONObject body = new JSONObject();
        body.put("id", RandomUtil.randomLong());
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.put("params", params);
        return getRestApiClient(fullAccountContext).jsonRequest(
                JSON_RPC_URL,
                HttpMethod.POST,
                buildHeaders(fullAccountContext, null),
                null,
                body
        ).thenApply(result -> {
            if (result.get("error") != null) {
                throw new RuntimeException("json rpc error, " + result.get("error"));
            }
            return result.getJSONObject("result");
        });
    }

    private static CompletableFuture<JSONObject> request(
            FullAccountContext fullAccountContext,
            String path,
            HttpMethod method,
            Map<String, Object> params,
            Map<String, Object> body
    ) {
        return getRestApiClient(fullAccountContext).jsonRequest(
                BASE_URL + path,
                method,
                buildHeaders(fullAccountContext, path),
                params == null ? null : new JSONObject(params),
                body == null ? null : new JSONObject(body)
        ).thenApply(result -> {
            if (!result.getBoolean("success")) {
                throw new RuntimeException("request [%s] failed, %s".formatted(path, result.get("error")));
            }
            return result.getJSONObject("data");
        });
    }

    private static Map<String, String> buildHeaders(
            FullAccountContext fullAccountContext,
            String path
    ) {
        Map<String, String> headers = fullAccountContext.buildHeader();
        headers.put(HeaderKey.ORIGIN, "https://hub.gopher-ai.com");
        String referer = "https://hub.gopher-ai.com/";
        if (PATH_FAUCET.equals(path)) {
            referer += "gopher-faucet";
        }
        headers.put(HeaderKey.REFERER, referer);
        headers.put(HeaderKey.CONTENT_TYPE, "application/json");
        return headers;
    }

    private static RestApiClient getRestApiClient(FullAccountContext fullAccountContext) {
        return RestApiClientFactory.getClient(fullAccountContext.getProxy());
    }


    private static WalletInfo checkAndGetGopherWallet(FullAccountContext fullAccountContext) {
        String walletJSON = CastUtil.autoCast(fullAccountContext.getParam(GOPHER_WALLET_KEY));
        if (StrUtil.isBlank(walletJSON)) {
            throw new IllegalArgumentException("gopher not found, please generate wallet first");
        }
        return JSONObject.parseObject(walletJSON, WalletInfo.class);
    }

    public static double keep6Decimal(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(6, RoundingMode.DOWN); // 向下取整，也可以用 RoundingMode.HALF_UP 四舍五入
        return bd.doubleValue();
    }

}
