plugins {
    id("loader-make")
    `maven-publish`
}

group = "io.github.joemama"
version = "1.1-ALPHA"

val mmVersion = "0.4.0-a20240227"

loaderMake {
    version = "1.20.4"
}

dependencies {
    implementation(project(":loader"))
    compileOnlyApi("org.stianloader:micromixin-annotations:$mmVersion")
    runtimeOnly("org.stianloader:micromixin-runtime:$mmVersion")
    implementation("org.stianloader:micromixin-transformer:$mmVersion")
}

tasks.processResources {
    filesMatching("mods.toml") {
        expand("version" to version)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                artifactId = "micromixin"
            }
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/0xjoemama/public")
            credentials {
                username = System.getenv("REPSY_USERNAME")
                password = System.getenv("REPSY_PASSWORD")
            }
        }
    }
}
