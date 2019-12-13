package org.tokend.template.di

import dagger.Module
import dagger.Provides
import okhttp3.CookieJar
import org.tokend.template.di.providers.*
import org.tokend.template.logic.AppTfaCallback
import javax.inject.Singleton

@Module
class ApiProviderModule(
        private val cookieJar: CookieJar?
) {
    @Provides
    @Singleton
    fun apiProvider(urlConfigProvider: UrlConfigProvider,
                    accountProvider: AccountProvider,
                    walletInfoProvider: WalletInfoProvider,
                    tfaCallback: AppTfaCallback): ApiProvider {
        return ApiProviderFactory()
                .createApiProvider(urlConfigProvider, accountProvider, walletInfoProvider,
                        tfaCallback, cookieJar)
    }
}