package com.template.states

import com.template.contracts.SuperSimpleContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import java.security.PublicKey

// *********
// * State *
// *********
@BelongsToContract(SuperSimpleContract::class)
data class SuperSimpleStateWithOwner(
        val data: String,
        val currentOwner: PublicKey,
        val issuer: Party
) : ContractState {
    override val participants: List<AbstractParty>
        get() = {
            listOf(AnonymousParty(currentOwner))
        }()
}