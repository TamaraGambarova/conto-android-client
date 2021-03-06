package org.tokend.template.features.assets.buy.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_atomic_swap_asks.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.data.repository.AtomicSwapAsksRepository
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.assets.buy.view.adapter.AtomicSwapAskListItem
import org.tokend.template.features.assets.buy.view.adapter.AtomicSwapAsksAdapter
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.logic.WalletEventsListener
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager

class AtomicSwapAsksFragment : BaseFragment(), ToolbarProvider {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    private val assetCode: String by lazy {
        arguments?.getString(ASSET_CODE_EXTRA)
                ?: throw IllegalArgumentException("$ASSET_CODE_EXTRA must be specified in arguments")
    }

    private val asset: AssetRecord?
        get() = repositoryProvider.assets().itemsMap[assetCode]

    private val asksRepository: AtomicSwapAsksRepository
        get() = repositoryProvider.atomicSwapAsks(assetCode)

    private lateinit var adapter: AtomicSwapAsksAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_atomic_swap_asks, container, false)
    }

    override fun onInitAllowed() {
        toolbarSubject.onNext(toolbar)
        toolbar.title = getString(
                R.string.template_buy_asset,
                asset?.name ?: assetCode
        )

        initList()
        initSwipeRefresh()
        initHint()

        subscribeToAsks()

        update()
    }

    // region Init
    private fun initList() {
        adapter = AtomicSwapAsksAdapter(amountFormatter)
        adapter.onItemClick { _, item ->
            item.source?.also(this::openBuy)
        }

        asks_recycler_view.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        asks_recycler_view.adapter = adapter

        error_empty_view.setEmptyDrawable(R.drawable.ic_trade)
        error_empty_view.observeAdapter(adapter, R.string.no_offers)
        error_empty_view.setEmptyViewDenial(asksRepository::isNeverUpdated)

        ElevationUtil.initScrollElevation(hint_appbar, appbar_elevation_view)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initHint() {
        atomic_swap_hint_text_view.text = getString(
                R.string.template_atomic_swaps_hint,
                assetCode
        )
        hint_appbar.visibility = View.GONE
    }
    // endregion

    private fun subscribeToAsks() {
        asksRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayAsks() }
                .addTo(compositeDisposable)

        asksRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it)
                }
                .addTo(compositeDisposable)

        asksRepository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { error ->
                    if (!adapter.hasData) {
                        error_empty_view.showError(error,
                                errorHandlerFactory.getDefault()) {
                            update(true)
                        }
                    } else {
                        errorHandlerFactory.getDefault().handle(error)
                    }
                }
                .addTo(compositeDisposable)
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            asksRepository.updateIfNotFresh()
        } else {
            asksRepository.update()
        }
    }

    private fun displayAsks() {
        val items = asksRepository
                .itemsList
                .filterNot(AtomicSwapAskRecord::isCanceled)
                .map(::AtomicSwapAskListItem)
        adapter.setData(items)

        hint_appbar.visibility = View.GONE
    }

    private fun openBuy(ask: AtomicSwapAskRecord) {
        TODO("Buy with atomic swap is no more supported")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                BUY_REQUEST -> {
                    (activity as? WalletEventsListener)?.onAtomicSwapBuyConfirmed()
                }
            }
        }
    }

    companion object {
        val ID = "aswap_asks".hashCode().toLong() and 0xffff
        private const val ASSET_CODE_EXTRA = "asset_code"
        private val BUY_REQUEST = "buy".hashCode() and 0xffff

        fun newInstance(bundle: Bundle): AtomicSwapAsksFragment =
                AtomicSwapAsksFragment().withArguments(bundle)

        fun getBundle(assetCode: String) = Bundle().apply {
            putString(ASSET_CODE_EXTRA, assetCode)
        }
    }
}