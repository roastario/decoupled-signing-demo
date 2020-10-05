package com.decoupled.demo.flows.state.lifecycle

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AsyncSigningService
import com.template.contracts.SuperSimpleContract.SuperSimpleContractCommands
import com.template.states.SuperSimpleStateWithOwner
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant
import java.time.temporal.ChronoUnit

@InitiatingFlow
@StartableByRPC
class IssueSimpleStateFlow : FlowLogic<SecureHash>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SecureHash {
        val stateWithOwner = SuperSimpleStateWithOwner(
                data = "Brand new",
                issuer = ourIdentity,
                currentOwner = ourIdentity.owningKey
        )

        val transactionBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.single())
                .addOutputState(stateWithOwner)
                .addCommand(SuperSimpleContractCommands.Issue, ourIdentity.owningKey)
                .setTimeWindow(TimeWindow.between(Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS)))


        val (transactionHash, signingFuture) = serviceHub.cordaService(AsyncSigningService::class.java)
                .registerTransactionForSigning(transactionBuilder)
        return transactionHash

    }
}
