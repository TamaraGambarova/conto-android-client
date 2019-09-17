package org.tokend.template.features.assets.buy.singleprice.view.adapter

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AtomicSwapAskRecord
import java.math.BigDecimal

class SinglePriceAtomicSwapAskListItem(
        val available: BigDecimal,
        val asset: Asset,
        val logoUrl: String?,
        val price: BigDecimal,
        val priceAsset: Asset,
        val companyName: String?,
        val source: AtomicSwapAskRecord?
) {
    constructor(source: AtomicSwapAskRecord,
                withCompany: Boolean) : this(
            available = source.amount,
            asset = source.asset,
            logoUrl = source.asset.logoUrl,
            price = source.price,
            priceAsset = source.priceAsset,
            companyName = if (withCompany) source.company.name else null,
            source = source
    )
}