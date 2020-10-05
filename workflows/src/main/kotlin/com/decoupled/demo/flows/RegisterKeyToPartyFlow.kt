package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import java.security.PublicKey

@StartableByService
@StartableByRPC
class RegisterKeyToPartyFlow(val publicKeyToRegister: PublicKey, val partyToRegisterTo: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        serviceHub.identityService.registerKey(publicKeyToRegister, partyToRegisterTo)
    }
}