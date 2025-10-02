package cn.com.vortexa.gopher.service;

import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.exception.BotInvokeException;
import cn.com.vortexa.web3.dto.WalletInfo;
import cn.hutool.core.lang.Pair;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface GopherApi {

    WalletInfo autoGenerateGopherWallet(FullAccountContext fullAccountContext, AppendLogger logger) throws UnreadableWalletException;

    void faucet(FullAccountContext fullAccountContext, int faucetTimes, int retryTimes, AppendLogger logger) throws BotInvokeException, InterruptedException;

    Pair<Double, String> balanceQuery(FullAccountContext fullAccountContext, String denom) throws IOException, ExecutionException, InterruptedException;

    @NotNull Double queryStakedBalance(FullAccountContext fullAccountContext) throws InterruptedException, ExecutionException;

    Double goaiBalanceQuery(FullAccountContext fullAccountContext, AppendLogger logger) throws IOException, ExecutionException, InterruptedException;

    Double stakedBalanceQuery(FullAccountContext fullAccountContext, AppendLogger logger) throws ExecutionException, InterruptedException;

    String stake(FullAccountContext fullAccountContext, int minPercent, int maxPercent, AppendLogger logger) throws IOException, ExecutionException, InterruptedException, UnreadableWalletException;

    String unStake(FullAccountContext fullAccountContext, int minPercent, int maxPercent, AppendLogger logger) throws ExecutionException, InterruptedException, IOException, UnreadableWalletException;

    Map<String, String> vote(FullAccountContext fullAccountContext, AppendLogger logger) throws ExecutionException, InterruptedException;
}
