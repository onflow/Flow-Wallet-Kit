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
        .package(url: "https://github.com/outblock/flow-swift.git", .revisionItem("41e693b18dc941168492b79d623e392d4fb8c3a5")),
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
            dependencies: ["FlowWalletKit"],
            path: "iOS/FlowWalletKit/Tests"
        ),
    ]
)
