// File di build principale: i plugin vengono applicati nel modulo :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
