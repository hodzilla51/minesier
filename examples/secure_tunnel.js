// A minimal authenticated encrypted tunnel over one NIC.
// Generate a key pair on each endpoint with crypto.x25519KeyPair(), exchange only
// publicKey values, then paste the keys and peer MAC below on each endpoint.

var nic = net.nic("back");
var peerMac = "02:12:34:56:78:9a";
var privateKey = "paste-local-private-key";
var peerPublicKey = "paste-peer-public-key";

var sharedSecret = crypto.x25519SharedSecret(privateKey, peerPublicKey);
var tunnelKey = crypto.hkdfSha256(sharedSecret, "", "minesier-vpn-v1", 32);
var aad = "minesier-vpn-v1";

nic.onReceive(function (frame) {
  if (frame.source !== peerMac || !frame.data.startsWith("vpn1|")) return;

  var fields = frame.data.split("|", 3);
  var plaintext = crypto.aesGcmDecrypt(tunnelKey, fields[1], fields[2], aad);
  if (plaintext === null) {
    print("dropped unauthenticated tunnel frame");
    return;
  }
  print("tunnel:", plaintext);
});

function sendTunnel(plaintext) {
  var encrypted = crypto.aesGcmEncrypt(tunnelKey, plaintext, aad);
  return nic.send(peerMac, "vpn1|" + encrypted.nonce + "|" + encrypted.ciphertext);
}

print("secure tunnel listening");
