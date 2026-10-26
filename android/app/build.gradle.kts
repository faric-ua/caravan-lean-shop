import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Стабільний ключ підпису Caravan Lean.
// CI: змінні середовища CL_* (із секретів GitHub).
// Телефон: ~/.android/caravan-lean-signing.properties (створює меню, пункт 7).
// Без ключа збирається лише непідписаний APK — його не публікуємо.
val phoneSigningFile = File(System.getProperty("user.home"), ".android/caravan-lean-signing.properties")
val phoneSigning = Properties().apply {
    if (phoneSigningFile.isFile) phoneSigningFile.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: phoneSigning.getProperty(prop)?.takeIf { it.isNotBlank() }

val clKeystoreFile = signingValue("CL_KEYSTORE_PATH", "storeFile")?.let { file(it) }
val clStorePassword = signingValue("CL_STORE_PASSWORD", "storePassword")
val clKeyAlias = signingValue("CL_KEY_ALIAS", "keyAlias")
val clKeyPassword = signingValue("CL_KEY_PASSWORD", "keyPassword")
val clSigningAvailable = clKeystoreFile?.isFile == true &&
    !clStorePassword.isNullOrBlank() && !clKeyAlias.isNullOrBlank() && !clKeyPassword.isNullOrBlank()

// Коміт коду, з якого зібрано APK: показується в «Про застосунок».
val clCommit: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short=7", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifBlank { "?" }
} catch (e: Exception) {
    "?"
}

val clCatalogUrl: String = providers.environmentVariable("CL_CATALOG_URL").orNull
    ?: providers.gradleProperty("caravanlean.catalogUrl").orNull ?: ""

android {
    namespace = "ua.faric.caravanlean"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.faric.caravanlean"
        // Android 8+: адаптивний значок без PNG-запасок.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIT_COMMIT", "\"$clCommit\"")
        // Адреса свіжого каталогу (формат catalog.json). Порожньо — лише знімок у APK + живі ціни з сайту.
        // Публічне репо: raw.githubusercontent.com/…/data/catalog/catalog.json; потім — сервер магазину.
        buildConfigField("String", "CATALOG_URL", "\"$clCatalogUrl\"")
    }

    signingConfigs {
        create("stable") {
            if (clSigningAvailable) {
                storeFile = clKeystoreFile
                storePassword = clStorePassword
                keyAlias = clKeyAlias
                keyPassword = clKeyPassword
            }
        }
    }

    buildTypes {
        // Робоча збірка для телефона — release без мініфікації (як у Track Catcher).
        release {
            isMinifyEnabled = false
            if (clSigningAvailable) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Сітки плиток (групи, товари) — швидко навіть на тисячах товарів.
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // Розбір HTML сторінок сайту на телефоні (живе оновлення ціни й наявності).
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
    // У юніт-тестах Android-заглушки org.json не працюють — справжня бібліотека.
    testImplementation("org.json:json:20240303")
}
