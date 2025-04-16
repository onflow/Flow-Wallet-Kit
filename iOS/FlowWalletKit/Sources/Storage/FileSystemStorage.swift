/// FlowWalletKit - File System Storage Implementation
///
/// This module provides a file system-based storage implementation for caching data.
/// It uses iOS's FileManager to store data in the app's cache directory.

import Foundation

/// File system-based implementation of StorageProtocol
public class FileSystemStorage: StorageProtocol {
    // MARK: - Properties
    
    /// Base directory for storing cache files
    private let cacheDirectory: URL
    
    /// FileManager instance for file operations
    private let fileManager: FileManager
    
    /// All keys currently stored in the storage
    public var allKeys: [String] {
        do {
            let contents = try fileManager.contentsOfDirectory(at: cacheDirectory,
                                                             includingPropertiesForKeys: nil,
                                                             options: [])
            return contents.map { url in
                // Convert filename back to key by replacing underscores
                url.lastPathComponent
                    .replacingOccurrences(of: "_", with: " ")
            }
        } catch {
            return []
        }
    }
    
    // MARK: - Initialization
    
    /// Initialize with custom cache directory
    /// - Parameter directory: Custom directory URL (defaults to app's cache directory)
    public init(type: FileManager.SearchPathDirectory = .documentDirectory, directory: URL? = nil) {
        self.fileManager = FileManager.default
        
        if let directory = directory {
            self.cacheDirectory = directory
        } else {
            // Use the app's cache directory
            let cachePaths = fileManager.urls(for: type, in: .userDomainMask)
            let cacheDirectory = cachePaths[0].appendingPathComponent("FlowWalletKit", isDirectory: true)
            
            // Create directory if it doesn't exist
            try? fileManager.createDirectory(at: cacheDirectory,
                                          withIntermediateDirectories: true,
                                          attributes: nil)
            
            self.cacheDirectory = cacheDirectory
        }
    }
    
    // MARK: - StorageProtocol Implementation
    
    /// Find keys matching a keyword
    /// - Parameter keyword: Search term to match against keys
    /// - Returns: Array of matching keys
    /// - Throws: Storage access errors
    public func findKey(_ keyword: String) throws -> [String] {
        return allKeys.filter { key in
            key.localizedCaseInsensitiveContains(keyword)
        }
    }
    
    /// Store data with a given key
    /// - Parameters:
    ///   - key: Unique identifier for the data
    ///   - value: Data to store
    /// - Throws: Error if writing fails
    public func set(_ key: String, value: Data) throws {
        let fileURL = cacheDirectory.appendingPathComponent(sanitizeFilename(key))
        try value.write(to: fileURL, options: .atomic)
    }
    
    /// Retrieve data for a given key
    /// - Parameter key: Unique identifier for the data
    /// - Returns: Stored data if found, nil otherwise
    /// - Throws: Error if reading fails
    public func get(_ key: String) throws -> Data? {
        let fileURL = cacheDirectory.appendingPathComponent(sanitizeFilename(key))
        guard fileManager.fileExists(atPath: fileURL.path) else {
            return nil
        }
        return try Data(contentsOf: fileURL)
    }
    
    /// Remove data for a given key
    /// - Parameter key: Unique identifier for the data to remove
    /// - Throws: Error if deletion fails
    public func remove(_ key: String) throws {
        let fileURL = cacheDirectory.appendingPathComponent(sanitizeFilename(key))
        if fileManager.fileExists(atPath: fileURL.path) {
            try fileManager.removeItem(at: fileURL)
        }
    }
    
    /// Remove all stored data
    /// - Throws: Error if clearing fails
    public func removeAll() throws {
        let contents = try fileManager.contentsOfDirectory(at: cacheDirectory,
                                                         includingPropertiesForKeys: nil,
                                                         options: [])
        try contents.forEach { url in
            try fileManager.removeItem(at: url)
        }
    }
    
    // MARK: - Helper Methods
    
    /// Convert a key into a safe filename
    /// - Parameter filename: Original key
    /// - Returns: Sanitized filename safe for filesystem
    private func sanitizeFilename(_ filename: String) -> String {
        // Replace invalid characters with underscores
        let invalidCharacters = CharacterSet(charactersIn: ":/\\?%*|\"<>")
        return filename.components(separatedBy: invalidCharacters)
            .joined(separator: "_")
            .replacingOccurrences(of: " ", with: "_")
    }
    
    /// Get the size of cached data
    /// - Returns: Total size in bytes
    /// - Throws: Error if size calculation fails
    public func getCacheSize() throws -> UInt64 {
        let contents = try fileManager.contentsOfDirectory(at: cacheDirectory,
                                                         includingPropertiesForKeys: [.fileSizeKey],
                                                         options: [])
        return try contents.reduce(0) { total, url in
            let resourceValues = try url.resourceValues(forKeys: [.fileSizeKey])
            return total + UInt64(resourceValues.fileSize ?? 0)
        }
    }
    
    /// Check if a key exists in storage
    /// - Parameter key: Key to check
    /// - Returns: Whether the key exists
    public func exists(_ key: String) -> Bool {
        let fileURL = cacheDirectory.appendingPathComponent(sanitizeFilename(key))
        return fileManager.fileExists(atPath: fileURL.path)
    }
    
    /// Get the modification date of cached data
    /// - Parameter key: Key to check
    /// - Returns: Modification date if available
    /// - Throws: Error if date retrieval fails
    public func getModificationDate(_ key: String) throws -> Date? {
        let fileURL = cacheDirectory.appendingPathComponent(sanitizeFilename(key))
        guard fileManager.fileExists(atPath: fileURL.path) else {
            return nil
        }
        let attributes = try fileManager.attributesOfItem(atPath: fileURL.path)
        return attributes[.modificationDate] as? Date
    }
} 