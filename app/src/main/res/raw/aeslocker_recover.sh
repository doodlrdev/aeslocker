#!/usr/bin/env bash
#
# aeslocker_recover.sh — encrypt/decrypt AESLocker files from the command
# line, without the AESLocker app.
#
# Full instructions (requirements, how to run this, what "chmod +x" means)
# are in README.txt, bundled alongside this script in the recovery kit you
# downloaded. Open README.txt in any text editor.
#
# Quick reference once the script is executable (see README.txt):
#   ./aeslocker_recover.sh encrypt <input_file> <output_file>
#   ./aeslocker_recover.sh decrypt <input_file> <output_file>

set -euo pipefail

OPENSSL_BIN="${OPENSSL_BIN:-openssl}"
MAGIC_HEX="4145534c4f434b31"   # "AESLOCK1" in hex
ITERATIONS=600000
OVERHEAD=72                    # 8 (magic) + 16 (salt) + 16 (iv) + 32 (hmac)

usage() {
    echo "Usage: $0 {encrypt|decrypt} <input_file> <output_file>" >&2
    exit 1
}

[ $# -eq 3 ] || usage
MODE="$1"; IN="$2"; OUT="$3"

[ -f "$IN" ] || { echo "Input file not found: $IN" >&2; exit 1; }

# Cross-platform file size (GNU stat vs BSD/macOS stat).
file_size() {
    stat -c%s "$1" 2>/dev/null || stat -f%z "$1"
}

# Derives AES key + HMAC key from a password (prompted) and a hex salt.
# Echoes "AES_KEY_HEX HMAC_KEY_HEX" on stdout.
derive_keys() {
    local salt_hex="$1"
    local pass
    read -r -s -p "Password: " pass
    echo >&2

    local material
    material=$("$OPENSSL_BIN" kdf -keylen 64 \
        -kdfopt digest:SHA512 \
        -kdfopt "pass:${pass}" \
        -kdfopt "hexsalt:${salt_hex}" \
        -kdfopt "iter:${ITERATIONS}" \
        PBKDF2 | tr -d ':\n')

    echo "${material:0:64} ${material:64:64}"
}

do_encrypt() {
    local salt_hex iv_hex aes_key hmac_key tmp_cipher tag_file
    salt_hex=$("$OPENSSL_BIN" rand -hex 16)
    iv_hex=$("$OPENSSL_BIN" rand -hex 16)

    read -r aes_key hmac_key <<< "$(derive_keys "$salt_hex")"

    tmp_cipher=$(mktemp)
    tag_file=$(mktemp)
    trap 'rm -f "$tmp_cipher" "$tag_file"' EXIT

    "$OPENSSL_BIN" enc -aes-256-cbc -K "$aes_key" -iv "$iv_hex" \
        -in "$IN" -out "$tmp_cipher"

    # Write magic + salt + iv + ciphertext:
    {
        echo -n "$MAGIC_HEX" | xxd -r -p
        echo -n "$salt_hex"  | xxd -r -p
        echo -n "$iv_hex"    | xxd -r -p
        cat "$tmp_cipher"
    } > "$OUT"

    # HMAC-SHA256 over everything written so far, appended as the trailer:
    "$OPENSSL_BIN" dgst -sha256 -mac HMAC -macopt "hexkey:${hmac_key}" \
        -binary "$OUT" > "$tag_file"
    cat "$tag_file" >> "$OUT"

    echo "Encrypted -> $OUT"
}

do_decrypt() {
    local size cipher_len magic_hex salt_hex iv_hex aes_key hmac_key
    local header_and_cipher_len computed_tag stored_tag

    size=$(file_size "$IN")
    (( size >= OVERHEAD )) || { echo "File too small — not a valid AESLocker file." >&2; exit 1; }
    cipher_len=$(( size - OVERHEAD ))

    magic_hex=$(dd if="$IN" bs=1 skip=0  count=8  2>/dev/null | xxd -p -c8)
    [ "$magic_hex" = "$MAGIC_HEX" ] || { echo "Bad magic — not an AESLocker file." >&2; exit 1; }

    salt_hex=$(dd if="$IN" bs=1 skip=8  count=16 2>/dev/null | xxd -p -c16)
    iv_hex=$(dd   if="$IN" bs=1 skip=24 count=16 2>/dev/null | xxd -p -c16)

    read -r aes_key hmac_key <<< "$(derive_keys "$salt_hex")"

    # --- Verify HMAC before trusting anything (checks password AND that
    #     the file hasn't been corrupted or tampered with) ---
    header_and_cipher_len=$(( 40 + cipher_len ))  # magic+salt+iv = 40 bytes
    computed_tag=$(dd if="$IN" bs=1 skip=0 count="$header_and_cipher_len" 2>/dev/null \
        | "$OPENSSL_BIN" dgst -sha256 -mac HMAC -macopt "hexkey:${hmac_key}" -binary \
        | xxd -p -c32)
    stored_tag=$(dd if="$IN" bs=1 skip="$header_and_cipher_len" count=32 2>/dev/null | xxd -p -c32)

    if [ "$computed_tag" != "$stored_tag" ]; then
        echo "HMAC verification failed — wrong password, or file is corrupted/tampered." >&2
        exit 1
    fi

    dd if="$IN" bs=1 skip=40 count="$cipher_len" 2>/dev/null \
        | "$OPENSSL_BIN" enc -d -aes-256-cbc -K "$aes_key" -iv "$iv_hex" -out "$OUT"

    echo "Decrypted -> $OUT (integrity verified)"
}

case "$MODE" in
    encrypt) do_encrypt ;;
    decrypt) do_decrypt ;;
    *) usage ;;
esac