/*

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSet

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.tasks.Jar
import java.io.Writer
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import kotlin.concurrent.thread
*/

import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.JavaVersion.VERSION_11

fun properties(key: String) = project.findProperty(key).toString()

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    jcenter()

    flatDir {
        dirs("libs")
    }
}

var kotlinVersion: String by extra

buildscript {
    var kotlinVersion: String by extra

    kotlinVersion = "1.4.31"
    repositories { mavenCentral() }

    dependencies { classpath(kotlin("gradle-plugin", kotlinVersion)) }
}

plugins {
    //idea
    kotlin("jvm") version "1.4.31"
    id("org.jetbrains.intellij") version "0.7.2"
}

allprojects {
    apply {
            //plugin("idea")
            plugin("kotlin")
            //plugin("org.jetbrains.grammarkit")
            plugin("org.jetbrains.intellij")
    }

    intellij {
        //updateSinceUntilBuild = false
        //instrumentCode = true

        version = "2020.3.3"
        pluginName = "IntellijEve"
        //setPlugins("org.toml.lang:0.2.143","org.rust.lang:0.3.143")
    }


    configure<JavaPluginConvention> {
        sourceCompatibility = VERSION_11
        targetCompatibility = VERSION_11
    }

    tasks.withType<KotlinCompile> {
        /*
        dependsOn(
                genParser,
                genLexer,
                genDocfmtParser,
                genDocfmtLexer,
                sortSpelling
        )
        */
        kotlinOptions {
            jvmTarget = "11"
            languageVersion = "1.4"
            apiVersion = "1.4"
            freeCompilerArgs = listOf("-Xjvm-default=enable")
        }
    }


    tasks.withType<PatchPluginXmlTask> {
        //changeNotes(file("res/META-INF/change-notes.html").readText())
        //pluginDescription(file("res/META-INF/description.html").readText())
        version(properties("pluginVersion"))
        pluginId(properties("pluginGroup"))
        println(pluginId)
    }

    sourceSets {
        getByName("main").apply {
            //kotlin.srcDirs("src/main/kotlin")
            resources.srcDir("resources")
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("commons-io:commons-io:2.8.0")
        implementation(group = "org.rust.lang",  name = "intellij-rust-0.3.143")
        implementation(group = "org.toml.lang", name = "intellij-toml-0.2.143")

        //compile(kotlin("stdlib"))
        implementation(kotlin("stdlib-jdk8"))
    }
}