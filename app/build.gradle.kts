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

    // ✅ Обязательно для Java-кода (у тебя всё на Java)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ✅ Поддержка ViewBinding (если ты используешь XML View)
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ✅ SSH-библиотеки
    implementation("com.hierynomus:sshj:0.37.0")
    implementation("com.jcraft:jsch:0.1.55")

    // ✅ AndroidX
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // ✅ WorkManager (фоновые задачи)
    implementation("androidx.work:work-runtime:2.9.0")

    // ✅ Apache POI для Excel
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.commons:commons-collections4:4.4")

    // ✅ Тесты
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
