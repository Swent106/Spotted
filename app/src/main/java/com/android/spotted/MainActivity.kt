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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.spotted.resources.C
import com.android.spotted.ui.MainViewModel
import com.android.spotted.ui.StartDestination
import com.android.spotted.ui.authentification.SignInScreen
import com.android.spotted.ui.theme.SampleAppTheme

object Route {
  const val SIGNIN = "signin"
  const val PERSONAL_INFO = "personal_info"
  const val HOME = "home"
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
      PersonalInfoScreenPlaceholder(
          onProfileSaved = {
            navController.navigate(Route.HOME) { popUpTo(Route.PERSONAL_INFO) { inclusive = true } }
          }
      )
    }
    composable(Route.HOME) { HomeScreenPlaceholder() }
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
      Text("Continue to Home")
    }
  }
}

@Composable
fun HomeScreenPlaceholder() {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = "Home Screen",
        modifier = Modifier.semantics { testTag = "home_screen" },
    )
  }
}
