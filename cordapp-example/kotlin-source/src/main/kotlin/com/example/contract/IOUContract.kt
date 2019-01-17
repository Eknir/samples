package com.example.contract

import com.example.state.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOU].
 *
 * For a new [IOU] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOU].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "com.example.contract.IOUContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<IOUContract.Commands>()
        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    // Generic constraints around the IOU issuance.
                    "No inputs should be consumed when issuing an IOU." using (tx.inputs.filterIsInstance<IOUState>().isEmpty())
                    "Only one output state should be created." using (tx.outputsOfType<IOUState>().size == 1)
                    val out = tx.outputsOfType<IOUState>().single()
                    "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
                    "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))
                    // IOU-specific constraints.
                    "The IOU's value must be non-negative." using (out.value > 0)
                }
            }

            is Commands.Destroy -> {
                requireThat {
                    // tx.inputsOfType(<IOUState>).single() throws if the amount of inputs != 0 and returns the input otherwise
                    val input = tx.inputsOfType<IOUState>().single()
                    "No output state should be created" using(tx.outputs.isEmpty())
                    "The transaction is signed by the lender" using(command.signers.contains(input.lender.owningKey))
                }
            }

            else -> throw IllegalArgumentException("Unrecognized command")
        }
    }

    /**
     * This contract implements two commands: Create and Destroy.
     */
    interface Commands : CommandData {
        class Create : Commands
        class Destroy: Commands
    }
}
