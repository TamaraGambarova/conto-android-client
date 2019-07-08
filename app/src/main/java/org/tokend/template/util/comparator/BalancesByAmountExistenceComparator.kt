package org.tokend.template.util.comparator

import org.tokend.template.data.model.BalanceRecord

/**
 * Compares [BalanceRecord]s by existence of the available amount.
 * Order is descending (balances with non-zero amounts will be first)
 */
class BalancesByAmountExistenceComparator(
        private val fallbackComparator: Comparator<BalanceRecord>?
) : Comparator<BalanceRecord> {
    override fun compare(o1: BalanceRecord, o2: BalanceRecord): Int {
        val result = (o2.available.signum() > 0).compareTo(o1.available.signum() > 0)
        return if (result == 0 && fallbackComparator != null)
            fallbackComparator.compare(o1, o2)
        else
            result
    }
}