package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AdvancedAccountsService
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.SendKeyForAccount
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.flows.*
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*


@InitiatingFlow
@StartableByRPC
@StartableByService
class RequestStaticKeyForAccountFlow(val uuid: UUID) : FlowLogic<PublicKey>() {
    @Suspendable
    override fun call(): PublicKey {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accountInfo = accountService.accountInfo(uuid) ?: throw IllegalStateException("account ${uuid} not known")
        val host = accountInfo.state.data.host
        val session = initiateFlow(host)
        val foundRemotely = session.sendAndReceive<Boolean>(uuid).unwrap { it }
        if (!foundRemotely) {
            throw IllegalStateException("key request for account: ${uuid} rejected by known host")
        } else {
            val keyToUse = session.receive(PublicKey::class.java).unwrap { it }
            return keyToUse
        }
    }

}

@InitiatedBy(RequestKeyForAccount::class)
class SendStaticKeyForAccountFlow(otherSide: FlowSession) : SendKeyForAccount(otherSide) {
    @Suspendable
    override fun call() {
        // No need to do anything if the initiating node is us. We can generate a key locally.
        if (otherSide.counterparty == ourIdentity) {
            return
        }
        val requestedAccountForKey = otherSide.receive(UUID::class.java).unwrap { it }
        val existingAccountInfo = accountService.accountInfo(requestedAccountForKey)
        if (existingAccountInfo == null) {
            otherSide.send(false)
        } else {
            otherSide.send(true)
            val advancedAccountsService = serviceHub.cordaService(AdvancedAccountsService::class.java)
            val keyForAccount = advancedAccountsService.keyForAccount(requestedAccountForKey)
            otherSide.send(keyForAccount)
        }
    }
}