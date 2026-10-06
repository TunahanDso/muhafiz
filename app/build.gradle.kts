import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

    // Kotlin 2.x Compose Compiler plugin.
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "com.tunix.nazar"

    /*
     * =============================================================
     * ANDROID SDK
     * =============================================================
     *
     * Muhafiz release hatti:
     *
     * compileSdk = 36
     * targetSdk  = 36
     * minSdk     = 30
     *
     * Toolchain:
     *
     * Gradle 8.13
     * AGP 8.13.2
     * Kotlin 2.3.21
     * JDK 17
     */
    compileSdk = 36

    defaultConfig {

        /*
         * =============================================================
         * APPLICATION ID
         * =============================================================
         *
         * Eski Google Play uygulamasi:
         *
         * com.tunix.mn
         *
         * Yeni Google Play uygulamasi:
         *
         * com.tunix.muhafiz.guard
         *
         * namespace bilerek eski kaynak package'i olan
         * com.tunix.nazar olarak kalir.
         */
        applicationId = "com.tunix.muhafiz.guard"

        minSdk = 30
        targetSdk = 36

        /*
         * =============================================================
         * VERSION
         * =============================================================
         */
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }


    /*
     * =============================================================
     * PRODUCT FLAVORS
     * =============================================================
     *
     * production
     * -------------------------------------------------------------
     *
     * applicationId:
     * com.tunix.muhafiz.guard
     *
     * versionName:
     * 1.0.1
     *
     *
     * lab
     * -------------------------------------------------------------
     *
     * applicationId:
     * com.tunix.muhafiz.guard.test
     *
     * versionName:
     * 1.0.1-test
     */
    flavorDimensions += "distribution"

    productFlavors {

        /*
         * ---------------------------------------------------------
         * PRODUCTION / GOOGLE PLAY
         * ---------------------------------------------------------
         */
        create("production") {
            dimension = "distribution"
        }


        /*
         * ---------------------------------------------------------
         * LAB / DEVELOPMENT TESTING
         * ---------------------------------------------------------
         */
        create("lab") {
            dimension = "distribution"

            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
    }


    /*
     * =============================================================
     * BUILD TYPES
     * =============================================================
     */
    buildTypes {

        debug {
            isMinifyEnabled = false
        }


        release {

            /*
             * Ilk temiz Google Play release'inde R8 kaynakli yeni
             * bir degisken olusturmamak icin minification simdilik
             * kapali tutuluyor.
             *
             * Release hatti stabil olduktan sonra:
             *
             * - R8
             * - resource shrinking
             * - ilgili ProGuard kurallari
             *
             * ayrica test edilerek etkinlestirilebilir.
             */
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }


    /*
     * =============================================================
     * JAVA
     * =============================================================
     *
     * Gradle / AGP / Kotlin derleme zincirimiz JDK 17 ile
     * sabitlenmistir.
     */
    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }


    /*
     * =============================================================
     * JETPACK COMPOSE
     * =============================================================
     *
     * Kotlin 2.x ile:
     *
     * composeOptions {
     *     kotlinCompilerExtensionVersion = "..."
     * }
     *
     * kullanilmiyor.
     *
     * Bunun yerine:
     *
     * org.jetbrains.kotlin.plugin.compose
     *
     * kullaniliyor.
     */
    buildFeatures {
        compose = true
    }


    /*
     * =============================================================
     * PACKAGING
     * =============================================================
     */
    packaging {
        resources {
            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


/*
 * =============================================================
 * KOTLIN
 * =============================================================
 *
 * Eski android.kotlinOptions DSL yerine modern compilerOptions
 * kullaniliyor.
 */
kotlin {

    /*
     * Kotlin derlemelerinde JDK 17 toolchain.
     */
    jvmToolchain(17)

    compilerOptions {
        jvmTarget =
            JvmTarget.JVM_17
    }
}


dependencies {

    /*
     * =============================================================
     * ANDROIDX CORE
     * =============================================================
     */

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        libs.androidx.activity.compose
    )


    /*
     * =============================================================
     * ANDROIDX FRAGMENT
     * =============================================================
     *
     * Muhafiz Fragment tabanli bir UI kullanmiyor.
     *
     * Ancak Activity Result API:
     *
     * registerForActivityResult(...)
     *
     * kullaniyor.
     *
     * Production release lint kontrolu classpath'te eski /
     * gecersiz Fragment altyapisi algiladigi icin Fragment
     * dependency'sini modern bir surume acikca sabitliyoruz.
     *
     * Surum libs.versions.toml tarafinda:
     *
     * fragment = "1.9.1"
     *
     * olarak yonetiliyor.
     *
     * fragment-ktx gerekli degil.
     */
    implementation(
        "androidx.fragment:fragment:1.9.1"
    )


    /*
     * =============================================================
     * JETPACK COMPOSE BOM
     * =============================================================
     *
     * Compose UI / Foundation / Material3 kutuphanelerinin
     * birbirleriyle ayni uyumlu release hattinda kalmasini
     * saglar.
     *
     * enforcedPlatform kullanmamizin nedeni farkli transitive
     * dependency'lerin Compose versiyonlarini ayirmasini
     * engellemektir.
     */
    implementation(
        enforcedPlatform(
            libs.androidx.compose.bom
        )
    )


    /*
     * =============================================================
     * COMPOSE UI
     * =============================================================
     */

    implementation(
        libs.androidx.ui
    )

    implementation(
        libs.androidx.ui.graphics
    )

    implementation(
        libs.androidx.ui.tooling.preview
    )

    implementation(
        libs.androidx.material3
    )


    /*
     * =============================================================
     * COMPOSE FOUNDATION
     * =============================================================
     *
     * Surum Compose BOM tarafindan yonetilir.
     */
    implementation(
        "androidx.compose.foundation:foundation"
    )

    implementation(
        "androidx.compose.foundation:foundation-layout"
    )


    /*
     * =============================================================
     * LIFECYCLE / VIEWMODEL
     * =============================================================
     *
     * Mevcut API 36 release hattinda Lifecycle 2.10.0
     * kullaniliyor.
     */
    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0"
    )


    /*
     * =============================================================
     * DATASTORE
     * =============================================================
     */
    implementation(
        "androidx.datastore:datastore-preferences:1.2.1"
    )


    /*
     * =============================================================
     * GOOGLE PLAY BILLING
     * =============================================================
     *
     * BillingManager:
     *
     * - gercek Billing ready state
     * - reconnect
     * - pending purchase devam ettirme
     * - kullanici hata geri bildirimi
     * - product / offer kontrolu
     * - purchase acknowledgement
     *
     * akisini yonetir.
     */
    implementation(
        "com.android.billingclient:billing:9.1.0"
    )


    /*
     * =============================================================
     * ML / NSFW MODEL
     * =============================================================
     *
     * Mevcut kod:
     *
     * org.tensorflow.lite.Interpreter
     *
     * ve:
     *
     * org.tensorflow.lite.support.common.FileUtil
     *
     * kullandigi icin mevcut TensorFlow Lite bagimliliklari
     * simdilik korunuyor.
     *
     * tensorflow-lite-gpu kaldirildi.
     *
     * LiteRT ve native / 16 KB page-size migrasyonu ayri bir
     * release sertlestirme adiminda ele alinacak.
     */
    implementation(
        "org.tensorflow:tensorflow-lite:2.16.1"
    )

    implementation(
        "org.tensorflow:tensorflow-lite-support:0.4.4"
    )


    /*
     * =============================================================
     * UNIT TEST
     * =============================================================
     */
    testImplementation(
        libs.junit
    )


    /*
     * =============================================================
     * ANDROID INSTRUMENTATION TEST
     * =============================================================
     */
    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        enforcedPlatform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.ui.test.junit4
    )


    /*
     * =============================================================
     * DEBUG / COMPOSE TOOLS
     * =============================================================
     *
     * Release APK/AAB'ye dahil edilmez.
     */
    debugImplementation(
        libs.androidx.ui.tooling
    )

    debugImplementation(
        libs.androidx.ui.test.manifest
    )
}