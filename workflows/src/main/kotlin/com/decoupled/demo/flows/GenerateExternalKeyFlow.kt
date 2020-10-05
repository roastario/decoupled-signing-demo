package com.decoupled.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.decoupled.demo.services.ExternalKeyService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import java.security.PublicKey

@StartableByService
@StartableByRPC
class GenerateExternalKeyFlow : FlowLogic<PublicKey>() {

    @Suspendable
    override fun call(): PublicKey {
        val keyService = serviceHub.cordaService(ExternalKeyService::class.java)
        return keyService.freshECDSAKeyPair()
    }
}