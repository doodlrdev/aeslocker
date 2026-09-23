package com.doodlr.aeslocker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object FlavorComponents {
    fun initialize(activity: Activity) {
        val consentAndAgeManager = ConsentAndAgeManager(activity)
        consentAndAgeManager.gatherConsentAndAgeSignals {
            // Play Services Ads has a long-documented quirk (reported as far back as
            // Android 7, still surfaces on specific OEM/SDK-version combinations
            // today) where SDK init/ad rendering can silently reset the Activity's
            // Configuration.uiMode night bit back to light, without going through
            // AppCompatDelegate. AppCompatDelegate never finds out, so it won't
            // self-correct. Forcing applyDayNight() once init completes re-asserts
            // whatever night mode the user actually has selected.
            MobileAds.initialize(activity) {
                (activity as? AppCompatActivity)?.delegate?.applyDayNight()
            }
        }
    }
}

@Composable
fun BannerAdView() {
    val context = LocalContext.current
    val adSize: AdSize = remember(context) {
        val displayMetrics = context.resources.displayMetrics
        val adWidthPx = displayMetrics.widthPixels
        val density = displayMetrics.density
        val adWidth = (adWidthPx / density).toInt()
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adSize)
                adUnitId = ctx.getString(R.string.admob_banner_ad_unit_id)
                // Same reassertion here: this is the specific component the
                // original bug reports call out, so re-apply night mode right
                // after this particular ad actually loads too, as a second,
                // more targeted safety net on top of the SDK-init one above.
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        (ctx as? AppCompatActivity)?.delegate?.applyDayNight()
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
fun ProUpgradeButton() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val borderColor = MaterialTheme.colorScheme.tertiary

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.5.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(
            text = stringResource(R.string.btn_ad_free),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = stringResource(R.string.dialog_pro_title)) },
            text = {
                Text(text = stringResource(R.string.dialog_pro_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(context.getString(R.string.pro_market_url))
                            setPackage("com.android.vending")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.pro_playstore_url)))
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.btn_get_pro))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun SourceButton() {
    // No-op: Source link only shown in the Pro flavor
}

/**
 * Free flavor: the recovery kit download is gated behind a rewarded ad.
 * This is a SOFT gate, deliberately: if no ad is available (offline, no
 * fill, etc.), the download proceeds anyway rather than blocking it. This
 * feature exists so users can always get back into their own encrypted
 * files - dead-ending that during an actual recovery situation just
 * because an ad didn't load would undermine the one thing this feature
 * promises. The ad is a speed bump for the common case, not a hard paywall.
 *
 * When an ad isn't available, rather than silently falling through with no
 * monetization touchpoint at all, a Get Pro dialog is shown once - but every
 * exit from that dialog (Get Pro, Continue, or dismissing it) still leads to
 * the download. It's a promotional beat layered on top of the fail-open
 * behavior, never a second gate.
 */
@Composable
fun RecoveryKitButton(onDownload: () -> Unit) {
    val context = LocalContext.current
    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var showFallbackPromo by remember { mutableStateOf(false) }

    fun loadAd() {
        RewardedAd.load(
            context,
            context.getString(R.string.admob_rewarded_ad_unit_id),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    // Pre-load as soon as this button enters composition, so an ad is
    // likely already ready by the time the user taps it.
    LaunchedEffect(Unit) { loadAd() }

    OutlinedButton(
        onClick = {
            val activity = context as? Activity
            val ad = rewardedAd
            if (activity != null && ad != null) {
                rewardedAd = null // consumed - request a fresh one for next time
                var rewardEarned = false

                // IMPORTANT: onUserEarnedReward() (passed to ad.show() below)
                // fires the instant the reward threshold is reached DURING
                // playback - not when the ad actually finishes. With
                // sequential/multi-creative rewarded ads this can fire while
                // a second creative is still on screen and the ad Activity
                // still owns the foreground, which is exactly what caused
                // the SAF save picker to launch and start writing before the
                // app was properly resumed, producing a truncated file.
                // So: only record that the reward was earned here, and only
                // act on it once the ad has fully closed.
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        if (rewardEarned) onDownload()
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        // Loaded but couldn't actually display - fail open,
                        // same policy as "no ad available" (see kdoc above).
                        onDownload()
                    }
                }

                ad.show(activity) { rewardEarned = true }
                loadAd()
            } else {
                // No ad ready - offer Pro once, but this never blocks the
                // download (see kdoc above).
                showFallbackPromo = true
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.download_kit_button_free))
    }

    if (showFallbackPromo) {
        AlertDialog(
            onDismissRequest = {
                // Tapping outside / back button must still lead to the
                // download - this dialog is a promo beat, not a gate.
                showFallbackPromo = false
                onDownload()
            },
            title = { Text(text = stringResource(R.string.dialog_ad_unavailable_title)) },
            text = { Text(text = stringResource(R.string.dialog_ad_unavailable_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFallbackPromo = false
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(context.getString(R.string.pro_market_url))
                            setPackage("com.android.vending")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.pro_playstore_url)))
                            )
                        }
                        // Deliberately NOT also calling onDownload() here:
                        // this launches a second Activity (Play Store) right
                        // as the user is about to be prompted with the SAF
                        // save dialog for the download - firing both at once
                        // risks one of the two intents getting clobbered.
                        // The user can tap the button again for the free
                        // download if they don't end up going through with Pro.
                    }
                ) {
                    Text(text = stringResource(R.string.btn_get_pro))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFallbackPromo = false
                        onDownload()
                    }
                ) {
                    Text(text = stringResource(R.string.btn_continue_download))
                }
            }
        )
    }
}

@Composable
fun RecoveryKitPromo() {
    val context = LocalContext.current

    Column {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.recovery_kit_pro_promo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(context.getString(R.string.pro_market_url))
                    setPackage("com.android.vending")
                }
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.pro_playstore_url)))
                    )
                }
            }
        )
    }
}