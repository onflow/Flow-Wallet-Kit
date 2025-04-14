package io.outblock.wallet.backup

import io.outblock.wallet.keys.WalletKey

/**
 * Protocol defining the rules and procedures for wallet backups
 */
interface BackupProtocol {
    /**
     * Backup types supported by the protocol
     */
    enum class BackupType {
        DEVICE,
        SEED_PHRASE
    }

    /**
     * Backup status indicating the state of a backup operation
     */
    data class BackupStatus(
        val type: BackupType,
        val timestamp: Long,
        val size: Long,
        val checksum: String,
        val isEncrypted: Boolean,
        val version: Int = CURRENT_VERSION
    ) {
        companion object {
            const val CURRENT_VERSION = 1
        }
    }

    /**
     * Creates a backup following the protocol rules
     * @param type The type of backup to create
     * @return Result containing the backup status
     */
    suspend fun createBackup(type: BackupType): Result<BackupStatus>

    /**
     * Restores a wallet from a backup following the protocol rules
     * @param type The type of backup to restore from
     * @return Result containing the restored wallet key
     */
    suspend fun restoreBackup(type: BackupType): Result<WalletKey>

    /**
     * Verifies the integrity of a backup
     * @param type The type of backup to verify
     * @return Result indicating whether the backup is valid
     */
    suspend fun verifyBackup(type: BackupType): Result<Boolean>

    /**
     * Gets the status of the latest backup
     * @param type The type of backup to check
     * @return The backup status, or null if no backup exists
     */
    suspend fun getBackupStatus(type: BackupType): BackupStatus?

    /**
     * Checks if a backup exists and is valid
     * @param type The type of backup to check
     * @return true if a valid backup exists, false otherwise
     */
    suspend fun hasValidBackup(type: BackupType): Boolean
} 