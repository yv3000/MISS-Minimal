package com.minimalist.launcher

import org.junit.Assert.*
import org.junit.Test

class RadioCommandsTest {
    @Test fun commandsAreFiniteAndUnknownIsNotOff() {
        assertNull(RadioCommands.decode(-1))
        assertNull(RadioCommands.decode(2))
        assertEquals(false, RadioCommands.decode(0))
        assertEquals(true, RadioCommands.decode(1))
        assertNull(RadioCommands.set("wifi; reboot", true, 34))
        assertEquals(listOf("cmd", "wifi", "start-softap", "AndroidHotspot", "wpa2", "12345678"), RadioCommands.set("hotspot", true, 34))
        assertEquals(listOf("cmd", "wifi", "stop-softap"), RadioCommands.set("hotspot", false, 34))
        assertEquals(listOf("svc", "data", "enable"), RadioCommands.set("data", true, 34))
        assertEquals(listOf("cmd", "bluetooth_manager", "disable"), RadioCommands.set("bluetooth", false, 34))
        assertEquals(listOf("svc", "bluetooth", "disable"), RadioCommands.set("bluetooth", false, 30))
        assertEquals(listOf("svc", "bluetooth", "enable"), RadioCommands.set("bluetooth", true, 31))
        assertEquals(listOf("svc", "bluetooth", "disable"), RadioCommands.set("bluetooth", false, 32))
        assertNull(RadioCommands.set("airplane", true, 27))
        assertEquals(listOf("cmd", "connectivity", "airplane-mode", "enable"), RadioCommands.set("airplane", true, 28))
        assertEquals(listOf("cmd", "wifi", "set-wifi-enabled", "enabled"), RadioCommands.set("wifi", true, 34))
        assertEquals(listOf("cmd", "location", "set-location-enabled", "false", "--user", "current"),
            RadioCommands.set("location", false, 34))
        assertEquals(listOf("cmd", "location", "set-location-enabled", "--user", "current", "true"),
            RadioCommands.set("location", true, 30))
        assertEquals(listOf("settings", "--user", "current", "put", "secure", "location_mode", "3"),
            RadioCommands.set("location", true, 29))
    }
}
