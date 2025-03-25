//
//  Container.swift
//  FlowWalletKit
//
//  Created by Marty Ulrich on 3/24/25.
//

import Factory
import Foundation

extension Container {
    var keychainStorage: Factory<StorageProtocol> {
        self { KeychainStorage(service: Bundle.main.bundleIdentifier!, label: "keychain", synchronizable: false) }
    }
}
