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
        versionCode = 1
        versionName = "1.0"

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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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

    // In-App Updates (Shared by both flavors)
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)

    // Play Review (Shared by both flavors)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    // Free-only dependencies (Omitted completely from Pro build)
    "freeImplementation"(libs.play.services.ads)
    "freeImplementation"(libs.user.messaging.platform)
    "freeImplementation"(libs.play.age.signals)
}