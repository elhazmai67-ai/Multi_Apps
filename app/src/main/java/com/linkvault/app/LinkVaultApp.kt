package com.linkvault.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.linkvault.app.ui.components.NeuFab
import com.linkvault.app.ui.components.neuInset
import com.linkvault.app.ui.components.neuRaised
import com.linkvault.app.ui.screens.*
import com.linkvault.app.ui.tools.ToolRouter
import com.linkvault.app.ui.tools.ToolsHubScreen
import com.linkvault.app.ui.theme.Neon
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.findFragmentActivity
import kotlinx.coroutines.launch

private data class NavTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf<NavTab>(
    NavTab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    NavTab("categories", "Categories", Icons.Outlined.Category, Icons.Filled.Category),
    NavTab("tools", "Tools", Icons.Outlined.Build, Icons.Filled.Build),
    NavTab("downloads", "Downloads", Icons.Outlined.Download, Icons.Filled.Download),
    NavTab("favorites", "Favorites", Icons.Outlined.StarBorder, Icons.Filled.Star),
    NavTab("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
)
private val tabRoutes = tabs.map { it.route }.toSet()

/** Soft bottom bar: the selected tab is pressed into the surface and its icon turns blue. */
@Composable
private fun NeonNavBar(selectedRoute: String?, onSelect: (String) -> Unit) {
    val barShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(Modifier.fillMaxWidth().neuRaised(barShape, 8.dp)) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(74.dp).padding(horizontal = 2.dp)) {
            tabs.forEach { t ->
                val selected = t.route == selectedRoute
                val iconColor by androidx.compose.animation.animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "nav-icon"
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = selected,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, role = androidx.compose.ui.semantics.Role.Tab,
                        onClick = { onSelect(t.route) }
                    ),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(42.dp).then(if (selected) Modifier.neuInset(androidx.compose.foundation.shape.CircleShape, 3.dp) else Modifier),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(if (selected) t.selectedIcon else t.icon, null, Modifier.size(22.dp), tint = iconColor)
                    }
                    Text(
                        t.label, maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LinkVaultApp(vm: MainViewModel, sharedSession: MutableState<Boolean>) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val settings by vm.settings.collectAsState()
    var searchSignal by remember { mutableIntStateOf(0) }

    fun goTab(r: String) {
        navController.navigate(r) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val navigate: (String) -> Unit = { r -> if (r in tabRoutes) goTab(r) else navController.navigate(r) }

    /** Leaves the Add screen. After a Share-sheet save, return to the app the user came from. */
    fun leaveAdd(saved: Boolean) {
        navController.popBackStack()
        if (sharedSession.value) {
            sharedSession.value = false
            if (saved) Toast.makeText(context, "Saved to LinkVault", Toast.LENGTH_SHORT).show()
            context.findFragmentActivity()?.moveTaskToBack(true)
        }
    }

    // Snackbar messages from anywhere in the app.
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
            }
        }
    }

    // Share target and launcher shortcuts.
    LaunchedEffect(vm.pendingNav) {
        when (val p = vm.pendingNav) {
            is PendingNav.Add -> navController.navigate(if (p.url != null) "add?url=" + android.net.Uri.encode(p.url) else "add") { launchSingleTop = true }
            PendingNav.Search -> { vm.homeFilters = Filters(); goTab("home"); searchSignal++ }
            PendingNav.Favorites -> goTab("favorites")
            PendingNav.Pinned -> { vm.homeFilters = Filters(pinnedOnly = true); goTab("home") }
            PendingNav.Downloads -> goTab("downloads")
            null -> {}
        }
        if (vm.pendingNav != null) vm.pendingNav = null
    }

    val isTab = route in tabRoutes
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTab) NeonNavBar(route) { r -> if (route != r) goTab(r) }
        },
        floatingActionButton = {
            if (route == "home" || route == "categories" || route == "favorites") {
                NeuFab(onClick = { navController.navigate("add") }, contentDescription = "Add link") { Icon(Icons.Outlined.Add, null) }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController, startDestination = "home",
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            // Tabs cross-fade; pushed screens (Add, Details, ...) slide in a short, soft distance.
            enterTransition = {
                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes)
                    fadeIn(tween(200, delayMillis = 60))
                else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }
            },
            exitTransition = {
                if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes)
                    fadeOut(tween(100))
                else fadeOut(tween(140))
            },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 12 } }
        ) {
            composable("home") { HomeScreen(vm, navigate, searchSignal) }
            composable("categories") { CategoriesScreen(vm, navigate) }
            composable("favorites") {
                LinksScreen(
                    vm, "Favorites", vm.favoritesFilters, { vm.favoritesFilters = it.copy(favoritesOnly = true) }, navigate,
                    emptyTitle = "No favorites yet", emptyBody = "Tap the star on any link, or swipe a link to the right, to keep it here."
                )
            }
            composable("settings") { SettingsScreen(vm, navigate) }

            composable(
                "add?url={url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                AddEditScreen(
                    vm, editId = null, initialUrl = entry.arguments?.getString("url"),
                    onBack = { leaveAdd(false) }, onSaved = { leaveAdd(true) },
                    onOpenExisting = { l -> navController.popBackStack(); navController.navigate("detail/${l.id}") }
                )
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                AddEditScreen(
                    vm, editId = entry.arguments?.getLong("id"), initialUrl = null,
                    onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() },
                    onOpenExisting = { l -> navController.popBackStack(); navController.navigate("detail/${l.id}") }
                )
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                DetailScreen(
                    vm, entry.arguments?.getLong("id") ?: -1L, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("edit/$it") }, onOpenDownloads = { goTab("downloads") }
                )
            }
            composable("category/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: UNCATEGORIZED
                val cats by vm.categories.collectAsState()
                var f by remember { mutableStateOf(Filters(categoryId = id)) }
                val name = if (id == UNCATEGORIZED) "Uncategorized" else cats.firstOrNull { it.id == id }?.name ?: "Category"
                LinksScreen(
                    vm, name, f, { f = it.copy(categoryId = id) }, navigate,
                    emptyTitle = "Nothing here yet", emptyBody = "Links you save in this category will appear here.",
                    onBack = { navController.popBackStack() }, lockCategory = true
                )
            }
            composable("tools") { ToolsHubScreen(navigate) }
            composable("tool/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                ToolRouter(entry.arguments?.getString("id").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable("downloads") { DownloadsScreen(vm) }
            composable("cleanup") { CleanupScreen(vm, navigate, onBack = { navController.popBackStack() }) }
            composable("manage_categories") { ManageCategoriesScreen(vm, onBack = { navController.popBackStack() }) }
            composable("manage_tags") {
                ManageTagsScreen(vm, onBack = { navController.popBackStack() }, onShowTag = { tag ->
                    vm.homeFilters = Filters(tag = tag); goTab("home")
                })
            }
            composable("backup") { DataScreen(vm, backupMode = true, onBack = { navController.popBackStack() }) }
            composable("transfer") { DataScreen(vm, backupMode = false, onBack = { navController.popBackStack() }) }
            composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
            composable("privacy_info") { PrivacyInfoScreen(onBack = { navController.popBackStack() }) }
        }
    }

    // Lock overlay: the app stays composed underneath so nothing is lost when relocking.
    val locked = settings?.appLockEnabled == true && !vm.unlocked
    if (locked) {
        BackHandler { /* back on the lock screen leaves the app via the default behaviour */ context.findFragmentActivity()?.moveTaskToBack(true) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LockScreen(vm) }
    }
    }
}
