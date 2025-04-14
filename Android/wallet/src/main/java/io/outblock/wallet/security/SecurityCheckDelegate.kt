package io.outblock.wallet.account

import kotlinx.coroutines.flow.Flow

/**
 * Interface for implementing security checks during signing operations
 */
interface SecurityCheckDelegate {
    /**
     * Verify if the security check passes
     * @return Flow<Boolean> indicating if the security check passed
     */
    suspend fun verify(): Flow<Boolean>
} 