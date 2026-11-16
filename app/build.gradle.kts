// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val packageName = "wang.lucian.quietcalls"
val appVersionName: String = "0.8.0"
val selfhostMinServerVersion: String = libs.versions.selfhost.min.server.get()
val stableSemanticVersionPattern = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

if (!stableSemanticVersionPattern.matches(selfhostMinServerVersion)) {
    throw GradleException("Invalid self-host minimum server version")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

android {
    namespace = packageName
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "wang.lucian.quietcalls"
        testInstrumentationRunner = "wang.lucian.quietcalls.RuleDeviceVerification"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 306
        versionName = appVersionName
        buildConfigField(
            "String",
            "MIN_SELFHOST_SERVER_VERSION",
            "\"${libs.versions.selfhost.min.server.get()}\"",
        )
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("sign") {
            val keyPath = System.getenv("SIGN_KEY_STORE_FILE")
            if (!keyPath.isNullOrBlank()) {
                storeFile = file(keyPath)
                storePassword = System.getenv("SIGN_KEY_STORE_PASSWORD")
                keyAlias = System.getenv("SIGN_KEY_ALIAS")
                keyPassword = System.getenv("SIGN_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            if (!System.getenv("SIGN_KEY_STORE_FILE").isNullOrBlank()) signingConfig = signingConfigs.getByName("sign")
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
            versionNameSuffix = ".release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("sign")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
    }
    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters.add("en")
        localeFilters.add("zh-rCN")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.compose.preference)
    implementation(libs.smartspacer.sdk.plugin)
    implementation(libs.zxing.embedded)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}

// 使用类型安全的签名 DSL；未配置发布密钥时不执行发布构建。
