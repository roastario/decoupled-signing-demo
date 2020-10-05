package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AsyncSigningService
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService

@StartableByRPC
@StartableByService
class GetTransactionsPendingSigningFlow : FlowLogic<List<SecureHash>>() {
    @Suspendable
    override fun call(): List<SecureHash> {
        val signingService = serviceHub.cordaService(AsyncSigningService::class.java)
        return signingService.getPendingTransactions()
    }
}