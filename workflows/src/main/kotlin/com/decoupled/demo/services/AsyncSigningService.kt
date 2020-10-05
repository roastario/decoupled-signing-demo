package com.decoupled.demo.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate

@CordaService
class AsyncSigningService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {

    private val futuresMap = Collections.synchronizedMap(mutableMapOf<SecureHash, Pair<CompletableFuture<SignedTransaction>, WireTransaction>>())

    @Suspendable
    fun registerTransactionForSigning(tx: TransactionBuilder): Pair<SecureHash, CompletableFuture<SignedTransaction>> {
        val wireTransaction = tx.toWireTransaction(appServiceHub)
        if (wireTransaction.timeWindow == null) {
            throw IllegalStateException("All transactions must have a time window attached to them")
        }
        if (wireTransaction.timeWindow?.untilTime == null) {
            throw IllegalStateException("All transactions must have an untilTime attached to them")
        }
        val completableFuture = CompletableFuture<SignedTransaction>()
        futuresMap[wireTransaction.id] = completableFuture to wireTransaction
        return wireTransaction.id to completableFuture
    }

    @Suspendable
    fun signTransaction(hash: SecureHash): SignedTransaction {
        val (future, wireTransaction) = (futuresMap[hash]
                ?: throw IllegalStateException("transaction with hash: $hash not found in pending transactions"))

        val ledgerTransaction = wireTransaction.toLedgerTransaction(appServiceHub)
        ledgerTransaction.verify()

        val externalKeyService = appServiceHub.cordaService(ExternalKeyService::class.java)
        val requiredSigningKeys = wireTransaction.requiredSigningKeys
        val cordaKeys = appServiceHub.keyManagementService.filterMyKeys(requiredSigningKeys)
        val externalKeys = externalKeyService.keysControlled(requiredSigningKeys)

        val cordaSigs = cordaKeys.map { appServiceHub.createSignature(wireTransaction.buildFilteredTransaction(Predicate { true })) }
        val externalSigs = externalKeys.mapNotNull { externalKeyService.signWithExternalKey(wireTransaction, it) }

        return SignedTransaction(wireTransaction, cordaSigs + externalSigs).also {
            future.thenRunAsync { futuresMap.remove(it.id) }
            future.complete(it)
        }
    }

    fun getPendingTransactions(): List<SecureHash> {
        return futuresMap.keys.toList()
    }

}