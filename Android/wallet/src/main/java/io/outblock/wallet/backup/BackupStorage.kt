package io.outblock.wallet.backup

import android.content.Context
import io.outblock.wallet.storage.HardwareBackedStorage
import io.outblock.wallet.storage.StorageProtocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class BackupStorage(
    private val context: Context,
    private val storage: StorageProtocol = HardwareBackedStorage(context, "wallet_backup_key")
) {
    companion object {
        private const val BACKUP_DIR = "backups"
        private const val DEVICE_BACKUP_DIR = "device"
        private const val SEED_BACKUP_DIR = "seed"
        private const val BACKUP_METADATA_KEY = "backup_metadata"
    }

    private val deviceBackupDir = File(context.filesDir, "$BACKUP_DIR/$DEVICE_BACKUP_DIR")
    private val seedBackupDir = File(context.filesDir, "$BACKUP_DIR/$SEED_BACKUP_DIR")

    init {
        deviceBackupDir.mkdirs()
        seedBackupDir.mkdirs()
    }

    fun saveDeviceBackup(data: ByteArray): File {
        val backupFile = File(deviceBackupDir, "backup_${System.currentTimeMillis()}.dat")
        storage.set(backupFile.absolutePath, data)
        updateBackupMetadata(backupFile, BackupType.DEVICE)
        return backupFile
    }

    fun saveSeedBackup(seedPhrase: String): File {
        val backupFile = File(seedBackupDir, "seed_${System.currentTimeMillis()}.dat")
        storage.set(backupFile.absolutePath, seedPhrase.toByteArray())
        updateBackupMetadata(backupFile, BackupType.SEED)
        return backupFile
    }

    fun loadDeviceBackup(backupFile: File): ByteArray {
        return storage.get(backupFile.absolutePath)
            ?: throw IllegalStateException("Backup file not found: ${backupFile.absolutePath}")
    }

    fun loadSeedBackup(backupFile: File): String {
        return storage.get(backupFile.absolutePath)
            ?.let { String(it) }
            ?: throw IllegalStateException("Backup file not found: ${backupFile.absolutePath}")
    }

    fun getLatestDeviceBackup(): File? {
        return getLatestBackup(BackupType.DEVICE)
    }

    fun getLatestSeedBackup(): File? {
        return getLatestBackup(BackupType.SEED)
    }

    fun hasDeviceBackup(): Boolean {
        return getLatestDeviceBackup() != null
    }

    fun hasSeedBackup(): Boolean {
        return getLatestSeedBackup() != null
    }

    fun deleteAllBackups() {
        // Delete all backup files
        deviceBackupDir.deleteRecursively()
        seedBackupDir.deleteRecursively()
        deviceBackupDir.mkdirs()
        seedBackupDir.mkdirs()

        // Clear backup metadata
        storage.remove(BACKUP_METADATA_KEY)
    }

    private fun updateBackupMetadata(backupFile: File, type: BackupType) {
        val metadata = getBackupMetadata().toMutableMap()
        metadata[backupFile.absolutePath] = BackupMetadata(
            type = type,
            timestamp = System.currentTimeMillis(),
            path = backupFile.absolutePath
        )
        storage.set(BACKUP_METADATA_KEY, Json.encodeToString(metadata).toByteArray())
    }

    private fun getLatestBackup(type: BackupType): File? {
        val metadata = getBackupMetadata()
        return metadata.values
            .filter { it.type == type }
            .maxByOrNull { it.timestamp }
            ?.let { File(it.path) }
    }

    private fun getBackupMetadata(): Map<String, BackupMetadata> {
        return storage.get(BACKUP_METADATA_KEY)
            ?.let { String(it) }
            ?.let { Json.decodeFromString<Map<String, BackupMetadata>>(it) }
            ?: emptyMap()
    }

    private enum class BackupType {
        DEVICE,
        SEED
    }

    @kotlinx.serialization.Serializable
    private data class BackupMetadata(
        val type: BackupType,
        val timestamp: Long,
        val path: String
    )
} 