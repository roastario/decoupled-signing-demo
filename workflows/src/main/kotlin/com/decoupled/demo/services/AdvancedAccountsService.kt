package com.decoupled.demo.services

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.ourIdentity
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture

@CordaService
class AdvancedAccountsService(val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {

    private val uuidToKeyMap: MutableMap<UUID, PublicKey> = Collections.synchronizedMap(mutableMapOf())


    fun shareAccountAndKeyForAccount(accountInfo: StateAndRef<AccountInfo>,
                                     publicKey: PublicKey,
                                     partyToShareWith: Party) {
        val accountService = appServiceHub.cordaService(KeyManagementBackedAccountService::class.java)
        accountService.shareAccountInfoWithParty(accountInfo.state.data.identifier.id, partyToShareWith)
    }

    @Suspendable
    fun registerKeyToNewAccount(accountName: String, publicKey: PublicKey): CompletableFuture<UUID> {
        return flowAwareStartFlow(CreateAccount(accountName)).toCompletableFuture().thenApply { newAccount ->
            newAccount.state.data.identifier.id
        }.thenApply {
            appServiceHub.identityService.registerKey(publicKey, appServiceHub.ourIdentity, it)
            uuidToKeyMap[it] = publicKey
            it
        }
    }

    fun keyForAccount(requestedAccountForKey: UUID): PublicKey {
        return uuidToKeyMap[requestedAccountForKey] ?: throw IllegalStateException("No key registered for uuid: ${requestedAccountForKey}")
    }


    @Suspendable
    private inline fun <reified T : Any> flowAwareStartFlow(flowLogic: FlowLogic<T>): CordaFuture<T> {
        val currentFlow = FlowLogic.currentTopLevel
        return if (currentFlow != null) {
            val result = currentFlow.subFlow(flowLogic)
            doneFuture(result)
        } else {
            this.appServiceHub.startFlow(flowLogic).returnValue
        }
    }

}