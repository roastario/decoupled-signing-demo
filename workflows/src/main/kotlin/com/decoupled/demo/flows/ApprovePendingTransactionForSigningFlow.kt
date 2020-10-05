package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AsyncFinalizationService
import com.decoupled.demo.services.AsyncSigningService
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.SignedTransaction

@StartableByService
@StartableByRPC
class ApprovePendingTransactionForSigningFlow(private val secureHash: SecureHash) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val signingService = serviceHub.cordaService(AsyncSigningService::class.java)
        val signedTransaction = signingService.signTransaction(hash = secureHash)
        val finalizationService = serviceHub.cordaService(AsyncFinalizationService::class.java)
        finalizationService.registerTransactionForFinalization(signedTransaction)
        return signedTransaction
    }
}