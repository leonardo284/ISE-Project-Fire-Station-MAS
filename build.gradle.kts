plugins {
    java
}

group = "fireStation.mas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Dipendenza ufficiale di Jason per Java 17/21
    implementation("io.github.jason-lang:jason-interpreter:3.2.1")
    testImplementation("junit", "junit", "4.13.2")
}

sourceSets {
    main {
        resources {
            srcDir("src/main/asl")
        }
    }
}

// Task per avviare il file .mas2j comodamente
tasks.register<JavaExec>("runMas") {
    group = "run"
    classpath = sourceSets.getByName("main").runtimeClasspath
    mainClass.set("jason.infra.centralised.RunCentralisedMAS")
    args(file("src/main/firestation.mas2j").absolutePath)
    standardInput = System.`in`
}