package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

@InitiatedBy(ApprovePendingTransactionForFinalizationFlow::class)
class ReceiveFinalizedTransactionFlow(private val counterParty: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterParty, statesToRecord = StatesToRecord.ONLY_RELEVANT))
    }

}