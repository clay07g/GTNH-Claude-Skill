plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("kapt") version "2.0.21"
    application
}

group = "com.claygillman"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    kapt("info.picocli:picocli-codegen:4.7.6")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.claygillman.gtnh.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// Configure kapt to generate picocli reflection config
kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}

// Fat JAR for distribution
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.claygillman.gtnh.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Build skill folder for distribution
// Usage: ./gradlew buildSkill [-PgtnhPath="/path/to/GTNH/installation"]
tasks.register("buildSkill") {
    dependsOn("jar")
    group = "distribution"
    description = "Build skill folder with JAR and optionally pre-indexed quest data"

    doLast {
        val releaseDir = file("build/release/gtnh-skill")
        releaseDir.deleteRecursively()
        releaseDir.mkdirs()

        // Copy fat JAR as gtnh-skill.jar
        copy {
            from("build/libs") {
                include("*.jar")
            }
            into(releaseDir)
            rename { "gtnh-skill.jar" }
        }

        // Copy SKILL.md
        copy {
            from("skill")
            into(releaseDir)
        }

        // If gtnhPath provided, index quests into DB
        val gtnhPath = project.findProperty("gtnhPath") as String?
        if (gtnhPath != null) {
            println("Indexing quest data from: $gtnhPath")
            javaexec {
                classpath = files("build/libs/${project.name}-${project.version}.jar")
                mainClass.set("com.claygillman.gtnh.MainKt")
                args = listOf("-d", "${releaseDir.absolutePath}/gtnh.db", "index", "-p", gtnhPath, "--force")
            }
            println("Quest database created at: ${releaseDir.absolutePath}/gtnh.db")
        } else {
            println("No gtnhPath provided - skill built without pre-indexed data")
            println("To include quest data: ./gradlew buildSkill -PgtnhPath=\"/path/to/GTNH\"")
        }

        println("\nSkill folder created at: ${releaseDir.absolutePath}")
    }
}
