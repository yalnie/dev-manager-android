package org.webosbrew.devmanager

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class DevManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register BouncyCastle for modern SSH cryptographic algorithms on Android
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
