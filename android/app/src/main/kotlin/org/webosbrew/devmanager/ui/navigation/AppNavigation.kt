package org.webosbrew.devmanager.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.webosbrew.devmanager.ui.apps.AppsScreen
import org.webosbrew.devmanager.ui.apps.AppsViewModel
import org.webosbrew.devmanager.ui.devices.DevicesScreen
import org.webosbrew.devmanager.ui.devices.DevicesViewModel
import org.webosbrew.devmanager.ui.files.FilesScreen
import org.webosbrew.devmanager.ui.files.FilesViewModel
import org.webosbrew.devmanager.ui.info.InfoScreen
import org.webosbrew.devmanager.ui.info.InfoViewModel
import org.webosbrew.devmanager.ui.terminal.TerminalScreen
import org.webosbrew.devmanager.ui.terminal.TerminalViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                        },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Apps.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Apps.route) {
                val vm: AppsViewModel = viewModel()
                AppsScreen(
                    viewModel = vm,
                    onNavigateToDevices = {
                        navController.navigate(Screen.Devices.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Files.route) {
                val vm: FilesViewModel = viewModel()
                FilesScreen(
                    viewModel = vm,
                    onNavigateToDevices = {
                        navController.navigate(Screen.Devices.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Terminal.route) {
                val vm: TerminalViewModel = viewModel()
                TerminalScreen(
                    viewModel = vm,
                    onNavigateToDevices = {
                        navController.navigate(Screen.Devices.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Info.route) {
                val vm: InfoViewModel = viewModel()
                InfoScreen(
                    viewModel = vm,
                    onNavigateToDevices = {
                        navController.navigate(Screen.Devices.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Devices.route) {
                val vm: DevicesViewModel = viewModel()
                DevicesScreen(viewModel = vm)
            }
        }
    }
}
