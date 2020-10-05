# Cordapp showing how to build, sign and finalize transactions as different steps

```
 See workflows/src/integrationTest/kotlin/com/decoupled/testing/DriverBasedTest.kt for example of all features
```

## Decoupled Build/Sign/Finalize

### `com.decoupled.demo.services.AsyncSigningService`
This service allows a flow to submit a TransactionBuider that needs signing (by the local node) to be signed at later time
The service will hold a transaction and sign it once approved

### `com.decoupled.demo.services.AsyncFinalizationService`
This service allows a flow to submit a SignedTransaction that needs notarization and finalization at a later time.
The service will hold a transaction and submit it for notarization once approved

These two services allows the process of building, signing and finalizing a transaction to be performed as three discrete steps, 
where a flow/rpc invocation is used to move the transaction to the next state (Building -> Signing and Signing -> Finalizing)

## External Keys

### `com.decoupled.demo.services.ExternalKeyService` 
This service allows the user to create fresh ECDSA keys that are completely isolated from Corda. 
It allows also signing by keys that are held outside of Corda.
 
### `com.decoupled.demo.services.AdvancedAccountsService`
This service is used when registering a key generated outside of Corda with an account within corda. 

