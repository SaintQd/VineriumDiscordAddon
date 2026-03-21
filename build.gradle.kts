plugins {
    id("java")
}

group = "org.saintqd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        maven(url = "https://nexus.scarsz.me/content/groups/public/")
        maven(url = "https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.discordsrv:discordsrv:1.30.2")
    compileOnly(files("../VineriumLib/build/libs/VineriumLib-1.0-SNAPSHOT.jar"))
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.maxmind.geoip2:geoip2:5.0.1")
}

tasks.test {
    useJUnitPlatform()
}
tasks.withType<Jar> {

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all the dependencies otherwise a "NoClassDefFoundError" error
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}