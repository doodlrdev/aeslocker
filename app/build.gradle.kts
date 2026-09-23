import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configure<ApplicationExtension> {
    namespace = "com.doodlr.aeslocker"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions.add("version")

    productFlavors {
        create("free") {
            dimension = "version"
            applicationId = "com.doodlr.aeslocker"
        }
        create("pro") {
            dimension = "version"
            applicationId = "com.doodlr.aeslocker.pro"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Ships every language's resources in every install, instead of only the
    // split matching the device's system locale. Without this, per-app language
    // switching (AppCompatDelegate.setApplicationLocales) silently falls back to
    // the default locale whenever the user picks a language whose resource split
    // wasn't installed by Play — this only shows up on Play-Store/AAB installs,
    // never on a directly-installed monolithic APK, which is why it looked
    // inconsistent. String-only resources are small, so this has negligible
    // impact on install size for this app.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Extended Material Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Free-only dependencies (Omitted completely from Pro build — keeps Pro fully offline)
    "freeImplementation"(libs.play.services.ads)
    "freeImplementation"(libs.user.messaging.platform)
    "freeImplementation"(libs.play.age.signals)
    "freeImplementation"(libs.app.update)
    "freeImplementation"(libs.app.update.ktx)
    "freeImplementation"(libs.play.review)
    "freeImplementation"(libs.play.review.ktx)
}