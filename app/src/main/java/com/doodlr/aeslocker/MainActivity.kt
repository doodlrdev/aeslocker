package com.doodlr.aeslocker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// Single source of truth for every language this app ships translated
// resources for. Used both by LanguageSwitcherButton (the in-app language
// picker) and by buildMultilingualReadme (the recovery kit's README.txt) -
// keeping this in one place means the recovery kit can never drift out of
// sync with what languages the app actually supports.
val SUPPORTED_LANGUAGES = linkedMapOf(
    "العربية" to "ar",
    "বাংলা" to "bn",
    "Čeština" to "cs",
    "Deutsch" to "de",
    "English" to "en",
    "Español" to "es",
    "Español (Latinoamérica)" to "es-419",
    "Filipino" to "fil",
    "Français" to "fr",
    "ગુજરાતી" to "gu",
    "हिन्दी" to "hi",
    "Magyar" to "hu",
    "Bahasa Indonesia" to "id",
    "Íslenska" to "is",
    "Italiano" to "it",
    "日本語" to "ja",
    "Basa Jawa" to "jv",
    "ខ្មែរ" to "km",
    "ಕನ್ನಡ" to "kn",
    "한국어" to "ko",
    "Kiswahili" to "sw",
    "മലയാളം" to "ml",
    "मराठी" to "mr",
    "Nāhuatl" to "nah",
    "Nederlands" to "nl",
    "ଓଡ଼ିଆ" to "or",
    "ਪੰਜਾਬੀ" to "pa",
    "Polski" to "pl",
    "پښتو" to "ps",
    "Português (Brasil)" to "pt-BR",
    "Română" to "ro",
    "Русский" to "ru",
    "سنڌي" to "sd",
    "Suomi" to "fi",
    "தமிழ்" to "ta",
    "తెలుగు" to "te",
    "ไทย" to "th",
    "Türkçe" to "tr",
    "Українська" to "uk",
    "اردو" to "ur",
    "Tiếng Việt" to "vi",
    "中文 (简体)" to "zh-CN"
)


/**
 * FlavorComponents.initialize() (consent gathering + MobileAds init, both in the case of Free version only) and the Play Core
 * update check both kick off async work that captures whichever Activity instance is live
 * at the time, and both were being re-triggered on every single onCreate() — including
 * relaunches. If a relaunch happens while the previous instance's async callback is still
 * pending, that callback can end up touching a now-stale Activity's window right as the new
 * Activity is building its own — a plausible source of window/decor corruption. Neither of
 * these actually needs to repeat on every relaunch: consent status and update availability
 * don't change just because the user switched languages. Restricting each to once per
 * process lifetime removes that overlap entirely.
 */
object ProcessScopedInitGuard {
    @Volatile
    var flavorComponentsInitialized = false

    @Volatile
    var updateCheckStarted = false
}

object ThemePreferences {
    val THEME_MODE_KEY = intPreferencesKey("theme_mode")

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    fun getThemeMode(context: Context): Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE_KEY] ?: MODE_SYSTEM
        }

    suspend fun saveThemeMode(context: Context, mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }
}


private val DarkColors = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF505050),
    surfaceTint = Color(0xFF141414),
    primary = Color(0xFFD2D2D2),
    onPrimary = Color(0xFF000000),
    onBackground = Color(0xFFECECEC),
    onSurface = Color(0xFFECECEC),
    tertiary = Color(0xFF7FD858),
    tertiaryContainer = Color(0xFF1E3A12),
    onTertiaryContainer = Color(0xFFB8F29A)
)

private val LightColors = lightColorScheme(
    background = Color(0xFFCBCBCB),
    surface = Color(0xFFE4E4E0),
    surfaceVariant = Color(0xFFE7E7E7),
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFF2F2F0),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    tertiary = Color(0xFF2E7D32),
    tertiaryContainer = Color(0xFFDCF2D1),
    onTertiaryContainer = Color(0xFF14431A)
)


class MainActivity : AppCompatActivity() {

    private lateinit var appUpdateHelper: AppUpdateHelper
    private lateinit var appReviewHelper: AppReviewHelper
    private lateinit var interstitialAdHelper: InterstitialAdHelper

    override fun onCreate(savedInstanceState: Bundle?) {

        val savedThemeMode = runBlocking {
            ThemePreferences.getThemeMode(this@MainActivity).first()
        }
        applyAppCompatTheme(savedThemeMode)

        super.onCreate(savedInstanceState)

        appUpdateHelper = AppUpdateHelper(this)
        appReviewHelper = AppReviewHelper(this)

        setContent {
            val currentSavedTheme by ThemePreferences.getThemeMode(this@MainActivity)
                .collectAsState(initial = savedThemeMode)

            val isDarkMode = when (currentSavedTheme) {
                ThemePreferences.MODE_LIGHT -> false
                ThemePreferences.MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }

            val colorScheme = if (isDarkMode) DarkColors else LightColors

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        currentThemeMode = currentSavedTheme,
                        onThemeSelected = { newMode ->
                            applyAppCompatTheme(newMode)
                        },
                        onOperationSuccess = {
                            appReviewHelper.notifyOperationCompleted()
                            if (::interstitialAdHelper.isInitialized) {
                                interstitialAdHelper.notifyOperationCompleted(this@MainActivity)
                            }
                        }
                    )
                }
            }
        }

        // Everything below touches third-party SDKs (AdMob, UMP consent, Play Core
        // update, all in the case of Free version only; Pro version uses dummy calls)
        // that do their own async work and, in the case of AdMob/UMP, can
        // show their own overlay UI. Starting that work before setContent() had
        // installed the window's decor view meant an SDK callback could fire while
        // PhoneWindow was still mid-setup — the actual cause of the intermittent
        // "Window couldn't find content container view" crash on the free flavor.
        // Posting it to the decor view's message queue guarantees it only runs once
        // the window is fully installed, so there's nothing left for it to race with.
        window.decorView.post {
            interstitialAdHelper = InterstitialAdHelper(this)

            if (!ProcessScopedInitGuard.flavorComponentsInitialized) {
                ProcessScopedInitGuard.flavorComponentsInitialized = true
                FlavorComponents.initialize(this)
            }
            if (!ProcessScopedInitGuard.updateCheckStarted) {
                ProcessScopedInitGuard.updateCheckStarted = true
                appUpdateHelper.checkForUpdate(this)
            }
        }
    }

    private fun applyAppCompatTheme(mode: Int) {
        val targetMode = when (mode) {
            ThemePreferences.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreferences.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
    }
    if (name.isEmpty()) {
        name = uri.path?.substringAfterLast('/') ?: "file"
    }
    return name
}

// Looks only at the final extension (everything after the last dot). If that
// segment starts with "aes" or "enc", the whole segment is treated as the
// encrypted-file marker and stripped — regardless of what a file
// picker/manager appended after it ("(1)", "tuv", "_1", etc). Checking only
// the last dot-segment (rather than searching the whole filename) avoids
// false positives like "myfile.aesthetic.jpg", where the real extension is
// ".jpg" and "aesthetic" is just a folder/word earlier in the name.
fun deriveDecryptedFileName(originalFileName: String): String {
    val lastDotIndex = originalFileName.lastIndexOf('.')
    if (lastDotIndex <= 0) return "decrypted_$originalFileName"
    val baseName = originalFileName.substring(0, lastDotIndex)
    val extension = originalFileName.substring(lastDotIndex + 1)
    return if (extension.startsWith("aes", ignoreCase = true) || extension.startsWith("enc", ignoreCase = true)) {
        baseName
    } else {
        "decrypted_$originalFileName"
    }
}

fun getFileSize(context: Context, uri: Uri): Long {
    var size = 0L
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) size = cursor.getLong(index)
            }
        }
    }
    return size
}

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

// Lightweight, fully local heuristic — no dictionary lookups, no network calls.
// Scores on length plus character-class variety (upper/lower/digit/symbol).
// Not a substitute for a true entropy estimator, but gives a reasonable rough
// signal without pulling in a bundled password-dictionary dependency.
fun calculatePasswordStrength(password: String): PasswordStrength {
    val length = password.length
    var classes = 0
    if (password.any { it.isUpperCase() }) classes++
    if (password.any { it.isLowerCase() }) classes++
    if (password.any { it.isDigit() }) classes++
    if (password.any { !it.isLetterOrDigit() }) classes++

    var score = 0
    if (length >= 8) score++
    if (length >= 12) score++
    if (classes >= 2) score++
    if (classes >= 3) score++

    return when {
        score <= 1 -> PasswordStrength.WEAK
        score <= 3 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.STRONG
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentThemeMode: Int,
    onThemeSelected: (Int) -> Unit,
    onOperationSuccess: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_encrypt),
        stringResource(R.string.tab_decrypt)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        ProUpgradeButton()
                    }
                },
                actions = {
                    PrivacyOptionsButton()
                    LanguageSwitcherButton()
                    ThemeSwitcherButton(
                        currentMode = currentThemeMode,
                        onThemeSelected = onThemeSelected
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    ShareButton()
                    Spacer(modifier = Modifier.height(16.dp))
                    SourceButton()
                }
                BannerAdView()
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            ),
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            CryptoForm(
                isEncryptMode = selectedTabIndex == 0,
                onOperationSuccess = onOperationSuccess
            )
        }
    }
}

@Composable
fun ThemeSwitcherButton(
    currentMode: Int,
    onThemeSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentIcon = when (currentMode) {
        ThemePreferences.MODE_LIGHT -> Icons.Default.LightMode
        ThemePreferences.MODE_DARK -> Icons.Default.DarkMode
        else -> Icons.Default.SettingsSuggest
    }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = currentIcon,
                contentDescription = stringResource(R.string.cd_switch_theme)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_system)) },
                onClick = {
                    expanded = false
                    scope.launch {
                        ThemePreferences.saveThemeMode(context, ThemePreferences.MODE_SYSTEM)
                        onThemeSelected(ThemePreferences.MODE_SYSTEM)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_light)) },
                onClick = {
                    expanded = false
                    scope.launch {
                        ThemePreferences.saveThemeMode(context, ThemePreferences.MODE_LIGHT)
                        onThemeSelected(ThemePreferences.MODE_LIGHT)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_dark)) },
                onClick = {
                    expanded = false
                    scope.launch {
                        ThemePreferences.saveThemeMode(context, ThemePreferences.MODE_DARK)
                        onThemeSelected(ThemePreferences.MODE_DARK)
                    }
                }
            )
        }
    }
}


@Composable
fun LanguageSwitcherButton() {
    var expanded by remember { mutableStateOf(false) }

    // 1. Create a map that always has a static "System Default" at the top
    val languages = LinkedHashMap<String, String>()
    languages["System Default"] = "" // An empty string signifies the system default

    // 2. Populate the rest of the languages
    for ((name, code) in SUPPORTED_LANGUAGES.toSortedMap()) {
        languages[name] = code
    }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = stringResource(R.string.cd_switch_language)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { (displayName, languageTag) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        expanded = false
                        val appLocale = LocaleListCompat.forLanguageTags(languageTag)
                        AppCompatDelegate.setApplicationLocales(appLocale)

                    }
                )
            }
        }
    }
}

@Composable
fun CryptoForm(
    isEncryptMode: Boolean,
    onOperationSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> inputUri = uri }

    // Lets a user download a "recovery kit" (a .zip containing the offline
    // script bundled as a raw resource, res/raw/aeslocker_recover.sh, plus
    // a README.txt generated on the fly in every language this app ships)
    // via the standard system file picker, so it's saved somewhere durable
    // outside the app. The script is also published in the project's
    // GitHub repo, so recovery doesn't depend on this app existing at all.
    val saveKitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { outputUri: Uri? ->
        if (outputUri != null) {
            runCatching {
                context.contentResolver.openOutputStream(outputUri)?.use { output ->
                    java.util.zip.ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(java.util.zip.ZipEntry("aeslocker_recover.sh"))
                        context.resources.openRawResource(R.raw.aeslocker_recover).use { it.copyTo(zip) }
                        zip.closeEntry()

                        zip.putNextEntry(java.util.zip.ZipEntry("README.txt"))
                        zip.write(buildMultilingualReadme(context).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.toast_kit_saved), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.toast_kit_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { outputUri: Uri? ->
        val sourceUri = inputUri
        if (outputUri != null && sourceUri != null) {
            isLoading = true
            progress = 0f
            val totalSize = getFileSize(context, sourceUri)

            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(outputUri)?.use { output ->
                            if (isEncryptMode) {
                                OpenSSLCrypto.encrypt(input, output, password.toCharArray(), totalSize) { p ->
                                    progress = p
                                }
                            } else {
                                OpenSSLCrypto.decrypt(input, output, password.toCharArray(), totalSize) { p ->
                                    progress = p
                                }
                            }
                        }
                    }
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        dialogMessage = context.getString(if (isEncryptMode) R.string.toast_encrypt_success else R.string.toast_decrypt_success)
                        isSuccessDialog = true
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        dialogMessage = context.getString(R.string.toast_operation_failed)
                        isSuccessDialog = false
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.select_file))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val displayPath = inputUri?.let { uri ->
                    uri.path?.let { path ->
                        val fileName = getFileName(context, uri)
                        val folder = path.substringBeforeLast('/').substringAfterLast('/')
                        if (folder.isNotEmpty()) ".../$folder/$fileName" else fileName
                    } ?: getFileName(context, uri)
                } ?: stringResource(R.string.no_file_selected)

                Text(
                    text = displayPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inputUri == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { newValue ->
                // Strip every whitespace character (regular space, non-breaking
                // space, tab, etc.) as the user types. This guards against three
                // real failure modes for a password field:
                //  1. A trailing space the user doesn't notice, which silently
                //     changes the password used for encryption vs. what they
                //     believe they typed for decryption later.
                //  2. Keyboards that auto-insert a space after a sentence-ending
                //     period.
                //  3. Keyboards that auto-insert a space after a closing quote
                //     character following an opening quote.
                // Filtering at the source means none of these can ever produce
                // a space in the stored password, regardless of which keyboard
                // or input method the user has.
                password = newValue.filterNot { it.isWhitespace() }
            },
            label = { Text(stringResource(R.string.password_label)) },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                val description = stringResource(
                    if (isPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password
                )

                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                platformImeOptions = PlatformImeOptions(
                    privateImeOptions = "com.google.android.inputmethod.latin.noPersonalizedLearning=true,nm"
                )
            )
        )

        if (isEncryptMode && password.isNotEmpty()) {
            PasswordStrengthIndicator(password)
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.processing_progress, (progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Button(
                onClick = {
                    val currentUri = inputUri
                    if (currentUri == null) {
                        dialogMessage = context.getString(R.string.toast_select_file)
                        isSuccessDialog = false
                        return@Button
                    }
                    if (password.isEmpty()) {
                        dialogMessage = context.getString(R.string.toast_enter_password)
                        isSuccessDialog = false
                        return@Button
                    }

                    if (!isEncryptMode) {
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            var isValid = false
                            try {
                                val totalSize = getFileSize(context, currentUri)
                                context.contentResolver.openInputStream(currentUri)?.use { stream ->
                                    isValid = OpenSSLCrypto.validatePassword(stream, password.toCharArray(), totalSize)
                                }
                            } catch (e: Exception) {
                                isValid = false
                            }

                            withContext(Dispatchers.Main) {
                                isLoading = false
                                if (!isValid) {
                                    dialogMessage = context.getString(R.string.toast_invalid_file)
                                    isSuccessDialog = false
                                } else {
                                    val originalFileName = getFileName(context, currentUri)
                                    val suggestedName = deriveDecryptedFileName(originalFileName)
                                    saveFileLauncher.launch(suggestedName)
                                }
                            }
                        }
                        return@Button
                    }

                    val originalFileName = getFileName(context, currentUri)
                    saveFileLauncher.launch("$originalFileName.aes")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(if (isEncryptMode) R.string.encrypt_file else R.string.decrypt_file),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.terminal_commands_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.terminal_commands_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                RecoveryKitButton(onDownload = { saveKitLauncher.launch("aeslocker_recovery_kit.zip") })
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.download_kit_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.github_repo_label, stringResource(R.string.github_repo_url)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                RecoveryKitPromo()
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.important_notice_script),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        if (dialogMessage != null) {
            AlertDialog(
                onDismissRequest = {
                    if (isSuccessDialog) onOperationSuccess()
                    dialogMessage = null
                },
                title = {
                    Text(
                        text = if (isSuccessDialog) stringResource(R.string.toast_title_success) else stringResource(R.string.toast_title_notice),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(text = dialogMessage!!)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isSuccessDialog) onOperationSuccess()
                            dialogMessage = null
                        }
                    ) {
                        Text(text = "OK")
                    }
                }
            )
        }
    }
}

@Composable
fun PasswordStrengthIndicator(password: String) {
    val strength = remember(password) { calculatePasswordStrength(password) }

    // A single fixed palette (rather than switching per-theme) would fail
    // contrast in one mode or the other — e.g. a light amber reads fine on
    // this app's near-black dark background but nearly disappears on the
    // light gray background, and vice versa for darker shades. Checking the
    // actual background luminance keeps the chosen shade readable either way.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val (color, labelRes, progress) = when (strength) {
        PasswordStrength.WEAK -> Triple(
            if (isDark) Color(0xFFEF5350) else Color(0xFFC62828),
            R.string.password_strength_weak,
            0.33f
        )
        PasswordStrength.MEDIUM -> Triple(
            if (isDark) Color(0xFFFFCA28) else Color(0xFFF9A825),
            R.string.password_strength_medium,
            0.66f
        )
        PasswordStrength.STRONG -> Triple(
            if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32),
            R.string.password_strength_strong,
            1f
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}


@Composable
fun ShareButton() {
    val context = LocalContext.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = {
                val playStoreUrl = context.getString(R.string.own_playstore_url)
                val shareText = context.getString(R.string.share_message, playStoreUrl)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = stringResource(R.string.share_button_label),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.share_button_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Builds README.txt content for the recovery kit, with one section per
 * language this app ships resources for - so the file is useful to
 * whoever opens it, regardless of which language their own device is set
 * to (they may not even have this app installed anymore by the time they
 * need this).
 *
 * Languages come from SUPPORTED_LANGUAGES (the same list the in-app
 * language switcher uses), NOT from AssetManager.getLocales(). That was
 * tried first but had to be dropped: it reflects every locale present
 * anywhere in the compiled resource table, including ones pulled in by
 * dependencies (Material Components, AppCompat, Play Services all ship
 * their own translations for generic strings in languages this app never
 * actually translated, e.g. "af", "az") - so those sections silently
 * fell back to English with no way to tell they weren't real.
 * SUPPORTED_LANGUAGES is the curated, authoritative list instead.
 *
 * Section order: English first (most widely understood, so whoever opens
 * this can orient themselves immediately), then the rest alphabetically
 * by display name.
 */
private fun buildMultilingualReadme(context: Context): String {
    // Sorted by each language's ENGLISH name (Arabic, Bengali, Czech...),
    // not its native name - sorting by native name technically is
    // alphabetical too, but produces a Unicode-block grouping (all Latin-
    // script languages first, then Cyrillic, then Arabic, then each Indic
    // script...) rather than the true A-Z order a reader expects.
    val orderedTags = SUPPORTED_LANGUAGES.values
        .sortedBy { tag ->
            if (tag == "en") "" // pin English first, ahead of every real name
            else java.util.Locale.forLanguageTag(tag).getDisplayName(java.util.Locale.ENGLISH)
        }

    val sections = orderedTags.map { tag ->
        val locale = java.util.Locale.forLanguageTag(tag)
        val localizedContext = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(locale)
            }
        )
        val s = localizedContext.resources
        val languageName = locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }

        buildString {
            appendLine("=".repeat(60))
            appendLine("$languageName ($tag)")
            appendLine("=".repeat(60))
            appendLine()
            appendLine(s.getString(R.string.readme_title))
            appendLine()
            appendLine(s.getString(R.string.readme_intro))
            appendLine()
            appendLine(s.getString(R.string.readme_terminal_intro))
            appendLine()
            appendLine(s.getString(R.string.readme_requirements_title))
            appendLine(
                s.getString(
                    R.string.readme_requirements_body,
                    s.getString(R.string.cmd_openssl_version),
                    s.getString(R.string.cmd_brew_install_openssl)
                )
            )
            appendLine()
            appendLine(s.getString(R.string.readme_chmod_title))
            appendLine(s.getString(R.string.readme_chmod_body, s.getString(R.string.cmd_chmod)))
            appendLine()
            appendLine(s.getString(R.string.readme_usage_title))
            appendLine(s.getString(R.string.readme_usage_encrypt, s.getString(R.string.cmd_encrypt_example)))
            appendLine()
            appendLine(s.getString(R.string.readme_usage_decrypt, s.getString(R.string.cmd_decrypt_example)))
            appendLine()
            appendLine(s.getString(R.string.readme_password_note))
            appendLine()
            appendLine(
                s.getString(
                    R.string.readme_linux_note,
                    s.getString(R.string.cmd_openssl_version),
                    s.getString(R.string.cmd_apt_install_xxd)
                )
            )
            appendLine()
            appendLine(s.getString(R.string.readme_android_note, s.getString(R.string.cmd_termux_install_openssl)))
            appendLine()
            appendLine(s.getString(R.string.readme_windows_note, s.getString(R.string.cmd_apt_install_xxd)))
        }
    }

    return sections.joinToString(separator = "\n\n")
}