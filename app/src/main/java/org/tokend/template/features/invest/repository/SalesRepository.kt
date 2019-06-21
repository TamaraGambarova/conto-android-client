package org.tokend.template.features.invest.repository

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.sales.params.SalesParams
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.features.invest.model.SaleRecord

class SalesRepository(
        private val ownerId: String?,
        private val apiProvider: ApiProvider,
        private val urlConfigProvider: UrlConfigProvider,
        itemsCache: RepositoryCache<SaleRecord>
) : PagedDataRepository<SaleRecord>(itemsCache) {

    private var name: String? = null
    private var baseAsset: String? = null

    override fun getPage(nextCursor: String?): Single<DataPage<SaleRecord>> {
        val requestParams = SalesParams(
                ownerAccountId = ownerId,
                name = name,
                baseAsset = baseAsset,
                pagingParams = PagingParamsV2(
                        page = nextCursor,
                        order = PagingOrder.DESC,
                        limit = DEFAULT_LIMIT
                ),
                openOnly = true
        )

        return apiProvider.getApi()
                .sales
                .getAll(requestParams)
                .toSingle()
                .map { page ->
                    DataPage(
                            page.nextCursor,
                            page.items.map {
                                SaleRecord(it, urlConfigProvider.getConfig())
                            },
                            page.isLast
                    )
                }
    }

    fun getSingle(id: Long): Single<SaleRecord> {
        return apiProvider.getApi()
                .sales
                .getById(id)
                .toSingle()
                .map { SaleRecord(it, urlConfigProvider.getConfig()) }
    }

    fun forQuery(name: String? = null, baseAsset: String? = null): SalesRepository {
        this.name = name
        this.baseAsset = baseAsset
        return this
    }

    companion object {
        private const val DEFAULT_LIMIT = 10
    }
}