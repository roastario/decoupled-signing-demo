package com.decoupled.demo.flows.state.lifecycle

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AsyncSigningService
import com.template.contracts.SuperSimpleContract
import com.template.states.SuperSimpleStateWithOwner
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit

@StartableByRPC
@StartableByService
class MoveStateFlow(val stateToMove: StateAndRef<SuperSimpleStateWithOwner>, val newOwner: PublicKey) : FlowLogic<SecureHash>() {

    @Suspendable
    override fun call(): SecureHash {
        val transactionBuilder = TransactionBuilder(stateToMove.state.notary)
        transactionBuilder.addInputState(stateToMove)
        transactionBuilder.addOutputState(stateToMove.state.data.copy(currentOwner = newOwner))
        transactionBuilder.addCommand(SuperSimpleContract.SuperSimpleContractCommands.Update, stateToMove.state.data.currentOwner)
        transactionBuilder.setTimeWindow(TimeWindow.between(Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS)))
        val signingService = serviceHub.cordaService(AsyncSigningService::class.java)
        val (secureHash, completableFuture) = signingService.registerTransactionForSigning(transactionBuilder)
        return secureHash
    }


}