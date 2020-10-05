package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.AdvancedAccountsService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.utilities.getOrThrow
import java.security.PublicKey
import java.util.*

@StartableByService
@StartableByRPC
class RegisterKeyToAccountFlow(val publicKeyToRegister: PublicKey, val name: String) : FlowLogic<UUID>() {
    @Suspendable
    override fun call(): UUID {
        val advancedAccountsService = serviceHub.cordaService(AdvancedAccountsService::class.java)
        return advancedAccountsService.registerKeyToNewAccount(accountName = name, publicKey = publicKeyToRegister).getOrThrow()
    }
}