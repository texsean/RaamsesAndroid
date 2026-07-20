package com.raamses.console.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.raamses.console.data.MockDataProvider
import com.raamses.console.data.RaamsesGatewayClient
import com.raamses.console.data.models.*
import com.raamses.console.ui.agents.AgentDetailScreen
import com.raamses.console.ui.alerts.AlertsScreen
import com.raamses.console.ui.dashboard.DashboardScreen
import com.raamses.console.ui.gateway.ConnectionScreen
import com.raamses.console.ui.gateway.GatewayScreen
import com.raamses.console.ui.theme.*
import java.util.UUID

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Gateway : Screen("gateway", "Gateway", Icons.Default.Terminal)
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Alerts : Screen("alerts", "Alerts", Icons.Default.Notifications)
    data object AgentDetail : Screen("agent/{agentId}", "Agent", Icons.Default.Computer) {
        fun createRoute(agentId: String) = "agent/$agentId"
    }
    data object Connection : Screen("connection", "Connection", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Gateway, Screen.Dashboard, Screen.Alerts)

@Composable
fun RaamsesNavHost(
    mockProvider: MockDataProvider,
    gatewayClient: RaamsesGatewayClient,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val now = System.currentTimeMillis() / 1000

    // Collect state
    val agents by gatewayClient.agents.collectAsState()
    val alerts by gatewayClient.alerts.collectAsState()
    val serverHealth by gatewayClient.serverHealth.collectAsState()
    val connectionState by gatewayClient.connectionState.collectAsState()
    val gatewayMessages by gatewayClient.gatewayMessages.collectAsState()

    // Initialize with mock data if no connection
    LaunchedEffect(Unit) {
        mockProvider.refresh()
        // Pre-load mock data into gateway
    }
    val displayAgents = agents.ifEmpty { mockProvider.agents.value }
    val displayAlerts = alerts.ifEmpty { mockProvider.alerts.value }
    val displayHealth = if (serverHealth.agentCount == 0) mockProvider.serverHealth.value else serverHealth
    val displayMessages = gatewayMessages.ifEmpty {
        listOf(
            GatewayMessage("welcome", "RAAMSES Console ready. Connect to a server or use /help for commands.", false, now, null),
            GatewayMessage("tip1", "Tip: Type /agents to list agents, /status for health, /alerts for active alerts.", false, now - 10, null),
            GatewayMessage("tip2", "Tip: Use Quick Connect presets to auto-fill connection settings.", false, now - 20, null)
        )
    }

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Surface, contentColor = TextPrimary) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Gateway.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentBlue,
                                selectedTextColor = AccentBlue,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentBlue.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Gateway.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Gateway Tab ──
            composable(Screen.Gateway.route) {
                GatewayScreen(
                    messages = displayMessages,
                    onSendCommand = { cmd -> gatewayClient.sendCommand(cmd) },
                    isConnected = connectionState.connected,
                    serverHost = connectionState.host,
                    onConnectClick = { navController.navigate(Screen.Connection.route) }
                )
            }

            // ── Dashboard Tab ──
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    agents = displayAgents,
                    serverHealth = displayHealth,
                    alerts = displayAlerts,
                    connectionState = connectionState,
                    onAgentClick = { agentId ->
                        navController.navigate(Screen.AgentDetail.createRoute(agentId))
                    }
                )
            }

            // ── Alerts Tab ──
            composable(Screen.Alerts.route) {
                AlertsScreen(
                    alerts = displayAlerts,
                    onAck = { /* TODO: send /ack to gateway */ }
                )
            }

            // ── Agent Detail ──
            composable(
                route = Screen.AgentDetail.route,
                arguments = listOf(navArgument("agentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val agentId = backStackEntry.arguments?.getString("agentId") ?: return@composable
                val agent = displayAgents.find { it.agentId == agentId }
                if (agent != null) {
                    AgentDetailScreen(agent = agent, onBack = { navController.popBackStack() })
                }
            }

            // ── Connection Screen ──
            composable(Screen.Connection.route) {
                ConnectionScreen(
                    currentConfig = GatewayConnection(),
                    isConnected = connectionState.connected,
                    onConnect = { config -> gatewayClient.connect(config) },
                    onDisconnect = { gatewayClient.disconnect() },
                    onDismiss = { navController.popBackStack() }
                )
            }
        }
    }
}
