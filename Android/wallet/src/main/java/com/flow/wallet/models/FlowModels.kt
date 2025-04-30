package com.flow.wallet.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

@Serializable
enum class SerializableSigningAlgorithm {
    @SerialName("ECDSA_P256")
    ECDSA_P256,
    @SerialName("ECDSA_secp256k1")
    ECDSA_SECP256K1;

    fun toFlowSigningAlgorithm(): SigningAlgorithm {
        return when (this) {
            ECDSA_P256 -> SigningAlgorithm.ECDSA_P256
            ECDSA_SECP256K1 -> SigningAlgorithm.ECDSA_secp256k1
        }
    }

    companion object {
        fun fromFlowSigningAlgorithm(algorithm: SigningAlgorithm): SerializableSigningAlgorithm {
            return when (algorithm) {
                SigningAlgorithm.ECDSA_P256 -> ECDSA_P256
                SigningAlgorithm.ECDSA_secp256k1 -> ECDSA_SECP256K1
            }
        }
    }
}

@Serializable
enum class SerializableHashingAlgorithm {
    @SerialName("SHA2_256")
    SHA2_256,
    @SerialName("SHA3_256")
    SHA3_256;

    fun toFlowHashingAlgorithm(): HashingAlgorithm {
        return when (this) {
            SHA2_256 -> HashingAlgorithm.SHA2_256
            SHA3_256 -> HashingAlgorithm.SHA3_256
        }
    }

    companion object {
        fun fromFlowHashingAlgorithm(algorithm: HashingAlgorithm): SerializableHashingAlgorithm {
            return when (algorithm) {
                HashingAlgorithm.SHA2_256 -> SHA2_256
                HashingAlgorithm.SHA3_256 -> SHA3_256
                HashingAlgorithm.UNKNOWN -> TODO()
                HashingAlgorithm.SHA2_384 -> TODO()
                HashingAlgorithm.SHA3_384 -> TODO()
                HashingAlgorithm.KMAC128_BLS_BLS12_381 -> TODO()
            }
        }
    }
} 