#!/usr/bin/env bash
# Turns the release key into a page to print and keep, and back again.
#
#   scripts/key-backup.sh print     writes keys/key-backup.pdf (and .html) from keys/
#   scripts/key-backup.sh restore   rebuilds keys/tolly-release.jks from the scanned QR codes
#
# The page holds everything needed to sign Tolly: the keystore as numbered QR codes (base64 in
# pieces) and its password. Anyone with the page can publish updates as Tolly, so keep it like a
# passport: printed, at home, away from the laptop. Needs qrencode and chromium (`print`) or
# nothing but base64 (`restore`); on NixOS: nix shell nixpkgs#qrencode nixpkgs#zbar.
set -euo pipefail
cd "$(dirname "$0")/.."
keys=keys
store="$keys/tolly-release.jks"
props="$keys/tolly-release.properties"
chunk=1000 # characters of base64 per code: small enough to scan easily from paper

case "${1:-}" in
print)
  password=$(grep '^storePassword=' "$props" | cut -d= -f2-)
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  # fold leaves the last piece without a newline, which read would skip.
  { base64 -w0 "$store" | fold -w "$chunk"; echo; } > "$work/pieces"
  count=$(wc -l < "$work/pieces")
  codes=""
  n=0
  while IFS= read -r piece; do
    n=$((n + 1))
    printf '%s' "$piece" | qrencode -l M -s 4 -m 2 -t SVG -o "$work/$n.svg"
    codes+="<figure><div class=qr>$(cat "$work/$n.svg")</div><figcaption>Key, part $n of $count</figcaption></figure>"
  done < "$work/pieces"
  printf '%s' "$password" | qrencode -l M -s 4 -m 2 -t SVG -o "$work/pw.svg"
  fingerprint=$(keytool -list -v -keystore "$store" -storepass "$password" | sed -n 's/^\s*SHA256: //p' | head -1)
  sum=$(sha256sum "$store" | cut -d' ' -f1)
  cat > "$keys/key-backup.html" <<HTML
<!doctype html><html><head><meta charset=utf-8><title>Tolly release key</title><style>
@page { size: A4; margin: 12mm; }
body { font: 10pt system-ui, sans-serif; color: #000; }
h1 { font-size: 16pt; margin: 0 0 4px; }
p, li { margin: 3px 0; }
code { font: 9pt ui-monospace, monospace; word-break: break-all; }
.password { font: bold 15pt ui-monospace, monospace; letter-spacing: 1px; }
.grid { display: flex; flex-wrap: wrap; gap: 5mm 10mm; margin-top: 5mm; }
figure { margin: 0; width: 50mm; text-align: center; break-inside: avoid; }
.qr svg { width: 50mm; height: 50mm; }
figcaption { font-weight: 600; margin-top: 2px; }
</style></head><body>
<h1>Tolly release signing key</h1>
<p>Made $(date -u -r "$store" +%Y-%m-%d). Signs every Tolly release (GitHub and Google Play). Anyone holding this page can publish updates as Tolly: keep it private and safe.</p>
<p>Alias <b>tolly</b>. Keystore and key password:</p>
<p class=password>$password</p>
<p>Certificate SHA-256: <code>$fingerprint</code></p>
<p>Keystore file SHA-256: <code>$sum</code></p>
<p><b>Restore:</b> scan the key parts in order, paste each on its own line in <code>android/keys/pieces.txt</code>, then run <code>scripts/key-backup.sh restore</code> (or by hand: join the lines and decode them with <code>base64 -d &gt; tolly-release.jks</code>). The file's SHA-256 must match the one above.</p>
<div class=grid>$codes<figure><div class=qr>$(cat "$work/pw.svg")</div><figcaption>Password</figcaption></figure></div>
</body></html>
HTML
  chromium --headless=new --disable-gpu --no-pdf-header-footer --print-to-pdf="$PWD/$keys/key-backup.pdf" "file://$PWD/$keys/key-backup.html" 2>/dev/null
  chmod 600 "$keys/key-backup.html" "$keys/key-backup.pdf"
  echo "Wrote $keys/key-backup.pdf ($count key parts). Print it, then delete the PDF and HTML."
  ;;
restore)
  tr -d ' \r\n' < "$keys/pieces.txt" | base64 -d > "$store.restored"
  sha256sum "$store.restored"
  echo "Compare with the page; if it matches, rename it to $store."
  ;;
*)
  sed -n '2,10p' "$0"
  exit 1
  ;;
esac
