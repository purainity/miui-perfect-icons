plugins {
    alias(libs.plugins.android.application)
}

val releaseStoreFile = providers.gradleProperty("ANDROID_RELEASE_KEYSTORE_FILE").orNull?.let { project.file(it) }
val releaseStorePassword = providers.gradleProperty("ANDROID_RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("ANDROID_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("ANDROID_RELEASE_KEY_PASSWORD").orNull

val releaseSigningAvailable = releaseStoreFile?.isFile == true && !releaseStorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

// 构建开始时输出签名环境变量状态，未设置时按请求的变体只提示对应一句
val requestedTasks = gradle.startParameter.taskNames
val wantsDebug = requestedTasks.any { it.contains("debug", ignoreCase = true) }
val wantsRelease = requestedTasks.any { it.contains("release", ignoreCase = true) }
if (releaseSigningAvailable) {
    logger.lifecycle("Signing environment variables are set, APKs will be signed")
} else if (wantsDebug && !wantsRelease) {
    logger.lifecycle("Signing environment variables are not set, debug builds will use the default debug signing")
} else if (wantsRelease && !wantsDebug) {
    logger.lifecycle("Signing environment variables are not set, release builds will be unsigned")
} else {
    logger.lifecycle("Signing environment variables are not set, debug builds will use the default debug signing and release builds will be unsigned")
}

android {
    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = checkNotNull(releaseStoreFile)
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    namespace = "me.purainity.miuiperfecticons"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.purainity.miuiperfecticons"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
            }
        }
        debug {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    // 纯 JVM 单元测试环境没有 Android 运行时，android.jar 里的 org.json 是只抛 "not mocked" 异常的空壳，用 JSONObject 构造夹具的测试类会直接崩溃，因此需要真实现
    // 运行时不受影响：APP 使用 Android 自带的 org.json，此依赖仅进入测试的 classpath
    // 可用 ./gradlew app:dependencies --configuration releaseRuntimeClasspath 验证产物依赖树不含 org.json
    // 纯 Java 库、零传递依赖，不触碰行为红线
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
