import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Generates serializers for @Serializable models (Interaction, Match) that
    // gitlive-firestore serializes when writing to Firestore.
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    publicResClass = true
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without this the compiler cannot derive a bundle ID from the exported
            // packages and falls back to the bundle *name* ("Shared") with a warning
            // on every build. Naming it explicitly also keeps the framework's
            // CFBundleIdentifier distinct from the app's own.
            binaryOption("bundleId", "com.mcclabs.mook.shared")
        }
    }
    
    androidLibrary {
       namespace = "com.mcclabs.mook.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.1.2"))
            // Android reads its own FCM token (see PushToken.android.kt). iOS cannot —
            // FirebaseMessaging is linked into the Xcode target there, and the token is
            // handed in from Swift instead.
            implementation("com.google.firebase:firebase-messaging")
            // The purchase/restore path forces an ID-token refresh after RevenueCat
            // confirms a transaction, so newly-written entitlement claims are picked
            // up without requiring the user to relaunch the app.
            implementation("com.google.firebase:firebase-auth")
            implementation("com.google.firebase:firebase-functions")
            implementation("com.google.android.gms:play-services-ads:25.5.0")
            // Gereksinim 2 (Faz 6): Play Integrity tabanlı App Check sağlayıcısı --
            // bkz. AppCheckInstaller.kt ve LimitedUseAppCheckCallableInvoker.android.kt.
            implementation("com.google.firebase:firebase-appcheck-playintegrity")
            implementation("com.google.android.ump:user-messaging-platform:4.0.0")
            // Zorla güncelleme (Force Update) için Google Play In-App Updates SDK'sı.
            implementation("com.google.android.play:app-update:2.1.0")
            // Android-first commercial implementation. The common subscription contract
            // remains iOS-safe through an explicit no-op actual implementation.
            implementation(libs.revenuecat.purchases)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            // `org.jetbrains.compose.ui:ui-tooling-preview` resolves to the *androidx*
            // artifact on the Android target, which has no org.jetbrains.compose
            // `@Preview`. The components variant is the multiplatform one, so a
            // @Preview in commonMain compiles for both targets.
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.storage)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.functions)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        getByName("androidHostTest").dependencies {
            // SSO güvenlik testleri JUnit 5 + MockK ile JVM üzerinde çalışır. Vintage motoru,
            // commonTest'teki mevcut kotlin.test (JUnit 4) testlerinin de aynı çalıştırmada
            // koşmaya devam etmesini sağlar.
            implementation(project.dependencies.platform(libs.junit5.bom))
            implementation(libs.junit5.jupiter)
            implementation(libs.mockk)
            runtimeOnly(libs.junit5.vintage.engine)
            runtimeOnly(libs.junit5.platform.launcher)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Virtual time for the WalkTalk demo's debounce / stale-response tests.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
