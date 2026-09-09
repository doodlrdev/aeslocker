package com.doodlr.aeslocker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

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