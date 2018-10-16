package org.tokend.template.base.activities

import android.os.Bundle
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_progress.*
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.R
import org.tokend.template.extensions.toCompletable
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.ToastManager

class ProcessLinkActivity : BaseActivity() {
    private class LinkData {
        @SerializedName("status")
        val status: Int? = 200
        @SerializedName("type")
        val action: Int? = null
        @SerializedName("meta")
        val data: JsonElement? = null
    }

    private class VerificationData {
        @SerializedName("token")
        val token: String? = null
        @SerializedName("wallet_id")
        val walletId: String? = null
    }

    override val allowUnauthorized = true

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_process_link)

        progress.show()

        progress.post {
            processIntentUrl()
        }
    }

    private fun processIntentUrl() {
        val intentData = intent?.data

        if (intentData == null) {
            finish()
            return
        }

        val url = intentData.toString()

        if (url.contains("/r/")) {
            val split = url.split("/r/")
            if (split.size < 2) {
                finish()
                return
            }

            val encodedData = split[1]
            val decodedData = String(Base64.decode(encodedData,
                    Base64.NO_WRAP or Base64.URL_SAFE))

            try {
                val linkData = GsonFactory().getBaseGson().fromJson(decodedData,
                        LinkData::class.java)
                when (linkData.action) {
                    1 -> performVerification(GsonFactory().getBaseGson()
                            .fromJson(linkData.data,
                                    VerificationData::class.java))
                }
            } catch (e: Exception) {
                finish()
                return
            }
        } else {
            finish()
            return
        }
    }

    private fun performVerification(data: VerificationData) {
        val walletId = data.walletId ?: return
        val token = data.token ?: return

        apiProvider.getApi()
                .wallets
                .verify(walletId, token)
                .toCompletable()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = {
                            ToastManager(this).short(R.string.email_verified)
                            Navigator.toSignIn(this)
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                            finish()
                        }
                )
                .addTo(compositeDisposable)
    }
}
