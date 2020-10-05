package com.decoupled.demo.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.*
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*

@CordaService
class ExternalKeyService(private val appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {

    private val SIGNATURE_SCHEME = Crypto.RSA_SHA256
    private val BOUNCYCASTLE: Provider = BouncyCastleProvider()

    private val hsm: MutableMap<String, PrivateKey> = mutableMapOf()

    @Suspendable
    fun freshECDSAKeyPair(): PublicKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(SIGNATURE_SCHEME.algorithmName, BOUNCYCASTLE)
        keyPairGenerator.initialize(SIGNATURE_SCHEME.keySize!!, newSecureRandom())
        val genKeyPair = keyPairGenerator.genKeyPair()
        hsm[genKeyPair.public.toStringShort()] = genKeyPair.private
        val decodedPublicKey = Crypto.decodePublicKey(Crypto.toSupportedPublicKey(genKeyPair.public).encoded)
        return decodedPublicKey
    }

    @Suspendable
    fun signWithKey(publicKey: PublicKey, dataToSign: ByteArray): ByteArray? {
        val privateKey = hsm[publicKey.toStringShort()] ?: return null
        val signer = Signature.getInstance(SIGNATURE_SCHEME.signatureName, BOUNCYCASTLE)
        signer.initSign(privateKey)
        signer.update(dataToSign)
        return signer.sign()
    }

    @Suspendable
    fun signWithExternalKey(wireTransaction: WireTransaction, publicKey: PublicKey): TransactionSignature? {
        val signatureMetadata = SignatureMetadata(appServiceHub.myInfo.platformVersion, SIGNATURE_SCHEME.schemeNumberID)
        val signableData = SignableData(wireTransaction.id, signatureMetadata)
        val signatureBytes = signWithKey(publicKey, signableData.serialize().bytes)
        return signatureBytes?.let { TransactionSignature(signatureBytes, publicKey, signableData.signatureMetadata) }
    }

    @Suspendable
    fun signWithExternalKey(signableData: SignableData, publicKey: PublicKey): TransactionSignature? {
        val signatureBytes = signWithKey(publicKey, signableData.serialize().bytes)
        return signatureBytes?.let { TransactionSignature(signatureBytes, publicKey, signableData.signatureMetadata) }
    }

    @Suspendable
    fun keysControlled(keysToCheck: Iterable<PublicKey>): Set<PublicKey> {
        return keysToCheck.filter { it.toStringShort() in hsm }.toSet()
    }


}