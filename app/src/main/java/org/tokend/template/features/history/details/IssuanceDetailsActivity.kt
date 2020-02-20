package org.tokend.template.features.history.details

import android.support.v4.content.ContextCompat
import org.tokend.template.R
import org.tokend.template.features.history.model.BalanceChange
import org.tokend.template.features.history.model.details.BalanceChangeCause
import org.tokend.template.view.details.DetailsItem

class IssuanceDetailsActivity : BalanceChangeDetailsActivity() {
    override fun displayDetails(item: BalanceChange) {
        super.displayDetails(item)

        val details = item.cause as? BalanceChangeCause.Issuance

        if (details == null) {
            finishWithError(IllegalStateException("Invalid item cause type"))
            return
        }

        displayReferenceAndCause(details)
    }

    private fun displayReferenceAndCause(details: BalanceChangeCause.Issuance) {
        val reference = details.reference?.takeIf { it.isNotBlank() }
        val cause = details.cause?.takeIf { it.isNotBlank() }

        if (cause != null) {
            adapter.addData(
                    DetailsItem(
                            text = cause,
                            hint = getString(R.string.tx_cause),
                            icon =
                            if (reference == null)
                                ContextCompat.getDrawable(this, R.drawable.ic_label_outline)
                            else
                                null
                    )
            )
        }
    }
}