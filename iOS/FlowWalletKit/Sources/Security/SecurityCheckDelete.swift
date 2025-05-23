//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 7/4/2025.
//

import Foundation

public protocol SecurityCheckDelegate {
    func verify() async throws -> Bool
}