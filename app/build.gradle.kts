plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// يُطبَّق مكوّن Google Services فقط عند وجود google-services.json (غير مرفوع إلى المستودع).
// بدونه يُبنى التطبيق بنجاح ويعمل في «وضع البيانات التجريبية» دون Firebase.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

fun config(name: String): String =
    (project.findProperty(name) as String?) ?: System.getenv(name) ?: ""

// ---- Start.io: ميتا-داتا App ID تُحقن في المانيفست فقط عند توفر المعرّف ----
// بدون معرّف: لا تُضاف الميتا-داتا ولا يُهيَّأ SDK وتبقى الإعلانات معطلة بالكامل.
val startioId = config("STARTIO_APP_ID").trim()
val baseManifest = file("src/main/AndroidManifest.xml")
val manifestForBuild: File = if (startioId.isNotEmpty()) {
    require(Regex("^[A-Za-z0-9_-]{4,64}$").matches(startioId)) { "STARTIO_APP_ID يحتوي محارف غير صالحة" }
    val out = layout.buildDirectory.file("generated/manifest/AndroidManifest.xml").get().asFile
    out.parentFile.mkdirs()
    out.writeText(
        baseManifest.readText().replace(
            "</application>",
            "    <meta-data android:name=\"com.startapp.sdk.APPLICATION_ID\" android:value=\"$startioId\" />\n    </application>"
        )
    )
    out
} else baseManifest

android {
    namespace = "jo.guide.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "jo.guide.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // معرّف Start.io يأتي من البناء فقط. فارغ = الإعلانات معطلة.
        buildConfigField("String", "STARTIO_APP_ID", "\"${config("STARTIO_APP_ID")}\"")
        buildConfigField("String", "ADMIN_PANEL_URL", "\"${config("ADMIN_PANEL_URL")}\"")
        buildConfigField("boolean", "ADS_CONFIGURED", (startioId.isNotEmpty()).toString())
    }

    sourceSets.getByName("main").manifest.srcFile(manifestForBuild)

    signingConfigs {
        // توقيع الإصدار يتطلب مفاتيح تضعها أنت كأسرار في GitHub (انظر README).
        if (config("KEYSTORE_PATH").isNotEmpty()) {
            create("release") {
                storeFile = file(config("KEYSTORE_PATH"))
                storePassword = config("KEYSTORE_PASSWORD")
                keyAlias = config("KEY_ALIAS")
                keyPassword = config("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.coroutines.play)

    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // SDK الأصلي لـ Start.io (Android). لا يعمل في الويب.
    implementation(libs.startio)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
