package com.glassfiles.security

object NativeSecurity {
    init {
        try { System.loadLibrary("glassfiles-security") }
        catch (_: UnsatisfiedLinkError) {}
    }

    /** Returns bitmask: 0x01=debugger, 0x02=frida, 0x04=xposed, 0x08=emulator */
    external fun nativeSecurityCheck(): Int

    /** Verify APK signature against embedded hash */
    external fun nativeVerifySignature(signatureBytes: ByteArray): Boolean

    /** Check APK file integrity */
    external fun nativeCheckIntegrity(apkPath: String): Boolean

    /** Check if system functions are hooked */
    external fun nativeCheckHooks(): Boolean
}
