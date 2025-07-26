/**
 * TrustWallet Core utilities and initialization
 */

import { initWasm } from '@trustwallet/wallet-core';

let wasmInitialized = false;
let wasmModule: Awaited<ReturnType<typeof initWasm>> | null = null;

/**
 * Initialize TrustWallet WASM module
 * This is safe to call multiple times - it will only initialize once
 */
export async function initTrustWallet(): Promise<Awaited<ReturnType<typeof initWasm>>> {
  if (!wasmInitialized || !wasmModule) {
    wasmModule = await initWasm();
    wasmInitialized = true;
  }
  return wasmModule;
}

/**
 * Get the TrustWallet module (must call initTrustWallet first)
 */
export function getTrustWallet(): Awaited<ReturnType<typeof initWasm>> {
  if (!wasmModule) {
    throw new Error('TrustWallet WASM not initialized. Call initTrustWallet() first.');
  }
  return wasmModule;
}

/**
 * Flow BIP44 derivation path
 * m/44'/539'/0'/0/0 where 539 is Flow's registered coin type
 */
export const FLOW_BIP44_PATH = "m/44'/539'/0'/0/0";

/**
 * Get Flow derivation path for a specific account index
 */
export function getFlowPath(index: number = 0): string {
  return `m/44'/539'/0'/0/${index}`;
}

/**
 * Remove the uncompressed public key prefix (0x04)
 */
export function removePublicKeyPrefix(publicKeyHex: string): string {
  return publicKeyHex.replace(/^04/, '');
}