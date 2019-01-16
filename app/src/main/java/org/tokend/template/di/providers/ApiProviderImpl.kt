package org.tokend.template.di.providers

import okhttp3.CookieJar
import org.tokend.sdk.api.TokenDApi
import org.tokend.sdk.keyserver.KeyServer
import org.tokend.sdk.signing.AccountRequestSigner
import org.tokend.sdk.tfa.TfaCallback
import org.tokend.sdk.utils.CookieJarProvider
import org.tokend.sdk.utils.HashCodes
import org.tokend.template.BuildConfig

class ApiProviderImpl(
        private val urlConfigProvider: UrlConfigProvider,
        private val accountProvider: AccountProvider,
        private val tfaCallback: TfaCallback?,
        cookieJar: CookieJar?) : ApiProvider {
    private val url: String
        get() = urlConfigProvider.getConfig().api

    private var cookieJarProvider = cookieJar?.let {
        object : CookieJarProvider {
            override fun getCookieJar(): CookieJar {
                return it
            }
        }
    }

    private val withLogs: Boolean
        get() = BuildConfig.WITH_LOGS

    private var apiByHash: Pair<Int, TokenDApi>? = null
    private var ketServerByHash: Pair<Int, KeyServer>? = null

    private var signedApiByHash: Pair<Int, TokenDApi>? = null
    private var signedKeyServerByHash: Pair<Int, KeyServer>? = null

    override fun getApi(): TokenDApi {
        val hash = url.hashCode()

        val api = apiByHash
                ?.takeIf { (currentHash, _) ->
                    currentHash == hash
                }
                ?.second
                ?: TokenDApi(
                        url,
                        null,
                        tfaCallback,
                        cookieJarProvider,
                        withLogs = withLogs
                )

        apiByHash = Pair(hash, api)

        return api
    }

    override fun getKeyServer(): KeyServer {
        val hash = url.hashCode()

        val keyServer = ketServerByHash
                ?.takeIf { (currentHash, _) ->
                    currentHash == hash
                }
                ?.second
                ?: KeyServer(getApi().wallets)

        ketServerByHash = Pair(hash, keyServer)

        return keyServer
    }

    override fun getSignedApi(): TokenDApi? {
        val account = accountProvider.getAccount() ?: return null
        val hash = HashCodes.ofMany(account.accountId, url)

        val signedApi =
                signedApiByHash
                        ?.takeIf { (currentHash, _) ->
                            currentHash == hash
                        }
                        ?.second
                        ?: TokenDApi(
                                url,
                                AccountRequestSigner(account),
                                tfaCallback,
                                cookieJarProvider,
                                withLogs = withLogs
                        )

        signedApiByHash = Pair(hash, signedApi)

        return signedApi
    }

    override fun getSignedKeyServer(): KeyServer? {
        val account = accountProvider.getAccount() ?: return null
        val hash = HashCodes.ofMany(account, url)

        val signedKeyServer =
                signedKeyServerByHash
                        ?.takeIf { (currentHash, _) ->
                            currentHash == hash
                        }
                        ?.second
                        ?: KeyServer(getSignedApi()?.wallets!!)

        signedKeyServerByHash = Pair(hash, signedKeyServer)

        return signedKeyServer
    }
}