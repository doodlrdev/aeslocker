# AESLocker

AESLocker is an Android app for encrypting and decrypting files using AES-256.

All encryption and decryption takes place locally on the device. Your files are not uploaded to a server for encryption or decryption.

## Source Available

AESLocker's source code is publicly available so that users, developers, and security researchers can inspect how the app works, including its encryption and file-processing implementation.

AESLocker is licensed under the [PolyForm Strict License 1.0.0](https://polyformproject.org/licenses/strict/1.0.0).

## Free and Pro Versions

AESLocker is available in two Android app flavors: **Free** and **Pro**.

Both versions use the same underlying encryption and decryption implementation.

### Free

[Get AESLocker Free on Google Play](https://play.google.com/store/apps/details?id=com.doodlr.aeslocker)

The Free version provides the complete AESLocker functionality and is supported by advertising. It includes:

* **Google AdMob** for advertising
* **Google User Messaging Platform (UMP)** for consent management
* **Google Play Age Signals** for age-related signals

### Pro

[Get AESLocker Pro on Google Play](https://play.google.com/store/apps/details?id=com.doodlr.aeslocker.pro)

The Pro version provides the same encryption and decryption functionality without advertising.

It does not include **Google AdMob, Google User Messaging Platform (UMP), or Google Play Age Signals**.

The Pro version also includes a convenient link to this GitHub repository, allowing users to inspect the source code behind AESLocker.

## Encryption

AESLocker uses its own authenticated file format, built on standard AES-256:

* **AES-256-CBC** for encryption
* **HMAC-SHA256** for integrity/tamper detection, computed over the file header and ciphertext
* **PBKDF2-HMAC-SHA512**, 600,000 iterations, to derive key material from the password
* A single PBKDF2 call produces 64 bytes of key material, split into a 32-byte AES key and a separate 32-byte HMAC key
* A **16-byte random salt** and a **16-byte random IV**, both generated fresh per file and stored in the file header

The HMAC covers the header and ciphertext, so any corruption or tampering is cryptographically detected before a password is even considered "correct" - this is a deliberate improvement over inferring password correctness from PKCS7 padding validity alone, which is a weaker signal.

Encryption and decryption are performed locally on the device.

**Note:** this format is not compatible with the plain `openssl enc` CLI. See [Compatibility](#compatibility) below for how to encrypt or decrypt AESLocker files without the app.

## File Format

AESLocker encrypted files use AESLocker's own format (magic bytes `AESLOCK1`), not OpenSSL's `Salted__` format.

The encrypted file contains, in order:

| Field | Size | Description |
|---|---|---|
| Magic | 8 bytes | ASCII `AESLOCK1` |
| Salt | 16 bytes | Random, unique per file |
| IV | 16 bytes | Random, unique per file |
| Ciphertext | Variable | AES-256-CBC encrypted data (PKCS7 padded) |
| HMAC tag | 32 bytes | HMAC-SHA256 over (magic \|\| salt \|\| IV \|\| ciphertext) |

Because the full file layout, key derivation, and HMAC construction are documented here, encrypted files can still be decrypted independently of the AESLocker app itself - see below.

## Compatibility

AESLocker encrypted files can be decrypted on any device with a terminal, OpenSSL 3.0+, and the [`aeslocker_recover.sh`](./app/src/main/res/raw/aeslocker_recover.sh) script from this repository.

Unlike the older OpenSSL-compatible format AESLocker previously used, this format cannot be decrypted with a single `openssl enc` command - the HMAC verification step and the custom header require a short script rather than one CLI invocation. `aeslocker_recover.sh` implements the full format (see [File Format](#file-format) above) using only `openssl` and `dd`, so recovery never depends on the AESLocker app being installed, or even on this repository still existing once downloaded.

```text
./aeslocker_recover.sh encrypt <input_file> <output_file>
./aeslocker_recover.sh decrypt <input_file> <output_file>
```

No AESLocker installation or access to the AESLocker app's source code is required for decryption - the password and this script are sufficient. The same script (bundled with a full-instructions README.txt) is also downloadable directly from within the app itself, as a "Recovery Kit," so users don't need to find this repository to recover their files.

This provides a way to recover encrypted files independently of AESLocker, including on desktop and server environments, without relying on AESLocker's continued availability.