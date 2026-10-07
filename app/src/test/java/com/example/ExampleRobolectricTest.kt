package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.AndroidAppActionBridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarves AI", appName)
    }

    @Test
    fun `test action bridge availability`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = AndroidAppActionBridge(context)
        assertTrue(bridge.isNativeBridgeAvailable())
    }

    @Test
    fun `test openApp error handling for uninstalled app`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = AndroidAppActionBridge(context)
        val resultStr = bridge.openApp("SuperRandomFakeApp")
        val json = JSONObject(resultStr)

        assertFalse(json.getBoolean("success"))
        assertNotNull(json.optString("message"))
        val alternatives = json.optJSONArray("suggested_alternatives")
        assertNotNull(alternatives)
        assertTrue(alternatives!!.length() > 0)
    }

    @Test
    fun `test openWhatsApp returns proper json when not installed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = AndroidAppActionBridge(context)
        val resultStr = bridge.openWhatsApp()
        val json = JSONObject(resultStr)
        assertNotNull(json)
    }

    @Test
    fun `test makeCall generates dialer or call intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = AndroidAppActionBridge(context)
        val resultStr = bridge.makeCall("9876543210")
        val json = JSONObject(resultStr)

        assertTrue(json.getBoolean("success"))
        assertEquals("9876543210", json.getString("phone_number"))
    }
}
