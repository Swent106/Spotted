// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.ui.alertdetail.AlertDetailViewModel
import com.android.spotted.ui.home.HomeViewModel
import com.android.spotted.ui.personalinfo.PersonalInfoViewModel
import com.android.spotted.ui.profile.HelperProfileViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** The production factories build their ViewModels, with the app's photo repository. */
@RunWith(AndroidJUnit4::class)
class ViewModelFactoriesTest {

  private val extras =
      MutableCreationExtras().apply {
        set(
            ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY,
            ApplicationProvider.getApplicationContext<Application>(),
        )
      }

  @Test
  fun personalInfoFactory_createsTheViewModel() {
    assertNotNull(PersonalInfoViewModel.Factory.create(PersonalInfoViewModel::class.java, extras))
  }

  @Test
  fun homeFactory_createsTheViewModel() {
    assertNotNull(HomeViewModel.Factory.create(HomeViewModel::class.java, extras))
  }

  @Test
  fun helperProfileFactory_createsTheViewModel() {
    assertNotNull(HelperProfileViewModel.Factory.create(HelperProfileViewModel::class.java, extras))
  }

  @Test
  fun alertDetailFactory_createsTheViewModel() {
    assertNotNull(AlertDetailViewModel.Factory.create(AlertDetailViewModel::class.java, extras))
  }

  @Test
  fun alertDetailFactory_withoutApplication_createsTheViewModel() {
    assertNotNull(
        AlertDetailViewModel.Factory.create(
            AlertDetailViewModel::class.java,
            MutableCreationExtras(),
        )
    )
  }

  @Test
  fun alertDetailFactory_withoutExtras_createsTheViewModel() {
    assertNotNull(AlertDetailViewModel.Factory.create(AlertDetailViewModel::class.java))
  }
}
