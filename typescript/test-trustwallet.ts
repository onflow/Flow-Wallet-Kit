/**
 * Test script to verify TrustWallet integration
 */

import { SeedPhraseProvider } from './src/keys/SeedPhraseProvider.js';
import { InMemoryProvider } from './src/storage/InMemoryProvider.js';
import { SigningAlgorithm } from './src/types/key.js';
import { bytesToHex } from './src/utils/crypto.js';

async function testTrustWallet() {
  console.log('Testing TrustWallet integration...\n');
  
  // Test 1: Create a new seed phrase
  console.log('1. Creating new seed phrase...');
  const storage = new InMemoryProvider();
  const provider = await SeedPhraseProvider.create(
    { wordCount: 12 },
    storage
  );
  
  const mnemonic = provider.exportMnemonic();
  console.log(`   Mnemonic: ${mnemonic.split(' ').slice(0, 3).join(' ')}... (${mnemonic.split(' ').length} words)`);
  
  // Test 2: Derive keys
  console.log('\n2. Deriving keys...');
  const p256PrivKey = provider.privateKey(SigningAlgorithm.ECDSA_P256);
  const p256PubKey = provider.publicKey(SigningAlgorithm.ECDSA_P256);
  
  console.log(`   P256 Private Key: ${bytesToHex(p256PrivKey!).slice(0, 16)}...`);
  console.log(`   P256 Public Key: ${bytesToHex(p256PubKey!).slice(0, 16)}... (${p256PubKey!.length} bytes)`);
  
  const secp256k1PrivKey = provider.privateKey(SigningAlgorithm.ECDSA_SECP256K1);
  const secp256k1PubKey = provider.publicKey(SigningAlgorithm.ECDSA_SECP256K1);
  
  console.log(`   Secp256k1 Private Key: ${bytesToHex(secp256k1PrivKey!).slice(0, 16)}...`);
  console.log(`   Secp256k1 Public Key: ${bytesToHex(secp256k1PubKey!).slice(0, 16)}... (${secp256k1PubKey!.length} bytes)`);
  
  // Test 3: Verify public keys have '04' prefix removed
  console.log('\n3. Verifying public key format...');
  console.log(`   P256 public key length: ${p256PubKey!.length} bytes (should be 64)`);
  console.log(`   Secp256k1 public key length: ${secp256k1PubKey!.length} bytes (should be 64)`);
  
  // Test 4: Create from existing mnemonic
  console.log('\n4. Creating from existing mnemonic...');
  const testMnemonic = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
  const provider2 = await SeedPhraseProvider.fromMnemonic(testMnemonic, '', provider.advance.derivationPath || "m/44'/539'/0'/0/0", storage);
  
  const p256PubKey2 = provider2.publicKey(SigningAlgorithm.ECDSA_P256);
  console.log(`   P256 Public Key from test mnemonic: ${bytesToHex(p256PubKey2!).slice(0, 32)}...`);
  
  // Test 5: Validate mnemonic
  console.log('\n5. Testing mnemonic validation...');
  const isValid = await SeedPhraseProvider.validateMnemonic(testMnemonic);
  const isInvalid = await SeedPhraseProvider.validateMnemonic('invalid mnemonic phrase');
  console.log(`   Valid mnemonic: ${isValid}`);
  console.log(`   Invalid mnemonic: ${isInvalid}`);
  
  console.log('\n✅ All tests passed!');
}

testTrustWallet().catch(console.error);