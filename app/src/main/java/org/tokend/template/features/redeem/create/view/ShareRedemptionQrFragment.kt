package org.tokend.template.features.redeem.create.view

import android.os.Bundle
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.template.R
import org.tokend.template.features.history.model.BalanceChange
import org.tokend.template.features.history.model.details.BalanceChangeCause
import org.tokend.template.features.qr.ShareQrFragment
import org.tokend.template.fragments.VisibilityChangesConsumer
import org.tokend.template.logic.WalletEventsListener
import org.tokend.template.util.IntervalPoller
import org.tokend.template.util.ObservableTransformers
import java.util.concurrent.TimeUnit

open class ShareRedemptionQrFragment : ShareQrFragment(), VisibilityChangesConsumer {
    override val title: String
        get() = ""

    override val shareDialogText: String
        get() = getString(R.string.share_redemption_request)

    protected open val referenceToPoll: String?
        get() = arguments?.getString(REFERENCE_EXTRA)

    protected open val balanceId: String?
        get() = arguments?.getString(BALANCE_ID_EXTRA)

    override val visibilityChangesSubject = BehaviorSubject.create<Boolean>()

    private var pollingDisposable: Disposable? = null
    protected var isAccepted = false

    override fun onInitAllowed() {
        super.onInitAllowed()
        subscribeToVisibilityChanges()
    }

    private fun subscribeToVisibilityChanges() {
        visibilityChangesSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { isVisible: Boolean ->
                    if (isVisible) {
                        startPollingIfNeeded()
                    } else {
                        stopPolling()
                    }
                }
                .addTo(compositeDisposable)
    }

    protected open fun startPollingIfNeeded() {
        if (visibilityChangesSubject.value == false || isAccepted) {
            return
        }

        val reference = referenceToPoll ?: return

        pollingDisposable?.dispose()
        pollingDisposable = IntervalPoller<BalanceChange>(
                POLLING_INTERVAL_S,
                TimeUnit.SECONDS,
                Single.defer {
                    repositoryProvider
                            .balanceChanges(balanceId)
                            .getPage(null, BALANCE_CHANGES_TO_CHECK, PagingOrder.DESC, false)
                            .map { page ->
                                page.items.find {
                                    it.cause is BalanceChangeCause.Payment
                                            && it.cause.reference == reference
                                } ?: throw IllegalStateException("No redemption payment yet")
                            }
                }
        )
                .asSingle()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = this::onRedemptionAccepted,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    protected open fun stopPolling() {
        pollingDisposable?.dispose()
    }

    override fun onResume() {
        super.onResume()
        startPollingIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    protected open fun onRedemptionAccepted(balanceChange: BalanceChange) {
        isAccepted = true

        toastManager.long(R.string.successfully_accepted_redemption)

        repositoryProvider.balanceChanges(balanceId).addBalanceChange(balanceChange)
        repositoryProvider.balances().updateBalanceByDelta(balanceChange.balanceId, -balanceChange.amount)

        (activity as? WalletEventsListener)?.onRedemptionRequestAccepted()
    }

    companion object {
        val ID = "share_redemption_qr".hashCode().toLong()
        private const val REFERENCE_EXTRA = "reference"
        private const val BALANCE_ID_EXTRA = "balance_id"
        private const val POLLING_INTERVAL_S = 2L
        private const val BALANCE_CHANGES_TO_CHECK = 5

        fun getBundle(serializedRequest: String,
                      shareText: String,
                      referenceToPoll: String,
                      relatedBalanceId: String?) = getBundle(
                data = serializedRequest,
                shareText = shareText,
                topText = shareText,
                title = null,
                bottomText = null,
                shareDialogText = null
        )
                .apply {
                    putString(REFERENCE_EXTRA, referenceToPoll)
                    putString(BALANCE_ID_EXTRA, relatedBalanceId)
                }

        fun newInstance(bundle: Bundle): ShareRedemptionQrFragment {
            val fragment = ShareRedemptionQrFragment()
            fragment.arguments = bundle
            return fragment
        }
    }
}