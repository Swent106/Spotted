package com.android.spotted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.android.spotted.resources.C
import com.android.spotted.ui.MainViewModel
import com.android.spotted.ui.StartDestination
import com.android.spotted.ui.theme.SampleAppTheme

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

  Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
  ) {
    when (destination) {
      StartDestination.SIGNIN -> LoginScreenPlaceholder()
      StartDestination.PERSONAL_INFO -> PersonalInfoScreenPlaceholder()
      StartDestination.HOME -> HomeScreenPlaceholder()
    }
  }
}

@Composable
fun LoginScreenPlaceholder() {
  Text(
      text = "Login Screen",
      modifier = Modifier.semantics { testTag = "login_screen" },
  )
}

@Composable
fun PersonalInfoScreenPlaceholder() {
  Text(
      text = "Personal Info Screen",
      modifier = Modifier.semantics { testTag = "personal_info_screen" },
  )
}

@Composable
fun HomeScreenPlaceholder() {
  Text(
      text = "Home Screen",
      modifier = Modifier.semantics { testTag = "home_screen" },
  )
}
