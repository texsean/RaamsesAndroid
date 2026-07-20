package com.raamses.console

import android.app.Application
import com.raamses.console.data.MockDataProvider
import com.raamses.console.data.RaamsesGatewayClient

class RaamsesApp : Application() {
    val mockProvider = MockDataProvider()
    val gatewayClient = RaamsesGatewayClient()

    override fun onCreate() {
        super.onCreate()
        mockProvider.refresh()
    }
}
