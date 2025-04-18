// swift-tools-version: 5.5
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "FlowWalletKit",
    platforms: [
        .iOS(.v13),
    ],
    products: [
        .library(
            name: "FlowWalletKit",
            targets: ["FlowWalletKit"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/kishikawakatsumi/KeychainAccess", from: "4.2.2"),
        .package(url: "https://github.com/Outblock/flow-swift", .revisionItem("489177ca2f3fee133cf3c1ce7d6c0cc2892c34ef")),
        .package(url: "https://github.com/Outblock/wallet-core", .branchItem("master")),
    ],
    targets: [
        .target(
            name: "FlowWalletKit",
            dependencies: [
                "KeychainAccess",
                .product(name: "Flow", package: "flow-swift"),
                .product(name: "WalletCore", package: "wallet-core"),
                .product(name: "WalletCoreSwiftProtobuf", package: "wallet-core"),
            ],
            path: "iOS/FlowWalletKit/Sources"
        ),
        .testTarget(
            name: "FlowWalletKitTests",
            dependencies: [
                "FlowWalletKit",
                .product(name: "Flow", package: "flow-swift"),
                .product(name: "WalletCore", package: "wallet-core")
            ],
            path: "iOS/FlowWalletKit/Tests"
        ),
    ]
)
