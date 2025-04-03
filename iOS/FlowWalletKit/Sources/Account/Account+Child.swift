//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 2/4/2025.
//

import Foundation
import Flow

public struct ChildAccount: Codable, Identifiable {
    public var id: Flow.Address {
        address
    }
    
    public let address: Flow.Address
    public let network: Flow.ChainID
    public let name: String?
    public let description: String?
    public let icon: URL?
}
