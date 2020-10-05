package com.decoupled.testing

import com.decoupled.demo.flows.*
import com.decoupled.demo.flows.state.lifecycle.IssueSimpleStateFlow
import com.decoupled.demo.flows.state.lifecycle.MoveStateFlow
import com.template.states.SuperSimpleStateWithOwner
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.hamcrest.core.Is.`is`
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Future

class DriverBasedTest {
    private val demoFlows = TestCordapp.findCordapp("com.decoupled.demo")
    private val contracts = TestCordapp.findCordapp("com.template")
    private val accountsContracts = TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts")
    private val accountsWorkflows = TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")

    init {
        // UNCOMMENT if running out of process nodes as we need to register BC in order to generate ECDSA keys
//        val bouncyCastleProvider = BouncyCastleProvider()
//        Security.addProvider(bouncyCastleProvider)
    }


    @Test
    fun `Build Sign and Finalize as separate steps`() {
        withDriver {
            val node1 = startNodes(TestIdentity(CordaX500Name("BankA", "", "GB"))).single()
            val issueTxPendingSigning = node1.rpc.startFlow(::IssueSimpleStateFlow).returnValue.getOrThrow()
            //we have a TX pending signing
            Assert.assertThat(issueTxPendingSigning, `is`(node1.rpc.startFlow(::GetTransactionsPendingSigningFlow).returnValue.getOrThrow().single()))
            //aprove signing of tx
            val signedIssueTxPendingFinalization = node1.rpc.startFlow(::ApprovePendingTransactionForSigningFlow, issueTxPendingSigning).returnValue.getOrThrow()
            //approve finalization of tx
            val finalizedIssueTx = node1.rpc.startFlow(::ApprovePendingTransactionForFinalizationFlow, issueTxPendingSigning).returnValue.getOrThrow()
            val issuedState = finalizedIssueTx.coreTransaction.outRefsOfType(SuperSimpleStateWithOwner::class.java).single()

            //Generate new keyPair - the private key for this keypair is never exposed to corda
            val newPublicKey1 = node1.rpc.startFlow(::GenerateExternalKeyFlow).returnValue.getOrThrow()

            //tx for move from issuer to new public key (pending signing)
            val moveToPublicKeyPendingSigningTx = node1.rpc.startFlow(::MoveStateFlow, issuedState, newPublicKey1).returnValue.getOrThrow()
            Assert.assertThat(node1.rpc.startFlow(::GetTransactionsPendingSigningFlow).returnValue.getOrThrow().single(), `is`(moveToPublicKeyPendingSigningTx))

            //approve signing of tx that moves from issue to new public key
            val signedMoveTx = node1.rpc.startFlow(::ApprovePendingTransactionForSigningFlow, moveToPublicKeyPendingSigningTx).returnValue.getOrThrow()
            //to be an active ledger participant the freshly generated public key must be logged as being associated with a party (ourselves in this case)
            node1.rpc.startFlow(::RegisterKeyToPartyFlow, newPublicKey1, node1.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()

            //approve finalization of TX which moves the state from issuer to new public key (not an account)
            val finalizedMoveTx = node1.rpc.startFlow(::ApprovePendingTransactionForFinalizationFlow, signedMoveTx.id).returnValue.getOrThrow()
            val movedState = finalizedMoveTx.coreTransaction.outRefsOfType<SuperSimpleStateWithOwner>().single()

            //check that the new state really has the expected owner
            Assert.assertThat(movedState.state.data.currentOwner.toStringShort(), `is`(newPublicKey1.toStringShort()))

            //generate a new key pair
            val newPublicKey2 = node1.rpc.startFlow(::GenerateExternalKeyFlow).returnValue.getOrThrow()
            //submit TX that moves state from pubKey1 -> pubKey2
            val secondMovTxPendingSigning = node1.rpc.startFlow(::MoveStateFlow, movedState, newPublicKey2).returnValue.getOrThrow()
            //approve signing of TX that moves state from pubKey1 -> pubKey2
            val signedSecondMoveTx = node1.rpc.startFlow(::ApprovePendingTransactionForSigningFlow, secondMovTxPendingSigning).returnValue.getOrThrow()
            // register pubKey2 as an account this time
            val accountIdForKey2 = node1.rpc.startFlow(::RegisterKeyToAccountFlow, newPublicKey2, "demo account for key 2").returnValue.getOrThrow()
            // approve finalization of Tx that moves state from pubKey1 -> pubKey2
            val finalizedSecondMoveTx = node1.rpc.startFlow(::ApprovePendingTransactionForFinalizationFlow, signedSecondMoveTx.id).returnValue.getOrThrow()
            val secondMoveState = finalizedSecondMoveTx.coreTransaction.outRefsOfType<SuperSimpleStateWithOwner>().single()
            val foundStatesByAccount = node1.rpc.vaultQueryBy<SuperSimpleStateWithOwner>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(accountIdForKey2))).states
            val foundStatesByType = node1.rpc.vaultQuery(SuperSimpleStateWithOwner::class.java).states

            //check that the expected state is present when searching by account ID
            Assert.assertThat(secondMoveState, `is`(foundStatesByAccount.single()))
            //check that the expected state is present when searching by state type
            Assert.assertThat(secondMoveState, `is`(foundStatesByType.single()))

            //check that the current owner is correct
            Assert.assertThat(secondMoveState.state.data.currentOwner.toStringShort(), `is`(newPublicKey2.toStringShort()))
            val node2 = startNode().getOrThrow()

            //generate a key and associate with an account on second participant
            val keyOnSecondParticipant = node2.rpc.startFlow(::GenerateExternalKeyFlow).returnValue.getOrThrow()
            val accountOnSecondParticipant = node2.rpc.startFlow(::RegisterKeyToAccountFlow, keyOnSecondParticipant, "account of second participant").returnValue.getOrThrow()

            //submit tx that moves from node1 to node2 for signing
            val moveFromNode1ToNode2TxPendingSigning = node1.rpc.startFlow(::MoveStateFlow, secondMoveState, keyOnSecondParticipant).returnValue.getOrThrow()
            //approve signing of tx that moves from node1 to node2
            val signedMoveFromNode1ToNode2Tx = node1.rpc.startFlow(::ApprovePendingTransactionForSigningFlow, moveFromNode1ToNode2TxPendingSigning).returnValue.getOrThrow()
            //associate this key (that lives on node2) with node2 on node1 so that node1 understands it - notice that node1 has no account info for this key
            node1.rpc.startFlow(::RegisterKeyToPartyFlow, keyOnSecondParticipant, node2.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
            //approve finalization of tx that moves from node1 to node2
            val finalizedMoveFromNode1ToNode2 = node1.rpc.startFlow(::ApprovePendingTransactionForFinalizationFlow, signedMoveFromNode1ToNode2Tx.id).returnValue.getOrThrow()

            //check that the correct owner is set on the moved state
            val movedFromNode1ToNode2 = finalizedMoveFromNode1ToNode2.coreTransaction.outRefsOfType<SuperSimpleStateWithOwner>().single()
            Assert.assertThat(movedFromNode1ToNode2.state.data.currentOwner, `is`(keyOnSecondParticipant))
            println(" Test completed ")
        }
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
            DriverParameters(isDebug = true, startNodesInProcess = false, cordappsForAllNodes = listOf(demoFlows, contracts, accountsContracts, accountsWorkflows))
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
            .map { startNode(providedName = it.name) }
            .waitForAll()
}