import java.time.Duration

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

repositories {
    gradlePluginPortal()
}

nexusPublishing {
    // Documentation for this plugin, see https://github.com/gradle-nexus/publish-plugin/blob/v1.3.0/README.md
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://aws.oss.sonatype.org/service/local/"))
            // For CI environments, the username and password should be stored in
            // ORG_GRADLE_PROJECT_sonatypeUsername and ORG_GRADLE_PROJECT_sonatypePassword respectively.
            username.set(properties["ossrhUsername"].toString())
            password.set(properties["ossrhPassword"].toString())
        }
    }

    // these are not strictly required. The default timeouts are set to 1 minute. But Sonatype can be really slow.
    // If you get the error "java.net.SocketTimeoutException: timeout", these lines will help.
    connectTimeout = Duration.ofMinutes(3)
    clientTimeout = Duration.ofMinutes(3)
}

object Versions {
    const val detekt = "1.20.0-RC2"
    const val dokka = "1.6.10"
    const val kotlin = "1.6.20"
    const val ktlintGradle = "10.2.1"
    const val pig = "0.6.1"
    const val shadow = "8.1.1"
}

object Plugins {
    const val detekt = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${Versions.detekt}"
    const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:${Versions.dokka}"
    const val kotlinGradle = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    const val ktlintGradle = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.ktlintGradle}"
    const val pig = "org.partiql:pig-gradle-plugin:${Versions.pig}"
    const val shadow = "com.github.johnrengelman:shadow:${Versions.shadow}"
}

dependencies {
    implementation(Plugins.detekt)
    implementation(Plugins.dokka)
    implementation(Plugins.kotlinGradle)
    implementation(Plugins.ktlintGradle)
    implementation(Plugins.pig)
    implementation(Plugins.shadow)
}



allprojects {
    group = rootProject.properties["group"] as String
    version = rootProject.properties["version"] as String
}
