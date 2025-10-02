package cn.com.vortexa.gopher;


import cn.com.vortexa.base.util.log.AppendLogger;
import cn.com.vortexa.bot_template.bot.AbstractVortexaBot;
import cn.com.vortexa.bot_template.bot.VortexaBotContext;
import cn.com.vortexa.bot_template.bot.anno.VortexaBot;
import cn.com.vortexa.bot_template.bot.anno.VortexaBotAPI;
import cn.com.vortexa.bot_template.bot.anno.VortexaBotCatalogueGroup;
import cn.com.vortexa.bot_template.bot.dto.FullAccountContext;
import cn.com.vortexa.bot_template.bot.handler.FullAccountContextScanner;
import cn.com.vortexa.bot_template.constants.VortexaBotApiSchedulerType;
import cn.com.vortexa.bot_template.entity.AccountContext;
import cn.com.vortexa.common.dto.PageResult;
import cn.com.vortexa.gopher.service.GopherApi;
import cn.com.vortexa.gopher.service.impl.GopherApiImpl;

/**
 * @author helei
 * @since 2025-10-02
 */
@VortexaBot(
        namespace = "Gopher",
        websiteUrl = "https://hub.gopher-ai.com/",
        catalogueGroup = {
                @VortexaBotCatalogueGroup(name = GopherBot.GROUP_INIT, order = 1),
                @VortexaBotCatalogueGroup(name = GopherBot.GROUP_QUERY, order = 2),
                @VortexaBotCatalogueGroup(name = GopherBot.GROUP_OPERATE, order = 3),
        }
)
public class GopherBot extends AbstractVortexaBot {
    public static final String GROUP_INIT = "init";
    public static final String GROUP_QUERY = "query";
    public static final String GROUP_OPERATE= "operate";


    public final GopherApi gopherApi;

    public GopherBot(VortexaBotContext vortexaBotContext) {
        super(vortexaBotContext);
        this.gopherApi = new GopherApiImpl();
    }

    @VortexaBotAPI(
            name = "Init wallet",
            catalogueName = GROUP_INIT,
            catalogueOrder = 1,
            schedulerType = VortexaBotApiSchedulerType.NONE
    )
    public void initWallet() {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {
            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return gopherApi.autoGenerateGopherWallet(fullAccountContext, logger);
            }
        });
    }

    @VortexaBotAPI(
            name = "Faucet",
            catalogueName = GROUP_INIT,
            catalogueOrder = 2,
            description = "params is [faucet times, retry times], please use sync invoke model",
            schedulerType = VortexaBotApiSchedulerType.ALL
    )
    public void faucet(int faucetTimes, int retryTimes) {
        forEachAccountContext((pageResult, i, fullAccountContext) -> {
            AppendLogger logger = getBotMethodInvokeContext().getLogger();
            gopherApi.faucet(fullAccountContext, faucetTimes, retryTimes, logger);
        });
    }

    @VortexaBotAPI(
            name = "GOAI balance query",
            catalogueName = GROUP_QUERY,
            catalogueOrder = 1,
            schedulerType = VortexaBotApiSchedulerType.ALL
    )
    public void goaiBalanceQuery() {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {
            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return gopherApi.goaiBalanceQuery(fullAccountContext, logger);
            }
        });
    }

    @VortexaBotAPI(
            name = "Staked balance query",
            catalogueName = GROUP_QUERY,
            catalogueOrder = 2,
            schedulerType = VortexaBotApiSchedulerType.ALL
    )
    public void stakedBalanceQuery() {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {
            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return gopherApi.stakedBalanceQuery(fullAccountContext, logger);
            }
        });
    }

    @VortexaBotAPI(
            name = "Stake",
            catalogueName = GROUP_OPERATE,
            catalogueOrder = 1,
            schedulerType = VortexaBotApiSchedulerType.ALL
    )
    public void stake(int minPercent, int maxPercent) {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {
            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return gopherApi.stake(fullAccountContext, minPercent, maxPercent, logger);
            }
        });
    }

    @VortexaBotAPI(
            name = "Un stake",
            catalogueName = GROUP_OPERATE,
            catalogueOrder = 2,
            schedulerType = VortexaBotApiSchedulerType.ALL
    )
    public void unStake(int minPercent, int maxPercent) {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {
            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return gopherApi.unStake(fullAccountContext, minPercent, maxPercent, logger);
            }
        });
    }

    @VortexaBotAPI(
            name = "Vote",
            catalogueName = GROUP_OPERATE,
            catalogueOrder = 2,
            schedulerType = VortexaBotApiSchedulerType.ALL
    )
    public void vote() {
        forEachAccountContext(new FullAccountContextScanner() {
            @Override
            public void scan(PageResult<AccountContext> pageResult, int i, FullAccountContext fullAccountContext) throws Exception {
            }

            @Override
            public Object scanWithResult(PageResult<AccountContext> page, int batchIdx, FullAccountContext fullAccountContext) throws Exception {
                AppendLogger logger = getBotMethodInvokeContext().getLogger();
                return gopherApi.vote(fullAccountContext, logger);
            }
        });
    }
}
