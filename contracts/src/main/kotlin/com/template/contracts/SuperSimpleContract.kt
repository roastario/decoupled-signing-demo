package com.template.contracts

import com.template.states.SuperSimpleStateWithOwner
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class SuperSimpleContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        requireThat {
            "there must be one command for this contract" using (tx.commandsOfType<SuperSimpleContractCommands>().size == 1)

            val command = tx.commandsOfType<SuperSimpleContractCommands>().single()

            when (command.value) {
                is SuperSimpleContractCommands.Issue -> {
                    "there must be exactly 0 input states" using (tx.inputsOfType<SuperSimpleStateWithOwner>().isEmpty())
                    "there must be exactly 1 output states" using (tx.outputsOfType<SuperSimpleStateWithOwner>().size == 1)
                    val outputState = tx.outputsOfType<SuperSimpleStateWithOwner>().single()
                    "the issuer must be a required signer for issue" using (command.signers.contains(outputState.currentOwner))
                }
                is SuperSimpleContractCommands.Update -> {
                    val inputState = tx.inputsOfType<SuperSimpleStateWithOwner>().single()
                    "the owner must be a required signer" using (command.signers.contains(inputState.currentOwner))
                    "during update, there must be at most 1 output state" using (tx.outputsOfType<SuperSimpleStateWithOwner>().size <= 1)
                }
                SuperSimpleContractCommands.Move -> {
                    val inputState = tx.inputsOfType<SuperSimpleStateWithOwner>().single()
                    "the owner must be a required signer" using (command.signers.contains(inputState.currentOwner))
                    "during move, there must be at exactly 1 output state" using (tx.outputsOfType<SuperSimpleStateWithOwner>().size == 1)
                    val outputState = tx.outputsOfType<SuperSimpleStateWithOwner>().single()
                    "during move the output state must be equal to input state" using
                            (inputState.copy(currentOwner = outputState.currentOwner) == outputState)
                }
            }

        }


    }

    sealed class SuperSimpleContractCommands : CommandData {
        object Update : SuperSimpleContractCommands()
        object Issue : SuperSimpleContractCommands()
        object Move : SuperSimpleContractCommands()
    }
}