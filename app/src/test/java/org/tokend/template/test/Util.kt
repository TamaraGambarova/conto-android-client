package org.tokend.template.test

import org.tokend.sdk.api.assets.model.AssetDetails
import org.tokend.sdk.factory.GsonFactory
import org.tokend.sdk.keyserver.models.WalletCreateResult
import org.tokend.template.data.model.UrlConfig
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.di.providers.UrlConfigProviderFactory
import org.tokend.template.features.signin.logic.PostSignInManager
import org.tokend.template.features.signin.logic.SignInUseCase
import org.tokend.template.logic.Session
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.Account
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.*
import org.tokend.wallet.xdr.op_extensions.CreateFeeOp
import java.math.BigDecimal

object Util {
    fun getUrlConfigProvider(url: String = Config.API): UrlConfigProvider {
        return UrlConfigProviderFactory().createUrlConfigProvider(
                UrlConfig(url, "", "", "")
        )
    }

    fun getVerifiedWallet(email: String,
                          password: CharArray,
                          apiProvider: ApiProvider,
                          session: Session,
                          repositoryProvider: RepositoryProvider?): WalletCreateResult {
        val createResult = apiProvider.getKeyServer().createAndSaveWallet(email, password)

        System.out.println("Email is $email")
        System.out.println("Recovery seed is " +
                createResult.recoveryAccount.secretSeed!!.joinToString(""))

        SignInUseCase(
                email,
                password,
                apiProvider.getKeyServer(),
                session,
                null,
                repositoryProvider?.let { PostSignInManager(it) }
        ).perform().blockingAwait()

        return createResult
    }

    fun getSomeMoney(asset: String,
                     amount: BigDecimal,
                     repositoryProvider: RepositoryProvider,
                     txManager: TxManager): BigDecimal {
        val netParams = repositoryProvider.systemInfo().getNetworkParams().blockingGet()

        val balanceId = repositoryProvider.balances()
                .itemsList
                .find { it.assetCode == asset }!!
                .id

        val issuance = IssuanceRequest(
                asset,
                netParams.amountToPrecised(amount),
                PublicKeyFactory.fromBalanceId(balanceId),
                "{}",
                Fee(0, 0, Fee.FeeExt.EmptyVersion()),
                IssuanceRequest.IssuanceRequestExt.EmptyVersion()
        )

        val op = CreateIssuanceRequestOp(
                issuance,
                "${System.currentTimeMillis()}",
                CreateIssuanceRequestOp.CreateIssuanceRequestOpExt.EmptyVersion()
        )

        val sourceAccount = Account.fromSecretSeed(Config.ADMIN_SEED)

        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
                .addOperation(Operation.OperationBody.CreateIssuanceRequest(op))
                .build()
        tx.addSignature(sourceAccount)

        txManager.submit(tx).blockingGet()

        return amount
    }

    fun addFeeForAccount(
            rootAccount: Account,
            repositoryProvider: RepositoryProvider,
            txManager: TxManager,
            feeType: FeeType,
            feeSubType: Int = 0,
            asset: String
    ): Boolean {
        val sourceAccount = Account.fromSecretSeed(Config.ADMIN_SEED)

        val netParams = repositoryProvider.systemInfo().getNetworkParams().blockingGet()

        val fixedFee = netParams.amountToPrecised(BigDecimal("0.050000"))
        val percentFee = netParams.amountToPrecised(BigDecimal("0.001000"))
        val upperBound = netParams.amountToPrecised(BigDecimal.TEN)
        val lowerBound = netParams.amountToPrecised(BigDecimal.ONE)

        val feeOp =
                CreateFeeOp(
                        feeType,
                        asset,
                        fixedFee,
                        percentFee,
                        upperBound,
                        lowerBound,
                        feeSubType.toLong(),
                        accountId = rootAccount.accountId
                )

        val op = Operation.OperationBody.SetFees(feeOp)

        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
                .addOperation(op)
                .build()

        tx.addSignature(sourceAccount)

        val response = txManager.submit(tx).blockingGet()

        return response.isSuccess
    }

    fun createAsset(
            sourceAccount: Account,
            apiProvider: ApiProvider,
            txManager: TxManager,
            session: Session,
            externalSystemType: String? = null
    ): String {
        val code = "${System.currentTimeMillis() / 1000}"

        val systemInfo =
                apiProvider.getApi()
                        .general
                        .getSystemInfo()
                        .execute()
                        .get()
        val netParams = systemInfo.toNetworkParams()

        val assetDetailsJson = GsonFactory().getBaseGson().toJson(
                AssetDetails("Asset name", null, null, externalSystemType)
        )

        val request = ManageAssetOp.ManageAssetOpRequest.CreateAssetCreationRequest(
                AssetCreationRequest(
                        code = code,
                        preissuedAssetSigner = PublicKeyFactory.fromAccountId(
                                systemInfo.masterExchangeAccountId
                        ),
                        maxIssuanceAmount = netParams.amountToPrecised(BigDecimal.TEN),
                        policies = 0,
                        initialPreissuedAmount = netParams.amountToPrecised(BigDecimal.TEN),
                        details = assetDetailsJson,
                        ext = AssetCreationRequest.AssetCreationRequestExt.EmptyVersion()
                )
        )

        val manageOp = ManageAssetOp(0, request,
                ManageAssetOp.ManageAssetOpExt.EmptyVersion())

        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
                .addOperation(Operation.OperationBody.ManageAsset(manageOp))
                .build()

        tx.addSignature(sourceAccount)

        txManager.submit(tx).blockingGet()

        return code
    }
}