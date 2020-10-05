package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AsyncFinalizationService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

@StartableByService
@StartableByRPC
@InitiatingFlow
class ApprovePendingTransactionForFinalizationFlow(private val secureHash: SecureHash) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val finalizationService = serviceHub.cordaService(AsyncFinalizationService::class.java)
        val transactionToFinalize = finalizationService.getTransactionForFinalization(secureHash)

        //set to false as the notary is yet to sign
        val ledgerTransaction = transactionToFinalize.toLedgerTransaction(serviceHub, checkSufficientSignatures = false)

        val participants = (
                ledgerTransaction.inputStates.flatMap { it.participants } + ledgerTransaction.outputStates.flatMap { it.participants }
                ).distinctBy { it.owningKey.toStringShort() }

        val sessions = participants.filter { serviceHub.identityService.wellKnownPartyFromAnonymous(it) != ourIdentity }.map { initiateFlow(it) }

        return subFlow(FinalityFlow(transactionToFinalize, sessions, StatesToRecord.ALL_VISIBLE))
    }
}

