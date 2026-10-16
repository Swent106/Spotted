// Portions of this code were generated with the help of GitHub Copilot.
package com.android.spotted.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelFactoryTest {

  @Test
  fun factory_createsMainViewModel() {
    val store = ViewModelStore()
    try {
      val viewModel = ViewModelProvider(store, MainViewModel.Factory)[MainViewModel::class.java]

      assertNotNull(viewModel)
    } finally {
      store.clear()
    }
  }
}
