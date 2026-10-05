// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}