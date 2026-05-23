package org.monfluo.wallet.lib

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class LibMonfluoOxideExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        System.loadLibrary("monfluo_oxide_jni")
    }
}
