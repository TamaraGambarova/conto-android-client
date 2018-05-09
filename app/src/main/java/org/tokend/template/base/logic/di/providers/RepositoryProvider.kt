package org.tokend.template.base.logic.di.providers

import org.tokend.template.base.logic.repository.TxRepository
import org.tokend.template.base.logic.repository.balances.BalancesRepository

interface RepositoryProvider {
    fun balances(): BalancesRepository
    fun transactions(asset: String): TxRepository
}