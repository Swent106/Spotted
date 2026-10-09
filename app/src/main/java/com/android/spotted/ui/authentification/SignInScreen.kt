package com.android.spotted.ui.authentification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R

/** Test tags used by UI tests to find the elements of the Sign-In screen. */
object SignInScreenTestTags {
    const val SCREEN = "signInScreen"
    const val GOOGLE_BUTTON = "signInGoogleButton"
    const val LOADER = "signInLoader"
    const val ERROR_MESSAGE = "signInErrorMessage"
}

private val Jakarta =
    FontFamily(
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold))

private fun jakarta(
    size: Int,
    line: Float,
    weight: Int,
    color: Long,
    align: TextAlign = TextAlign.Start
) =
    TextStyle(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFamily = Jakarta,
        fontWeight = FontWeight(weight),
        color = Color(color),
        textAlign = align)

/**
 * Stateless Sign-In screen: shows a loader while [SignInUiState.isLoading] and the error message on
 * failure.
 *
 * The screen fills the available space and scrolls vertically, so every element stays reachable on
 * small screens.
 */
@Composable
fun SignInContent(state: SignInUiState, onSignInClick: () -> Unit) {

    // Screen
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Color(0xFFEFEFEF))
                .paint(
                    painterResource(R.drawable.background_textures), contentScale = ContentScale.Crop)
                .verticalScroll(rememberScrollState())
                .testTag(SignInScreenTestTags.SCREEN)) {
        // Top
        Column(
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 8.dp)) {
            // Brand
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.27.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(54.dp)) {
                Image(
                    painterResource(R.drawable.spotted_logo),
                    contentDescription = "Logo Spotted",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.width(53.dp))
                Text("Spotted", style = jakarta(27, 35.59f, 800, 0xFF14161A))
            }
            // Heading
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Nobody finds a lost pet alone",
                    Modifier.fillMaxWidth(),
                    style = jakarta(30, 37f, 800, 0xFF14161A))
                Text(
                    "Sign in to post an alert for your own pet, and to be told when one goes missing close to you.",
                    Modifier.fillMaxWidth(),
                    style = jakarta(15, 20f, 500, 0xFF5F6672))
            }
            // Hero
            Hero()
        }
        // Bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(59.dp)
                        .background(Color(0xFFF5F6F8), RoundedCornerShape(100.dp))
                        .padding(start = 14.dp, end = 16.dp)) {
                Image(
                    painterResource(R.drawable.ic_watching),
                    contentDescription = null,
                    modifier = Modifier.padding(1.dp).size(18.dp),
                    contentScale = ContentScale.None)
                Text(
                    "1 240 people already watching out in Geneva",
                    style = jakarta(12, 16f, 600, 0xFF2C3340))
            }
            Spacer(Modifier.height(30.dp))

            if (state.isLoading) {
                Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(40.dp).testTag(SignInScreenTestTags.LOADER).semantics {
                                contentDescription = "Signing in"
                            },
                        color = Color(0xFF14161A),
                    )
                }
            } else {
                GoogleSignInButton(onSignInClick)
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    Modifier.fillMaxWidth().testTag(SignInScreenTestTags.ERROR_MESSAGE),
                    style = jakarta(13, 18f, 600, 0xFFD93025, TextAlign.Center),
                )
            }

            // Fixed height instead of weight(1f): weight does not work in a scrollable column
            Spacer(Modifier.height(48.dp))
            Text(
                "Why an account? It keeps alerts tied to a real owner, so only you can confirm sightings of your pet.",
                Modifier.fillMaxWidth(),
                style = jakarta(12, 16f, 500, 0xFF6B7280, TextAlign.Center))
            Spacer(Modifier.height(34.dp))
            Text(
                "By continuing you accept the terms and the privacy notice",
                Modifier.fillMaxWidth(),
                style = jakarta(11, 14f, 500, 0xFF9AA0AA, TextAlign.Center))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Hero() {
    Box(
        Modifier.width(342.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Color(0xFFE8EEFC))) {
        // Carte en fond
        Image(
            painterResource(R.drawable.hero_map),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset((-125).dp, (-9).dp)
                    .size(492.dp, 491.dp))
        // Cercles
        Ring(96, 35, 150)
        Ring(60, -1, 222)
        // Photos
        PetPhoto(
            R.drawable.hero_pet_lost, 138, 77, 66.dp, 0xFFF0E0CE, 98.dp, 174.51.dp, mirror = true)
        PetPhoto(R.drawable.hero_helper_1, 58, 44, 44.dp, 0xFFD9E2E8, 46.dp, 70.dp)
        PetPhoto(R.drawable.hero_helper_2, 240, 52, 44.dp, 0xFFDEE9DC, 38.dp, 50.87.dp)
        PetPhoto(R.drawable.hero_helper_3, 78, 142, 44.dp, 0xFFE7DCEC, 68.dp, 44.dp, mirror = true)
        PetPhoto(R.drawable.hero_helper_4, 252, 140, 44.dp, 0xFFDDE3EE, 40.dp, 64.dp, mirror = true)
        // Pins
        listOf(
            Triple(85, 30, 22),
            Triple(103, 129, 22),
            Triple(282, 129, 22),
            Triple(179, 55, 33),
            Triple(263, 38, 22))
            .forEach { (x, y, s) ->
                Image(
                    painterResource(R.drawable.hero_pin),
                    contentDescription = null,
                    modifier = Modifier.offset(x.dp, y.dp).size(s.dp))
            }
    }
}

@Composable
private fun Ring(x: Int, y: Int, size: Int) =
    Box(Modifier.offset(x.dp, y.dp).size(size.dp).border(1.dp, Color(0xFFD3DBF5), CircleShape))

@Composable
private fun PetPhoto(
    res: Int,
    x: Int,
    y: Int,
    size: Dp,
    bg: Long,
    imgW: Dp,
    imgH: Dp,
    mirror: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.offset(x.dp, y.dp)
                .size(size)
                .clip(CircleShape)
                .background(Color(bg))
                .border(3.dp, Color.White, CircleShape)) {
        Image(
            painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.wrapContentSize(unbounded = true)
                    .size(imgW, imgH)
                    .graphicsLayer(scaleX = if (mirror) -1f else 1f))
    }
}

@Composable
private fun GoogleSignInButton(onSignInClick: () -> Unit) {
    Button(
        onClick = onSignInClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White), // Button color
        shape = RoundedCornerShape(50), // Circular edges for the button
        border = BorderStroke(1.dp, Color.LightGray),
        modifier = Modifier.padding(8.dp).height(48.dp).testTag(SignInScreenTestTags.GOOGLE_BUTTON)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()) {
            // Load the Google logo from resources
            Image(
                painter = painterResource(id = R.drawable.google_logo),
                contentDescription = "Google Logo",
                modifier = Modifier.size(30.dp).padding(end = 8.dp))

            // Text for the button
            Text(
                text = "Sign in with Google",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInContentPreview() {
    SignInContent(state = SignInUiState(), onSignInClick = {})
}

@Preview(name = "Small screen", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun SignInContentSmallPreview() {
    SignInContent(state = SignInUiState(), onSignInClick = {})
}

@Preview(name = "Loading", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInContentLoadingPreview() {
    SignInContent(state = SignInUiState(isLoading = true), onSignInClick = {})
}

@Preview(name = "Error", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInContentErrorPreview() {
    SignInContent(state = SignInUiState(errorMessage = "Sign-in cancelled"), onSignInClick = {})
}

/**
 * Stateful entry point of the Sign-In screen.
 *
 * Collects the [SignInViewModel] state, forwards clicks to it, and calls [onSignedIn] once a user
 * is signed in. All the UI lives in the stateless [SignInContent].
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel = viewModel(),
    credentialManager: CredentialManager? = null,
    onSignedIn: () -> Unit = {},
) {
    val context = LocalContext.current
    val manager = credentialManager ?: remember { CredentialManager.create(context) }
    val state by viewModel.uiState.collectAsState()

    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.user) { if (state.user != null) currentOnSignedIn() }

    SignInContent(state = state, onSignInClick = { viewModel.signIn(context, manager) })
}