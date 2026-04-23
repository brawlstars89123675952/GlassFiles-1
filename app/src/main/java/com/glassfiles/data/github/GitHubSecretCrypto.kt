package com.glassfiles.data.github

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import java.nio.charset.StandardCharsets
import java.util.Arrays

object GitHubSecretCrypto {
    private val box by lazy {
        LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8) as Box.Native
    }

    fun encryptSecret(publicKeyBase64: String, value: String): String {
        val publicKey = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        require(Box.Checker.checkPublicKey(publicKey.size)) { "Invalid GitHub Actions public key" }

        val message = value.toByteArray(StandardCharsets.UTF_8)
        val cipher = ByteArray(message.size + Box.SEALBYTES)
        try {
            val ok = box.cryptoBoxSeal(cipher, message, message.size.toLong(), publicKey)
            check(ok) { "Failed to encrypt GitHub Actions secret" }
            return Base64.encodeToString(cipher, Base64.NO_WRAP)
        } finally {
            Arrays.fill(message, 0)
            Arrays.fill(cipher, 0)
        }
    }
}
