import io.github.gradlenexus.publishplugin.NexusPublishExtension
import java.time.Duration

plugins {
    id("io.github.gradle-nexus.publish-plugin")
}

rootProject.run {

    plugins.apply("io.github.gradle-nexus.publish-plugin")

    extensions.getByType(NexusPublishExtension::class.java).run {

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
        connectTimeout.set(Duration.ofMinutes(3))
        clientTimeout.set(Duration.ofMinutes(3))
    }
}
