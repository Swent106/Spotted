package com.android.spotted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.android.spotted.ui.theme.SampleAppTheme

object Route {
  const val SIGNIN = "signin"
  const val PERSONAL_INFO = "personal_info"
  const val ADD_PETS = "add_pets"
  const val HOME = "home"
  const val REPORT_MISSING = "report_missing"
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
      SignInScreen(
          onSignedIn = {
            navController.navigate(Route.PERSONAL_INFO) {
              popUpTo(Route.SIGNIN) { inclusive = true }
            }
          }
      )
    }
    composable(Route.PERSONAL_INFO) {
      // Personal info stays below Add pets, so Back from Add pets returns to it
      PersonalInfoScreenPlaceholder(onProfileSaved = { navController.navigate(Route.ADD_PETS) })
    }
    addPetsDestination(navController)
    composable(Route.HOME) {
      HomeScreen(onReportMissingClick = { navController.navigate(Route.REPORT_MISSING) })
    }
    composable(Route.REPORT_MISSING) { ReportMissingScreenPlaceholder() }
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

@Composable
fun PersonalInfoScreenPlaceholder(onProfileSaved: () -> Unit = {}) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = "Personal Info Screen",
        modifier = Modifier.semantics { testTag = "personal_info_screen" },
    )
    Button(
        onClick = onProfileSaved,
        modifier = Modifier.padding(top = 16.dp),
    ) {
      Text("Continue")
    }
  }
}

/** Shown until the Report missing screen exists (its ViewModel is ReportMissingViewModel). */
@Composable
fun ReportMissingScreenPlaceholder() {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = "Report a pet missing",
        modifier = Modifier.semantics { testTag = "report_missing_screen" },
    )
  }
}
