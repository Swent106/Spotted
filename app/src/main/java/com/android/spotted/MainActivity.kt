package com.android.spotted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.spotted.resources.C
import com.android.spotted.ui.MainViewModel
import com.android.spotted.ui.StartDestination
import com.android.spotted.ui.addpets.AddPetsScreen
import com.android.spotted.ui.authentification.SignInScreen
import com.android.spotted.ui.home.HomeScreen
import com.android.spotted.ui.personalinfo.PersonalInfoScreen
import com.android.spotted.ui.report.ReportMissingScreen
import com.android.spotted.ui.theme.SampleAppTheme

object Route {
  const val SIGNIN = "signin"
  const val PERSONAL_INFO = "personal_info"
  const val ADD_PETS = "add_pets"
  const val HOME = "home"
  const val REPORT_MISSING = "report_missing"
  const val ALERT_ID = "alertId"
  const val ALERT_DETAIL = "alert/{$ALERT_ID}"
  const val REPORT_SIGHTING = "report_sighting/{$ALERT_ID}"
  /** Add pets opened from Home: not an onboarding step, it goes back to Home when done. */
  const val ADD_PET = "add_pet"

  fun alertDetail(alertId: String) = "alert/$alertId"

  fun reportSighting(alertId: String) = "report_sighting/$alertId"
}

class MainActivity : ComponentActivity() {

  private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          SpottedApp(viewModel = mainViewModel)
        }
      }
    }
  }
}

@Composable
fun SpottedApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
  val destination by viewModel.uiState.collectAsState()
  val navController = rememberNavController()

  LaunchedEffect(destination) {
    val targetRoute =
        when (destination) {
          StartDestination.SIGNIN -> Route.SIGNIN
          StartDestination.PERSONAL_INFO -> Route.PERSONAL_INFO
          StartDestination.HOME -> Route.HOME
        }
    navController.navigate(targetRoute) {
      popUpTo(0) { inclusive = true }
      launchSingleTop = true
    }
  }

  NavHost(
      navController = navController,
      startDestination = Route.SIGNIN,
      modifier = modifier.fillMaxSize(),
  ) {
    composable(Route.SIGNIN) {
      // No navigation here: once signed in, MainViewModel picks Home or Personal info depending
      // on whether the profile exists, and the LaunchedEffect above navigates there
      SignInScreen()
    }
    composable(Route.PERSONAL_INFO) {
      // Personal info stays below Add pets, so Back from Add pets returns to it
      PersonalInfoScreen(onSaved = { navController.navigate(Route.ADD_PETS) })
    }
    addPetsDestination(navController)
    homeDestinations(navController)
  }
}

/**
 * The Add pets onboarding step. [addPetsContent] draws the screen; tests replace it to avoid the
 * real ViewModel and Firebase.
 */
fun NavGraphBuilder.addPetsDestination(
    navController: NavController,
    addPetsContent: @Composable (onBack: () -> Unit, onDone: () -> Unit) -> Unit =
        { onBack, onDone ->
          AddPetsScreen(onBack = onBack, onDone = onDone)
        },
) {
  composable(Route.ADD_PETS) {
    addPetsContent(
        { navController.popBackStack() },
        {
          // Onboarding is over: Back from Home must not return to it
          navController.navigate(Route.HOME) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
          }
        },
    )
  }
}

/**
 * Home and the screens it opens. [homeContent] draws Home, [reportMissingContent] the Report
 * missing screen and [addPetContent] Add pets opened from Home; tests replace them to use
 * hand-built states instead of the real ViewModels.
 */
fun NavGraphBuilder.homeDestinations(
    navController: NavController,
    homeContent: @Composable (HomeNavigation) -> Unit = { navigation ->
      HomeScreen(
          onReportMissingClick = navigation.onReportMissingClick,
          onAlertClick = navigation.onAlertClick,
          onSeenPetClick = navigation.onSeenPetClick,
          onAddPetClick = navigation.onAddPetClick,
      )
    },
    reportMissingContent: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
      ReportMissingScreen(onBack = onDone, onPublished = onDone)
    },
    addPetContent: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
      AddPetsScreen(onBack = onDone, onDone = onDone, isOnboarding = false)
    },
) {
  composable(Route.HOME) {
    homeContent(
        HomeNavigation(
            onReportMissingClick = { navController.navigate(Route.REPORT_MISSING) },
            onAlertClick = { alertId -> navController.navigate(Route.alertDetail(alertId)) },
            onSeenPetClick = { alertId -> navController.navigate(Route.reportSighting(alertId)) },
            onAddPetClick = { navController.navigate(Route.ADD_PET) },
        )
    )
  }
  composable(Route.ADD_PET) { addPetContent { navController.popBackStack() } }
  composable(Route.REPORT_MISSING) { reportMissingContent { navController.popBackStack() } }
  composable(Route.ALERT_DETAIL) { entry ->
    AlertDetailScreenPlaceholder(entry.arguments?.getString(Route.ALERT_ID).orEmpty())
  }
  composable(Route.REPORT_SIGHTING) { entry ->
    ReportSightingScreenPlaceholder(entry.arguments?.getString(Route.ALERT_ID).orEmpty())
  }
}

/** Where Home can send the user. */
data class HomeNavigation(
    val onReportMissingClick: () -> Unit,
    val onAlertClick: (alertId: String) -> Unit,
    val onSeenPetClick: (alertId: String) -> Unit,
    val onAddPetClick: () -> Unit = {},
)

/** Shown until the Alert detail screen exists (S2-09). */
@Composable
fun AlertDetailScreenPlaceholder(alertId: String) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = "Alert $alertId",
        modifier = Modifier.semantics { testTag = "alert_detail_screen" },
    )
  }
}

/** Shown until the Report sighting screen is wired here. */
@Composable
fun ReportSightingScreenPlaceholder(alertId: String) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = "Report a sighting of $alertId",
        modifier = Modifier.semantics { testTag = "report_sighting_screen" },
    )
  }
}
