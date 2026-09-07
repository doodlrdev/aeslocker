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

The Free version provides the complete AESLocker functionality and is supported by advertising. It includes:

* **Google AdMob** for advertising
* **Google User Messaging Platform (UMP)** for consent management
* **Google Play Age Signals** for age-related signals

### Pro

The Pro version provides the same encryption and decryption functionality without advertising.

It does not include **Google AdMob, Google User Messaging Platform (UMP), or Google Play Age Signals**.

The Pro version also includes a convenient link to this GitHub repository, allowing users to inspect the source code behind AESLocker.

## Encryption

AESLocker uses the following OpenSSL-compatible encryption scheme:

* **AES-256-CBC**
* **PBKDF2-HMAC-SHA256**
* **10,000 PBKDF2 iterations**
* **8-byte random salt**

The encryption and decryption operations are compatible with the following OpenSSL commands:

```text
openssl enc -aes-256-cbc -salt -pbkdf2 -in [input.ext] -out output.aes

openssl enc -d -aes-256-cbc -pbkdf2 -in input.aes -out [output.ext]
```

This allows files encrypted by AESLocker to be decrypted using OpenSSL on other devices without requiring AESLocker itself.

Encryption and decryption are performed locally on the device.

## File Format

AESLocker encrypted files use the OpenSSL `Salted__` format.

The encrypted file contains:

* The `Salted__` header
* An 8-byte random salt
* AES-256-CBC encrypted data

Because AESLocker uses the standard OpenSSL encryption format, encrypted files can be decrypted independently of AESLocker using OpenSSL and the user's password.

## Compatibility

AESLocker encrypted files can be decrypted using OpenSSL on any device that has a terminal and OpenSSL installed.

No AESLocker installation or access to the AESLocker source code is required for decryption. The password and the OpenSSL decryption command are sufficient.

```text
openssl enc -d -aes-256-cbc -pbkdf2 -in input.aes -out [output.ext]
```

This provides a simple way to recover encrypted files independently of AESLocker, including on desktop and server environments.
