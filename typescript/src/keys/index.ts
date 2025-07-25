/**
 * Key management exports for Flow Wallet Kit
 */

export { BaseKeyProtocol } from './KeyProtocol.js';
export { PrivateKeyProvider } from './PrivateKeyProvider.js';
export { SeedPhraseProvider, FLOW_DEFAULT_PATH } from './SeedPhraseProvider.js';
export * from './curves.js';

// Re-export key types for convenience
export {
  KeyType,
  SigningAlgorithm,
  HashingAlgorithm,
  type KeyProtocol,
  type KeyCreationOptions,
  type SeedPhraseKeyOptions,
  type PrivateKeyOptions,
  type KeyStatic
} from '../types/key.js';