package org.tokend.template.di

import dagger.Module
import dagger.Provides
import org.tokend.template.features.urlconfig.model.UrlConfig
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.di.providers.UrlConfigProviderFactory
import javax.inject.Singleton

@Module
class UrlConfigProviderModule(
        private val defaultConfig: UrlConfig?
) {
    @Provides
    @Singleton
    fun urlConfigProvider(): UrlConfigProvider {
        return UrlConfigProviderFactory().createUrlConfigProvider()
                .also {
                    if (defaultConfig != null) {
                        it.setConfig(defaultConfig)
                    }
                }
    }
}