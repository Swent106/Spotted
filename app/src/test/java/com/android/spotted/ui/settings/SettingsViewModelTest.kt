// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.lifecycle.viewmodel.MutableCreationExtras
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsViewModelTest {

  @Test
  fun factory_createsTheViewModelWithoutAnyExtras() {
    val viewModel =
        SettingsViewModel.Factory.create(SettingsViewModel::class.java, MutableCreationExtras())

    assertNotNull(viewModel.uiState.value)
  }
}
