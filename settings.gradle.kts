/*
 * =============================================================
 * MUHAFIZ - GRADLE SETTINGS
 * =============================================================
 *
 * Plugin ve dependency repository yönetimi bu dosyada
 * merkezi olarak yapılır.
 */

pluginManagement {
    repositories {

        /*
         * Android Gradle Plugin ve Google tarafından
         * yayınlanan Gradle pluginleri.
         */
        google()

        /*
         * Kotlin ve diğer Maven artifact'leri.
         */
        mavenCentral()

        /*
         * Gradle plugin çözümleme deposu.
         *
         * Kotlin Compose Compiler plugin dahil Gradle pluginlerinin
         * çözümleme zincirinde kullanılabilir.
         */
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {

    /*
     * Repository tanımlarının modül build.gradle.kts
     * dosyalarına dağılmasını engeller.
     *
     * Tüm dependency repository'leri buradan yönetilir.
     */
    repositoriesMode.set(
        RepositoriesMode.FAIL_ON_PROJECT_REPOS
    )

    repositories {

        /*
         * AndroidX, Google Play Billing vb.
         */
        google()

        /*
         * Kotlin/JVM ve üçüncü taraf Maven dependency'leri.
         */
        mavenCentral()
    }
}

/*
 * =============================================================
 * PROJECT
 * =============================================================
 */

/*
 * Bunu şimdilik "Nazar" bırakıyoruz.
 *
 * Bu değer Google Play applicationId veya uygulamanın
 * kullanıcıya görünen adı DEĞİLDİR.
 *
 * Dolayısıyla:
 *
 * rootProject.name = "Nazar"
 *
 * kalması;
 *
 * applicationId = "com.tunix.muhafiz.guard"
 *
 * kullanmamıza engel değildir.
 */
rootProject.name = "Nazar"

/*
 * Android uygulama modülü.
 */
include(":app")