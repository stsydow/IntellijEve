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
import org.gradle.api.JavaVersion.VERSION_1_8
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

val packageName = "org.tub.eveamcp"
val pluginVersion = "0.1.2"

group = packageName
version = pluginVersion
var kotlinVersion: String by extra


buildscript {
    var kotlinVersion: String by extra

    kotlinVersion = "1.3.0"
    repositories { mavenCentral() }

    dependencies { classpath(kotlin("gradle-plugin", kotlinVersion)) }
}

plugins {
    //id 'java-gradle-plugin' - breaks XML Parser classpath
    //idea
    kotlin("jvm") version "1.3.0"
    id("org.jetbrains.intellij") version "0.3.12"
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

        version = "2020.1"
        pluginName = "IntellijEve"
        setPlugins(
                "org.toml.lang:0.2.120",
                "org.rust.lang:0.2.120"
        )
    }


    configure<JavaPluginConvention> {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
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
            jvmTarget = "1.8"
            languageVersion = "1.3"
            apiVersion = "1.3"
            freeCompilerArgs = listOf("-Xjvm-default=enable")
        }
    }


    tasks.withType<PatchPluginXmlTask> {
        //changeNotes(file("res/META-INF/change-notes.html").readText())
        //pluginDescription(file("res/META-INF/description.html").readText())
        version(pluginVersion)
        pluginId(packageName)
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
        compile("commons-io:commons-io:2.6")
        //compile(kotlin("stdlib"))
        compile(kotlin("stdlib-jdk8"))
    }
}