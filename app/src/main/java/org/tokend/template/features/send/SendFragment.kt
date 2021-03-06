package org.tokend.template.features.send

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.features.balances.model.BalanceRecord
import org.tokend.template.features.balances.storage.BalancesRepository
import org.tokend.template.features.history.model.SimpleFeeRecord
import org.tokend.template.features.send.amount.model.PaymentAmountData
import org.tokend.template.features.send.amount.view.PaymentAmountFragment
import org.tokend.template.features.send.logic.CreatePaymentRequestUseCase
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.features.send.recipient.model.PaymentRecipientAndDescription
import org.tokend.template.features.send.recipient.view.PaymentRecipientFragment
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.logic.WalletEventsListener
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.UserFlowFragmentDisplayer
import org.tokend.template.view.util.input.SoftInputUtil
import java.math.BigDecimal

class SendFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val balances: List<BalanceRecord>
        get() = balancesRepository
                .itemsList
                .filter(BalanceRecord::hasAvailableAmount)

    private val requiredBalanceId: String?
        get() = arguments?.getString(REQUIRED_BALANCE_ID_EXTRA)

    private val requiredBalance: BalanceRecord?
        get() = balances
                .find { it.id == requiredBalanceId }

    private val requiredAmount: BigDecimal?
        get() = arguments?.getString(AMOUNT_EXTRA)?.let { BigDecimalUtil.valueOf(it) }

    private val requiredRecipient: String?
        get() = arguments?.getString(RECIPIENT_ACCOUNT_EXTRA)

    private val requiredRecipientNickname: String?
        get() = arguments?.getString(RECIPIENT_NICKNAME_EXTRA)

    private val allowToolbar: Boolean
        get() = arguments?.getBoolean(ALLOW_TOOLBAR_EXTRA) ?: true

    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private var recipient: PaymentRecipient? = null
    private var amount: BigDecimal = BigDecimal.ZERO
    private var balance: BalanceRecord? = null
    private var description: String? = null
    private var fee: PaymentFee? = null

    private var isWaitingForTransferableAssets: Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_user_flow, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initErrorEmptyView()

        subscribeToBalances()

        update()
    }

    // region Init
    private fun initToolbar() {
        toolbarSubject.onNext(toolbar)
        toolbar.title = getString(R.string.send_title)

        val requiredAmount = this.requiredAmount
        val requiredAsset = this.requiredBalance?.asset

        if (requiredAmount != null && requiredAsset != null) {
            toolbar.subtitle = amountFormatter.formatAssetAmount(
                    requiredAmount,
                    requiredAsset,
                    withAssetName = true
            )
        }

        if (!allowToolbar) {
            appbar.visibility = View.GONE
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { balancesRepository.update() }
    }

    private fun initErrorEmptyView() {
        error_empty_view.setEmptyDrawable(R.drawable.ic_send)
    }
    // endregion

    private var balancesDisposable: CompositeDisposable? = null

    private fun subscribeToBalances() {
        balancesDisposable?.dispose()
        balancesDisposable = CompositeDisposable(
                balancesRepository.itemsSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .subscribe {
                            onBalancesUpdated()
                        },
                balancesRepository.loadingSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .subscribe { loadingIndicator.setLoading(it, "balances") },
                balancesRepository.errorsSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .subscribe {
                            if (isWaitingForTransferableAssets) {
                                toErrorView(it)
                            } else {
                                errorHandlerFactory.getDefault().handle(it)
                            }
                        }
        ).also { it.addTo(compositeDisposable) }
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            balancesRepository.updateIfNotFresh()
        } else {
            balancesRepository.update()
        }
    }

    private fun onBalancesUpdated() {
        val anyTransferableAssets = balances
                .map(BalanceRecord::asset)
                .any(AssetRecord::isTransferable)

        if (anyTransferableAssets) {
            if (isWaitingForTransferableAssets) {
                isWaitingForTransferableAssets = false

                hideErrorOrEmptyView()
                startFlow()
            }
        } else {
            isWaitingForTransferableAssets = true
            toEmptyView()
        }
    }

    private fun startFlow() {
        val requiredRecipient = this.requiredRecipient

        if (requiredRecipient == null) {
            toRecipientScreen()
        } else {
            onRecipientSelected(PaymentRecipientAndDescription(
                    recipient = PaymentRecipient(
                            accountId = requiredRecipient,
                            nickname = this.requiredRecipientNickname
                    ),
                    description = null
            ))
        }
    }

    private fun toRecipientScreen() {
        val requestDescription = requiredAmount != null

        val fragment = PaymentRecipientFragment.newInstance(
                PaymentRecipientFragment.getBundle(requestDescription)
        )

        fragment
                .resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onRecipientSelected,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "recipient", null)
    }

    private fun onRecipientSelected(recipientAndDescription: PaymentRecipientAndDescription) {
        this.recipient = recipientAndDescription.recipient
        this.description = recipientAndDescription.description

        val requiredAmount = this.requiredAmount
        val requiredBalance = this.requiredBalance

        if (requiredAmount != null && requiredBalance != null) {
            onAmountEntered(PaymentAmountData(requiredAmount, requiredBalance,
                    description, PaymentFee(SimpleFeeRecord.ZERO, SimpleFeeRecord.ZERO)))
        } else {
            toAmountScreen()
        }
    }

    private fun toAmountScreen() {
        val recipientNickname = recipient?.displayedValue
                ?: return
        val recipientAccount = recipient?.accountId
                ?: return

        val fragment = PaymentAmountFragment.newInstance(
                PaymentAmountFragment.getBundle(recipientNickname, recipientAccount, requiredBalanceId)
        )

        fragment
                .resultObservable
                .map { it as PaymentAmountData }
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onAmountEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )

        fragmentDisplayer.display(fragment, "amount", true)
    }

    private fun onAmountEntered(result: PaymentAmountData) {
        this.amount = result.amount
        this.balance = result.balance
        this.description = result.description
        this.fee = result.fee

        createAndConfirmPaymentRequest()
    }

    private var paymentRequestDisposable: Disposable? = null
    private fun createAndConfirmPaymentRequest() {
        val recipient = recipient ?: return
        val fee = fee ?: return
        val balance = balance ?: return

        paymentRequestDisposable?.dispose()
        paymentRequestDisposable = CreatePaymentRequestUseCase(
                recipient,
                amount,
                balance,
                description,
                fee,
                walletInfoProvider
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = this::onPaymentRequestCreated,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onPaymentRequestCreated(request: PaymentRequest) {
        Navigator.from(this)
                .openPaymentConfirmation(request)
                .addTo(activityRequestsBag)
                .doOnSuccess {
                    (activity as? WalletEventsListener)
                            ?.onPaymentRequestConfirmed(request)
                }
    }

    // region Error/empty
    private fun toEmptyView() {
        fragmentDisplayer.clearBackStack()
        SoftInputUtil.hideSoftInput(requireActivity())
        error_empty_view.showEmpty(R.string.error_no_transferable_assets)
    }

    private fun toErrorView(e: Throwable) {
        fragmentDisplayer.clearBackStack()
        SoftInputUtil.hideSoftInput(requireActivity())
        error_empty_view.showError(e, errorHandlerFactory.getDefault()) {
            update(force = true)
        }
    }

    private fun hideErrorOrEmptyView() {
        error_empty_view.hide()
    }
    // endregion

    override fun onBackPressed(): Boolean {
        return !fragmentDisplayer.tryPopBackStack()
    }

    companion object {
        private const val REQUIRED_BALANCE_ID_EXTRA = "required_balance_id"
        private const val ALLOW_TOOLBAR_EXTRA = "allow_toolbar"
        private const val AMOUNT_EXTRA = "amount"
        private const val RECIPIENT_ACCOUNT_EXTRA = "recipient_account"
        private const val RECIPIENT_NICKNAME_EXTRA = "recipient_nickname"
        const val ID = 1118L

        fun newInstance(bundle: Bundle): SendFragment = SendFragment().withArguments(bundle)

        fun getBundle(amount: BigDecimal?,
                      recipientAccount: String?,
                      recipientNickname: String?,
                      balanceId: String?,
                      allowToolbar: Boolean) = Bundle().apply {
            putString(REQUIRED_BALANCE_ID_EXTRA, balanceId)
            putBoolean(ALLOW_TOOLBAR_EXTRA, allowToolbar)
            putString(AMOUNT_EXTRA, amount?.let(BigDecimalUtil::toPlainString))
            putString(RECIPIENT_ACCOUNT_EXTRA, recipientAccount)
            putString(RECIPIENT_NICKNAME_EXTRA, recipientNickname)
        }
    }
}