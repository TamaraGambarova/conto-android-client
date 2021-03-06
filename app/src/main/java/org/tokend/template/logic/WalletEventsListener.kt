package org.tokend.template.logic

import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.features.withdraw.model.WithdrawalRequest

interface WalletEventsListener {
    fun onPaymentRequestConfirmed(paymentRequest: PaymentRequest)
    fun onWithdrawalRequestConfirmed(withdrawalRequest: WithdrawalRequest)
    fun onAtomicSwapBuyConfirmed()
    fun onRedemptionRequestAccepted()
}