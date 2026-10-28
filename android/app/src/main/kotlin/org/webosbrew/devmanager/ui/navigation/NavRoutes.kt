package org.webosbrew.devmanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Apps : Screen("apps", "Apps", Icons.Default.Apps)
    data object Files : Screen("files", "Files", Icons.Default.Folder)
    data object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
    data object Info : Screen("info", "Info", Icons.Default.Info)
    data object Devices : Screen("devices", "Devices", Icons.Default.Devices)

    companion object {
        val bottomNavItems = listOf(Apps, Files, Terminal, Info, Devices)
    }
}
