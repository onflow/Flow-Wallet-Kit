//
//  File.swift
//
//
//  Created by Hao Fu on 27/8/2024.
//

import Foundation
import Flow

public enum FlowVM {
    case EVM
}

public protocol FlowVMProtocol {
    associatedtype VMAddress
    
    var vm: FlowVM { get }
    var address: VMAddress { get }
    var chainID: Flow.ChainID { get }
}
