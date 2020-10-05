package com.decoupled.demo.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import java.util.concurrent.CompletableFuture

@CordaService
class AsyncFinalizationService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {

    private val futuresMap = mutableMapOf<SecureHash, Pair<CompletableFuture<SignedTransaction>, SignedTransaction>>()

    @Suspendable
    fun registerTransactionForFinalization(signedTransaction: SignedTransaction) {
        synchronized(this) {
            futuresMap[signedTransaction.id] = CompletableFuture<SignedTransaction>() to signedTransaction
        }
    }

    @Suspendable
    fun getTransactionForFinalization(secureHash: SecureHash): SignedTransaction {
        val (completableFuture, signedTransaction) = synchronized(this) {
            futuresMap[secureHash]
                    ?: throw IllegalStateException("transaction $secureHash is not registered for finalization")
        }
        return signedTransaction
    }


}