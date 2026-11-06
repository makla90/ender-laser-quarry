plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.148"
}


// Jar name: ender_laser_quarry-<mc>-<mod>.jar
version = "${property("minecraft_version")}-${property("mod_version")}"
group = property("mod_group_id") as String

base {
    archivesName = property("mod_id") as String
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// GameTests, the client test, /elq demo and the dev tools (src/gametest): part of the mod in every dev run, never in the
// release jar (that is built from src/main only). Kept local, not in git.
val gametest by sourceSets.creating
configurations[gametest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[gametest.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
dependencies {
    gametest.implementationConfigurationName(sourceSets.main.get().output)
}

repositories {
    // Ender IO + EnderCore
    maven("https://maven.rover656.dev/releases") {
        content {
            includeGroup("com.enderio")
            includeGroup("dev.gigaherz.graph")
        }
    }
    // JEI (handy in dev, Ender IO integrates with it)
    maven("https://maven.blamejared.com") {
        content {
            includeGroup("mezz.jei")
            // JEI 29's config library
            includeGroup("net.mezzdev.config")
        }
    }
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
        // Dev tool: reference sample of every asteroid biome in the sampling dimensions (gametest/SamplingStudy),
        // raw counts + report in run-study/study
        create("samplingStudy") {
            server()
            programArgument("--nogui")
            gameDirectory = file("run-study")
            systemProperty("ender_laser_quarry.samplingStudy", file("run-study/study").absolutePath)
        }
        // Dev tool: real Analyze sampling of a few biomes on a dedicated server (gametest/SamplingCheck), report in
        // run-samplecheck/check_report.txt. Put mods into run-samplecheck/mods to check their ores
        create("samplingCheck") {
            server()
            programArgument("--nogui")
            gameDirectory = file("run-samplecheck")
            systemProperty("ender_laser_quarry.samplingCheck", file("run-samplecheck/check_report.txt").absolutePath)
            System.getProperty("onlyBiome")?.let { systemProperty("ender_laser_quarry.samplingCheck.only", it) }
        }
        // Dev tool: real claim mods (FTB Chunks, Open Parties and Claims in run-claimcheck/mods) vs real quarries on a
        // dedicated server (gametest/ClaimCheck), report in run-claimcheck/claim_report.txt
        create("claimCheck") {
            server()
            programArgument("--nogui")
            gameDirectory = file("run-claimcheck")
            systemProperty("ender_laser_quarry.claimCheck", file("run-claimcheck/claim_report.txt").absolutePath)
        }
        // Dev tool: a real client plays the mod by itself (gametest/ClientTest): demo base on a real coast, screenshots,
        // every depth mode on real chunks, report in run-clienttest/elq_clienttest/report.txt
        create("clientTest") {
            client()
            gameDirectory = file("run-clienttest")
            systemProperty("ender_laser_quarry.clientTest", "true")
            programArguments.addAll("--width", "1600", "--height", "900")
        }
        // Dev tool: screenshots of every one of our JEI recipes (gametest/RecipeShots), run-recipeshots/screenshots
        create("recipeShots") {
            client()
            gameDirectory = file("run-recipeshots")
            systemProperty("ender_laser_quarry.recipeShots", "true")
            programArguments.addAll("--width", "1600", "--height", "1000")
        }
        // Dev tool: every asteroid biome measured and baked into one space map (client/screen/AsteroidSheet)
        create("asteroidSheet") {
            client()
            gameDirectory = file("run-asteroidsheet")
            systemProperty("ender_laser_quarry.asteroidSheet", "true")
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
            // gradlew runGameTestServer -Delq.tests=TransferGameTests: only those test classes
            systemProperty("elq.tests", System.getProperty("elq.tests") ?: "")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
            // Dev runs load the tests and dev tools too (see the gametest source set)
            sourceSet = gametest
            // gradlew runX -Delq.jfr=C:/path/rec.jfr [-Delq.jfc=C:/path/settings.jfc]: Java Flight Recorder profile of that run
            System.getProperty("elq.jfr")?.let {
                jvmArgument("-XX:StartFlightRecording=filename=$it,settings=${System.getProperty("elq.jfc") ?: "profile"}")
            }
        }
    }

    mods {
        create(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }

    addModdingDependenciesTo(gametest)
}

dependencies {
    // Ender IO machine framework (PoweredMachineBlockEntity, capacitors, screens/widgets) + EnderCore
    implementation("com.enderio:enderio:${property("enderio_version")}")

    // JEI at runtime for testing
    runtimeOnly("mezz.jei:jei-26.1.2-neoforge:29.53.0.115")
    // JEI API for the dev tools only (gametest/RecipeShots), never in the mod
    gametest.compileOnlyConfigurationName("mezz.jei:jei-26.1.2-neoforge:29.53.0.115")
    // claim mod APIs for gametest/ClaimCheck only (the run loads them from run-claimcheck/mods)
    gametest.compileOnlyConfigurationName(fileTree("src/gametest/libs") { include("*.jar") })
    // Endergy capacitors at runtime for testing (optional for players)
    runtimeOnly("com.enderio:endergy:${property("endergy_version")}")
}

val metadataProperties = listOf(
        "minecraft_version_range",
        "neo_version_range",
        "enderio_version_range",
        "mod_id",
        "mod_name",
        "mod_license",
        "mod_version",
        "mod_authors",
        "mod_description",
).associateWith { project.property(it) as String }

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = metadataProperties
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

// Jar for testing in a normal launcher: the mod plus /elq demo (ender_laser_quarry-<mc>-<mod>-dev.jar). Only the demo
// and what it uses; the other dev tools switch themselves on or need the claim mods / JEI API.
val devJar by tasks.registering(Jar::class) {
    group = "build"
    archiveClassifier = "dev"
    from(sourceSets.main.get().output)
    from(gametest.output) {
        include("com/enderlaserquarry/gametest/DemoCommand*.class")
        include("com/enderlaserquarry/gametest/DemoPowerPlant*.class")
        include("com/enderlaserquarry/gametest/QuarryGameTests*.class")
        include("com/enderlaserquarry/gametest/TestQuarry*.class")
    }
    manifest.from(tasks.jar.get().manifest)
}

// show every compile error (the port has many), in English
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
}
