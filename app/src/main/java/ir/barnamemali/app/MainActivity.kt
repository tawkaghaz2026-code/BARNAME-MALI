package ir.barnamemali.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.barnamemali.app.ui.AccountsScreen
import ir.barnamemali.app.ui.AddTxnScreen
import ir.barnamemali.app.ui.AdjustmentsScreen
import ir.barnamemali.app.ui.AppColors
import ir.barnamemali.app.ui.AppTypography
import ir.barnamemali.app.ui.CategoriesScreen
import ir.barnamemali.app.ui.FamilyScreen
import ir.barnamemali.app.ui.HomeScreen
import ir.barnamemali.app.ui.LoanScreen
import ir.barnamemali.app.ui.LocalApp
import ir.barnamemali.app.ui.ObligationsScreen
import ir.barnamemali.app.ui.PersonScreen
import ir.barnamemali.app.ui.RecurringScreen
import ir.barnamemali.app.ui.ReportsScreen
import ir.barnamemali.app.ui.SettingsScreen
import ir.barnamemali.app.ui.SmsReviewScreen
import ir.barnamemali.app.ui.TxnsScreen
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var startRoute by mutableStateOf<String?>(null)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BarnameMaliApp
        unlocked = !app.repository.settings.lockEnabled
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            CompositionLocalProvider(LocalApp provides app, LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = AppColors, typography = AppTypography) {
                    if (unlocked) AppNav(startRoute) { startRoute = null } else LockScreen { authenticate() }
                }
            }
        }
        if (!unlocked) authenticate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_ROUTE)?.let { startRoute = it }
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val app = application as BarnameMaliApp
            app.appScope.launch {
                val ok = app.repository.ingestSms("اشتراک‌گذاری", text, System.currentTimeMillis(), notify = false)
                runOnUiThread {
                    if (ok) startRoute = ROUTE_SMS
                    else Toast.makeText(this@MainActivity, "مبلغی در این متن پیدا نشد", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("باز کردن برنامه مالی")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_HOME = "home"
        const val ROUTE_SMS = "sms"
        const val ROUTE_FAMILY = "family"
        const val ROUTE_OBLIGATIONS = "obligations"
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null)
        Text("برنامه قفل است", Modifier.padding(16.dp))
        Button(onClick = onUnlock) { Text("باز کردن") }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("home", "خانه", Icons.Default.Home),
    Tab("txns", "تراکنش‌ها", Icons.Default.List),
    Tab("family", "خانواده", Icons.Default.Face),
    Tab("obligations", "تعهدات", Icons.Default.DateRange),
    Tab("reports", "گزارش", Icons.Default.Star),
)

@Composable
private fun AppNav(startRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            nav.navigate(startRoute) { launchSingleTop = true }
            onRouteHandled()
        }
    }
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val showBar = tabs.any { it.route == route }
    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.switchTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (showBar) FloatingActionButton(onClick = { nav.navigate("add") }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "ثبت تراکنش", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(nav) }
            composable("txns") { TxnsScreen(nav) }
            composable("family") { FamilyScreen(nav) }
            composable("obligations") { ObligationsScreen(nav) }
            composable("reports") { ReportsScreen(nav) }
            composable("sms") { SmsReviewScreen(nav) }
            composable("accounts") { AccountsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("adjustments") { AdjustmentsScreen(nav) }
            composable("recurring") { RecurringScreen(nav) }
            composable("categories") { CategoriesScreen(nav) }
            composable("add?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
                AddTxnScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("person/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PersonScreen(nav, it.arguments!!.getLong("id"))
            }
            composable("loan/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                LoanScreen(nav, it.arguments!!.getLong("id"))
            }
        }
    }
}

fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}
