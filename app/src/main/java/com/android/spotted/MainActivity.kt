// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.android.spotted.data.notification.SystemNotifier.Companion.EXTRA_ROUTE
import com.android.spotted.data.photo.FirestorePhotoFetcher
import com.android.spotted.resources.C
import com.android.spotted.ui.MainViewModel
import com.android.spotted.ui.StartDestination
import com.android.spotted.ui.addpets.AddPetsScreen
import com.android.spotted.ui.alertdetail.AlertDetailScreen
import com.android.spotted.ui.alertdetail.AlertDetailViewModel
import com.android.spotted.ui.authentification.SignInScreen
import com.android.spotted.ui.components.isResumed
import com.android.spotted.ui.components.popBackStackSafely
import com.android.spotted.ui.editpet.EditPetScreen
import com.android.spotted.ui.editpet.EditPetViewModel
import com.android.spotted.ui.home.HomeScreen
import com.android.spotted.ui.map.AlertsMapScreen
import com.android.spotted.ui.myalert.MyAlertScreen
import com.android.spotted.ui.myalert.MyAlertViewModel
import com.android.spotted.ui.personalinfo.PersonalInfoScreen
import com.android.spotted.ui.petprofile.PetProfileScreen
import com.android.spotted.ui.petprofile.PetProfileViewModel
import com.android.spotted.ui.profile.HelperProfileNavigation
import com.android.spotted.ui.profile.HelperProfileScreen
import com.android.spotted.ui.report.ReportMissingScreen
import com.android.spotted.ui.scantag.ScanTagScreen
import com.android.spotted.ui.sighting.ReportSightingScreen
import com.android.spotted.ui.sighting.ReportSightingViewModel
import com.android.spotted.ui.theme.SpottedTheme

object Route {
  const val SIGNIN = "signin"
  const val PERSONAL_INFO = "personal_info"
  const val ADD_PETS = "add_pets"
  const val HOME = "home"
  const val SCAN_TAG = "scan_tag"
  const val REPORT_MISSING = "report_missing"
  const val ALERT_ID = "alertId"
  private const val ALERT = "alert"
  const val ALERT_DETAIL = "$ALERT/{$ALERT_ID}"
  /** Report sighting opened from the Camera tab, for a pet not linked to an alert yet. */
  const val NEW_SIGHTING = "report_sighting"
  const val REPORT_SIGHTING = "$NEW_SIGHTING/{$ALERT_ID}"
  /** Add pets opened from Home: not an onboarding step, it goes back to Home when done. */
  const val ADD_PET = "add_pet"
  const val MAP = "map"
  const val PROFILE = "profile"
  /** The profile form, opened from the Helper profile to change name, area or radius. */
  const val EDIT_PROFILE = "edit_profile"
  const val PET_ID = "petId"
  private const val PET = "pet"
  const val PET_PROFILE = "$PET/{$PET_ID}"
  /** The edit form of one of the user's pets, opened from its profile. */
  private const val EDIT_PET_BASE = "edit_pet"
  const val EDIT_PET = "$EDIT_PET_BASE/{$PET_ID}"
  /** The owner's view of their own alert: sightings, likely area, "My pet is home". */
  private const val MY_ALERT_BASE = "my_alert"
  const val MY_ALERT = "$MY_ALERT_BASE/{$ALERT_ID}"

  fun petProfile(petId: String) = "$PET/$petId"

  fun editPet(petId: String) = "$EDIT_PET_BASE/$petId"

  fun myAlert(alertId: String) = "$MY_ALERT_BASE/$alertId"

  fun alertDetail(alertId: String) = "$ALERT/$alertId"

  fun reportSighting(alertId: String) = "$NEW_SIGHTING/$alertId"
}

class MainActivity : ComponentActivity() {

  private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

  /** The route of a notification the user tapped, until it has been opened. */
  private var notificationRoute by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // After a rotation the intent is still the old one, already handled
    if (savedInstanceState == null) notificationRoute = intent.getStringExtra(EXTRA_ROUTE)
    // Photos are kept in Firestore: teach Coil to load their firestore:// references
    Coil.setImageLoader(
        ImageLoader.Builder(applicationContext)
            .components { add(FirestorePhotoFetcher.Factory()) }
            .build()
    )
    keepSplashWhileLoading()
    setContent {
      SpottedTheme {
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          SpottedApp(
              viewModel = mainViewModel,
              notificationRoute = notificationRoute,
              onNotificationRouteOpened = { notificationRoute = null },
          )
        }
      }
    }
  }

  /** The app was already running when a notification was tapped (it is `singleTop`). */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra(EXTRA_ROUTE)?.let { notificationRoute = it }
  }

  /**
   * Keeps the system splash screen until [MainViewModel] knows the start screen, so a signed-in
   * user reopening the app goes straight to Home without a glimpse of Welcome.
   */
  private fun keepSplashWhileLoading() {
    val content = findViewById<View>(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
          override fun onPreDraw(): Boolean {
            if (mainViewModel.uiState.value == StartDestination.LOADING) return false
            content.viewTreeObserver.removeOnPreDrawListener(this)
            return true
          }
        }
    )
  }
}

@Composable
fun SpottedApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    notificationRoute: String? = null,
    onNotificationRouteOpened: () -> Unit = {},
) {
  val destination by viewModel.uiState.collectAsState()
  val navController = rememberNavController()

  StartRouting(destination, navController)
  NotificationRouting(notificationRoute, destination, navController, onNotificationRouteOpened)

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
 * Opens the screen for [destination] whenever it changes, clearing the back stack. The last
 * destination handled is saved, so recreating the activity (e.g. rotating the phone) keeps the
 * current screen instead of going back to the start one.
 */
@Composable
fun StartRouting(destination: StartDestination, navController: NavController) {
  var routedDestination by rememberSaveable { mutableStateOf<StartDestination?>(null) }

  LaunchedEffect(destination) {
    if (destination == routedDestination) return@LaunchedEffect
    val targetRoute =
        when (destination) {
          // The splash screen hides the app until the start screen is known
          StartDestination.LOADING -> return@LaunchedEffect
          StartDestination.SIGNIN -> Route.SIGNIN
          StartDestination.PERSONAL_INFO -> Route.PERSONAL_INFO
          StartDestination.HOME -> Route.HOME
        }
    routedDestination = destination
    navController.navigate(targetRoute) {
      popUpTo(0) { inclusive = true }
      launchSingleTop = true
    }
  }
}

/**
 * Opens the [route] of a tapped notification, above Home. It waits for Home, so a cold start isn't
 * undone by [StartRouting] clearing the back stack. Must come after [StartRouting].
 */
@Composable
fun NotificationRouting(
    route: String?,
    destination: StartDestination,
    navController: NavController,
    onOpened: () -> Unit,
) {
  LaunchedEffect(route, destination) {
    if (route == null || destination != StartDestination.HOME) return@LaunchedEffect
    try {
      navController.navigate(route) { launchSingleTop = true }
    } catch (_: IllegalArgumentException) {
      // MainActivity is exported, so the route may come from another app and not exist
    }
    onOpened()
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
        { navController.popBackStackSafely() },
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
 * missing screen, [alertDetailContent] an alert's detail, [addPetContent] Add pets opened from
 * Home, [petProfileContent] one of the user's pets, [reportSightingContent] Report sighting (for an
 * alert, or none from the Camera tab), [mapContent] the Map tab, [profileContent] the Helper
 * profile (Profile tab), [editProfileContent] the profile form it opens, [editPetContent] the edit
 * form of a pet and [myAlertContent] the owner's own alert, opened from a missing pet on Home;
 * tests replace them to use hand-built states instead of the real ViewModels.
 */
fun NavGraphBuilder.homeDestinations(
    navController: NavController,
    homeContent: @Composable (HomeNavigation) -> Unit = { navigation ->
      HomeScreen(
          onReportMissingClick = navigation.onReportMissingClick,
          onAlertClick = navigation.onAlertClick,
          onSeenPetClick = navigation.onSeenPetClick,
          onAddPetClick = navigation.onAddPetClick,
          onPetClick = navigation.onPetClick,
          onMissingPetClick = navigation.onMissingPetClick,
          onMapClick = navigation.onMapClick,
          onCameraClick = navigation.onCameraClick,
          onProfileClick = navigation.onProfileClick,
          onScanTagClick = navigation.onScanTagClick,
      )
    },
    myAlertContent: @Composable (alertId: String, onBack: () -> Unit) -> Unit = { alertId, onBack ->
      MyAlertScreen(
          viewModel = viewModel(key = alertId, factory = MyAlertViewModel.Factory(alertId)),
          onBack = onBack,
      )
    },
    reportMissingContent: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
      ReportMissingScreen(onBack = onDone, onPublished = onDone)
    },
    alertDetailContent: @Composable (alertId: String, onReportSighting: () -> Unit) -> Unit =
        { alertId, onReportSighting ->
          AlertDetailScreen(
              viewModel = viewModel(factory = AlertDetailViewModel.Factory),
              alertId = alertId,
              onReportSighting = onReportSighting,
          )
        },
    addPetContent: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
      AddPetsScreen(onBack = onDone, onDone = onDone, isOnboarding = false)
    },
    reportSightingContent: @Composable (alertId: String?, onDone: () -> Unit) -> Unit =
        { alertId, onDone ->
          val viewModel: ReportSightingViewModel =
              viewModel(factory = ReportSightingViewModel.Factory)
          LaunchedEffect(alertId) { alertId?.let(viewModel::preselectAlert) }
          ReportSightingScreen(viewModel = viewModel, onSubmitted = onDone, onBack = onDone)
        },
    scanTagContent:
        @Composable
        (onBack: () -> Unit, onReportSighting: (alertId: String?) -> Unit) -> Unit =
        { onBack, onReportSighting ->
          ScanTagScreen(onBack = onBack, onReportSighting = onReportSighting)
        },
    petProfileContent:
        @Composable
        (
            petId: String,
            onBack: () -> Unit,
            onEdit: (String) -> Unit,
            onDeleted: () -> Unit,
        ) -> Unit =
        { petId, onBack, onEdit, onDeleted ->
          PetProfileScreen(
              viewModel = viewModel(factory = PetProfileViewModel.Factory),
              petId = petId,
              deleteViewModel = viewModel(key = petId, factory = EditPetViewModel.factory(petId)),
              onBack = onBack,
              onEdit = onEdit,
              onDeleted = onDeleted,
          )
        },
    editPetContent: @Composable (petId: String, onDone: () -> Unit) -> Unit = { petId, onDone ->
      EditPetScreen(petId = petId, onBack = onDone, onSaved = onDone)
    },
    mapContent: @Composable (onBack: () -> Unit, onAlertClick: (String) -> Unit) -> Unit =
        { onBack, onAlertClick ->
          AlertsMapScreen(onBack = onBack, onAlertClick = onAlertClick)
        },
    profileContent: @Composable (HelperProfileNavigation) -> Unit = { navigation ->
      HelperProfileScreen(navigation = navigation)
    },
    editProfileContent: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
      PersonalInfoScreen(onBack = onDone, onSaved = onDone)
    },
) {
  // Every click below is ignored while a transition is running (the screen is not RESUMED), so
  // two quick taps open one screen. Callbacks with an argument check the entry themselves, as
  // dropUnlessResumed only wraps () -> Unit.
  composable(Route.HOME) { entry ->
    homeContent(
        HomeNavigation(
            onReportMissingClick =
                dropUnlessResumed { navController.navigate(Route.REPORT_MISSING) },
            onAlertClick = { alertId ->
              if (entry.isResumed()) navController.navigate(Route.alertDetail(alertId))
            },
            onSeenPetClick = { alertId ->
              if (entry.isResumed()) navController.navigate(Route.reportSighting(alertId))
            },
            onAddPetClick = dropUnlessResumed { navController.navigate(Route.ADD_PET) },
            onPetClick = { petId ->
              if (entry.isResumed()) navController.navigate(Route.petProfile(petId))
            },
            onMissingPetClick = { alertId ->
              if (entry.isResumed()) navController.navigate(Route.myAlert(alertId))
            },
            onMapClick = dropUnlessResumed { navController.navigate(Route.MAP) },
            onCameraClick = dropUnlessResumed { navController.navigate(Route.NEW_SIGHTING) },
            onProfileClick = dropUnlessResumed { navController.navigate(Route.PROFILE) },
            onScanTagClick = dropUnlessResumed { navController.navigate(Route.SCAN_TAG) },
        )
    )
  }
  composable(Route.SCAN_TAG) { entry ->
    scanTagContent(
        dropUnlessResumed { navController.popBackStackSafely() },
        { alertId ->
          if (entry.isResumed()) {
            navController.navigate(
                if (alertId == null) Route.NEW_SIGHTING else Route.reportSighting(alertId)
            )
          }
        },
    )
  }
  composable(Route.PROFILE) {
    profileContent(
        HelperProfileNavigation(
            onHomeClick =
                dropUnlessResumed { navController.popBackStack(Route.HOME, inclusive = false) },
            onMapClick = dropUnlessResumed { navController.navigate(Route.MAP) },
            onCameraClick = dropUnlessResumed { navController.navigate(Route.NEW_SIGHTING) },
            onEditProfileClick = dropUnlessResumed { navController.navigate(Route.EDIT_PROFILE) },
        )
    )
  }
  composable(Route.EDIT_PROFILE) { editProfileContent { navController.popBackStackSafely() } }
  composable(Route.MAP) { entry ->
    mapContent({ navController.popBackStackSafely() }) { alertId ->
      if (entry.isResumed()) navController.navigate(Route.alertDetail(alertId))
    }
  }
  composable(Route.ADD_PET) { addPetContent { navController.popBackStackSafely() } }
  composable(Route.PET_PROFILE) { entry ->
    petProfileContent(
        entry.arguments?.getString(Route.PET_ID).orEmpty(),
        { navController.popBackStackSafely() },
        { petId -> if (entry.isResumed()) navController.navigate(Route.editPet(petId)) },
        // The pet no longer exists: back to Home, with its profile removed from the back stack
        { navController.popBackStack(Route.HOME, inclusive = false) },
    )
  }
  composable(Route.EDIT_PET) { entry ->
    editPetContent(entry.arguments?.getString(Route.PET_ID).orEmpty()) {
      navController.popBackStackSafely()
    }
  }
  composable(Route.REPORT_MISSING) { reportMissingContent { navController.popBackStackSafely() } }
  composable(Route.ALERT_DETAIL) { entry ->
    val alertId = entry.arguments?.getString(Route.ALERT_ID).orEmpty()
    alertDetailContent(
        alertId,
        dropUnlessResumed { navController.navigate(Route.reportSighting(alertId)) },
    )
  }
  composable(Route.REPORT_SIGHTING) { entry ->
    reportSightingContent(entry.arguments?.getString(Route.ALERT_ID)) {
      navController.popBackStackSafely()
    }
  }
  composable(Route.NEW_SIGHTING) {
    reportSightingContent(null) { navController.popBackStackSafely() }
  }
  composable(Route.MY_ALERT) { entry ->
    myAlertContent(entry.arguments?.getString(Route.ALERT_ID).orEmpty()) {
      navController.popBackStackSafely()
    }
  }
}

/** Where Home can send the user. */
data class HomeNavigation(
    val onReportMissingClick: () -> Unit,
    val onAlertClick: (alertId: String) -> Unit,
    val onSeenPetClick: (alertId: String) -> Unit,
    val onAddPetClick: () -> Unit = {},
    val onPetClick: (petId: String) -> Unit = {},
    /** Opens My alert for one of the user's missing pets. */
    val onMissingPetClick: (alertId: String) -> Unit = {},
    val onMapClick: () -> Unit = {},
    val onCameraClick: () -> Unit = {},
    val onProfileClick: () -> Unit = {},
    val onScanTagClick: () -> Unit = {},
)
