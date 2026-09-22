import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("com.google.gms.google-services")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    // Direct declaration prevents release lint from treating the older transitive
    // Fragment metadata as the Activity Result API implementation target.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.koin.core)
    // Main-process RevenueCat identity tracking uses the Android FirebaseAuth
    // listener, so declare the Android SDK here rather than relying on a
    // transitive dependency from the shared KMP module.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    // RevenueCat bundles Google Play Billing. Keeping it in the Android app module
    // makes the iOS target deliberately billing-free until its launch is scoped.
    implementation(libs.revenuecat.purchases)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.mcclabs.mook"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mcclabs.mook"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 9
        versionName = "1.3.3"

        // This is RevenueCat's Android *public* SDK key, which is intentionally
        // shipped in the app binary. Secret API keys never belong in a client build.
        buildConfigField("String", "REVENUECAT_PUBLIC_SDK_KEY", "\"goog_EuWWlzxxohTjBshyeDxGTVEJiiu\"")

        // SSO App Link alan adı. SsoConfig.APP_LINK_HOST ve assetlinks.json'un yayınlandığı
        // alan adıyla birebir aynı olmalıdır.
        manifestPlaceholders["ssoAppLinkHost"] = "mook-sso.web.app"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
