/**
 * Wallet module exports
 */

export { Wallet } from './Wallet.js';
export { KeyWallet } from './KeyWallet.js';
export { WatchWallet } from './WatchWallet.js';
export { Account } from './Account.js';

// Re-export types
export type {
  Wallet as IWallet,
  KeyWallet as IKeyWallet,
  WatchWallet as IWatchWallet,
  Account as IAccount,
  WalletType,
  WalletState,
  WalletConfiguration,
  ChildAccount,
  COA,
  TokenBalance,
  SecurityCheckDelegate
} from '../types/wallet.js';

export { WalletType, WalletState } from '../types/wallet.js';

// Factory functions
import type { ChainId } from '../types/network.js';
import type { KeyProtocol } from '../types/key.js';
import type { WalletConfiguration } from '../types/wallet.js';
import { KeyWallet } from './KeyWallet.js';
import { WatchWallet } from './WatchWallet.js';

/**
 * Create a key wallet
 */
export async function createKeyWallet(
  key: KeyProtocol,
  networks: ChainId[],
  configuration?: WalletConfiguration
): Promise<KeyWallet> {
  return KeyWallet.create(key, networks, configuration);
}

/**
 * Create a watch wallet
 */
export async function createWatchWallet(
  address: string,
  networks: ChainId[],
  configuration?: WalletConfiguration
): Promise<WatchWallet> {
  return WatchWallet.create(address, networks, configuration);
}