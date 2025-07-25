/**
 * Elliptic curve utilities for Flow Wallet Kit
 * Provides support for P256 and secp256k1 curves
 */

import { p256 } from '@noble/curves/p256';
import { secp256k1 } from '@noble/curves/secp256k1';
import { sha256 } from '@noble/hashes/sha256';
import { sha3_256 } from '@noble/hashes/sha3';
import type { ProjPointType } from '@noble/curves/abstract/weierstrass';
import { SigningAlgorithm, HashingAlgorithm } from '../types/key.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { hexToBytes, bytesToHex } from '../utils/crypto.js';

/**
 * Get the elliptic curve implementation for a signing algorithm
 */
export function getCurve(signAlgo: SigningAlgorithm) {
  switch (signAlgo) {
    case SigningAlgorithm.ECDSA_P256:
      return p256;
    case SigningAlgorithm.ECDSA_SECP256K1:
      return secp256k1;
    default:
      throw new WalletError(
        WalletErrorCode.UnsupportedSignatureAlgorithm,
        `Unsupported signing algorithm: ${signAlgo}`
      );
  }
}

/**
 * Get the hash function for a hashing algorithm
 */
export function getHasher(hashAlgo: HashingAlgorithm) {
  switch (hashAlgo) {
    case HashingAlgorithm.SHA2_256:
      return sha256;
    case HashingAlgorithm.SHA3_256:
      return sha3_256;
    default:
      throw new WalletError(
        WalletErrorCode.UnsupportedHashAlgorithm,
        `Unsupported hashing algorithm: ${hashAlgo}`
      );
  }
}

/**
 * Generate a new private key for the specified curve
 */
export function generatePrivateKey(signAlgo: SigningAlgorithm): Uint8Array {
  const curve = getCurve(signAlgo);
  return curve.utils.randomPrivateKey();
}

/**
 * Derive public key from private key
 */
export function derivePublicKey(privateKey: Uint8Array, signAlgo: SigningAlgorithm): Uint8Array {
  const curve = getCurve(signAlgo);
  const point = curve.getPublicKey(privateKey, false); // uncompressed
  return new Uint8Array(point);
}

/**
 * Get compressed public key
 */
export function getCompressedPublicKey(privateKey: Uint8Array, signAlgo: SigningAlgorithm): Uint8Array {
  const curve = getCurve(signAlgo);
  const point = curve.getPublicKey(privateKey, true); // compressed
  return new Uint8Array(point);
}

/**
 * Validate a private key
 */
export function isValidPrivateKey(privateKey: Uint8Array, signAlgo: SigningAlgorithm): boolean {
  try {
    const curve = getCurve(signAlgo);
    return curve.utils.isValidPrivateKey(privateKey);
  } catch {
    return false;
  }
}

/**
 * Sign a message with a private key
 * @param message Message to sign (will be hashed if not already)
 * @param privateKey Private key to sign with
 * @param signAlgo Signature algorithm
 * @param hashAlgo Hash algorithm
 * @param prehashed Whether the message is already hashed
 * @returns DER-encoded signature
 */
export function sign(
  message: Uint8Array,
  privateKey: Uint8Array,
  signAlgo: SigningAlgorithm,
  hashAlgo: HashingAlgorithm,
  prehashed: boolean = false
): Uint8Array {
  const curve = getCurve(signAlgo);
  const hasher = getHasher(hashAlgo);
  
  // Hash the message if not already hashed
  const msgHash = prehashed ? message : hasher(message);
  
  // Sign the message
  const signature = curve.sign(msgHash, privateKey);
  
  // Return DER-encoded signature
  return signature.toDERRawBytes();
}

/**
 * Verify a signature
 * @param signature DER-encoded signature
 * @param message Original message
 * @param publicKey Public key
 * @param signAlgo Signature algorithm
 * @param hashAlgo Hash algorithm
 * @param prehashed Whether the message is already hashed
 * @returns Whether the signature is valid
 */
export function verify(
  signature: Uint8Array,
  message: Uint8Array,
  publicKey: Uint8Array,
  signAlgo: SigningAlgorithm,
  hashAlgo: HashingAlgorithm,
  prehashed: boolean = false
): boolean {
  try {
    const curve = getCurve(signAlgo);
    const hasher = getHasher(hashAlgo);
    
    // Hash the message if not already hashed
    const msgHash = prehashed ? message : hasher(message);
    
    // Verify the signature
    return curve.verify(signature, msgHash, publicKey);
  } catch {
    return false;
  }
}

/**
 * Convert private key to various formats
 */
export function privateKeyToHex(privateKey: Uint8Array): string {
  return bytesToHex(privateKey);
}

export function privateKeyFromHex(hex: string): Uint8Array {
  const bytes = hexToBytes(hex);
  if (bytes.length !== 32) {
    throw new WalletError(
      WalletErrorCode.InvalidPrivateKey,
      'Private key must be 32 bytes'
    );
  }
  return bytes;
}

/**
 * Convert public key to various formats
 */
export function publicKeyToHex(publicKey: Uint8Array): string {
  return bytesToHex(publicKey);
}

export function publicKeyFromHex(hex: string): Uint8Array {
  return hexToBytes(hex);
}

/**
 * Get the public key point from a public key bytes
 */
export function getPublicKeyPoint(publicKey: Uint8Array, signAlgo: SigningAlgorithm): ProjPointType<bigint> {
  const curve = getCurve(signAlgo);
  return curve.ProjectivePoint.fromHex(publicKey);
}

/**
 * Normalize signature format (handle low-S malleability)
 */
export function normalizeSignature(signature: Uint8Array, signAlgo: SigningAlgorithm): Uint8Array {
  const curve = getCurve(signAlgo);
  const sig = curve.Signature.fromDER(signature);
  
  // Normalize to low-S format
  if (sig.hasHighS()) {
    sig.normalizeS();
  }
  
  return sig.toDERRawBytes();
}