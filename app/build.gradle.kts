plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ip"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ip"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

dependencies {
    // ✅ SSH-библиотеки
    implementation("com.hierynomus:sshj:0.37.0")
    implementation("com.jcraft:jsch:0.1.55")

    // ✅ AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ✅ Apache POI для Excel
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.commons:commons-collections4:4.4")

    // ✅ WorkManager для фоновых задач
    implementation("androidx.work:work-runtime:2.9.0")

    // ✅ Core KTX (для уведомлений и удобных API)
    implementation("androidx.core:core-ktx:1.13.1")

    // ✅ Тесты
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
