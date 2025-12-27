plugins {
    java
    id("io.papermc.paperweight.userdev").version("2.0.0-beta.19")
    id("xyz.jpenilla.run-paper").version("2.3.1")
    id("com.gradleup.shadow").version("9.0.0-beta4")
}

val pluginVersion: String by project
val minecraftVersion: String by project
val resourcepackPackFormat: String by project
val resourcepackPackFormatInt = resourcepackPackFormat.toInt()

group = "com.jnngl"
val artifactVersion = "$pluginVersion-mc$minecraftVersion"
version = artifactVersion

val paperVersion = "$minecraftVersion-R0.1-SNAPSHOT"
val resourcepackRoot = layout.projectDirectory.dir("resourcepack")
val resourcepackShadersRoot = resourcepackRoot.dir("assets/minecraft/shaders")
val pluginShadersRoot = layout.projectDirectory.dir("src/main/resources/shaders")

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
}

dependencies {
    paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:$paperVersion")
    implementation("net.elytrium:serializer:1.1.1")
    implementation("com.jnngl:mapcolor:1.0.1")
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    compileOnly("org.projectlombok:lombok:1.18.30")
    implementation("com.j256.ormlite:ormlite-jdbc:6.1")
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("net.elytrium.serializer", "com.jnngl.vanillaminimaps.serializer")
        exclude("org/slf4j/**")
        minimize()
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(
                "pluginVersion" to pluginVersion,
                "minecraftVersion" to minecraftVersion,
                "artifactVersion" to artifactVersion
            )
        }
    }

    register("syncResourcepackPackFormat") {
        group = "build"
        description = "Syncs resourcepack/pack.mcmeta pack_format with gradle.properties."
        doLast {
            val packMcmeta = resourcepackRoot.file("pack.mcmeta").asFile
            if (!packMcmeta.exists()) {
                throw org.gradle.api.GradleException("resourcepack/pack.mcmeta is missing.")
            }
            val content = packMcmeta.readText()
            val packFormatValue = resourcepackPackFormatInt
            val packPattern = Regex("\"pack_format\"\\s*:\\s*\\d+")
            val minPattern = Regex("\"min_format\"\\s*:\\s*\\d+")
            val maxPattern = Regex("\"max_format\"\\s*:\\s*\\d+")
            val includeMinMax = packFormatValue >= 64
            fun escapeJson(value: String): String {
                return value.replace("\\", "\\\\").replace("\"", "\\\"")
            }
            val description = Regex("\"description\"\\s*:\\s*\"(.*?)\"")
                .find(content)
                ?.groupValues
                ?.get(1)
                ?: ""
            val normalized = buildString {
                appendLine("{")
                appendLine("  \"pack\": {")
                appendLine("    \"pack_format\": $packFormatValue,")
                if (includeMinMax) {
                    appendLine("    \"min_format\": $packFormatValue,")
                    appendLine("    \"max_format\": $packFormatValue,")
                }
                appendLine("    \"description\": \"${escapeJson(description)}\"")
                appendLine("  }")
                append("}")
            }
            val updated = when {
                includeMinMax -> normalized
                minPattern.containsMatchIn(content) || maxPattern.containsMatchIn(content) -> normalized
                packPattern.containsMatchIn(content) -> packPattern.replace(content, "\"pack_format\": $packFormatValue")
                else -> normalized
            }
            if (updated != content) {
                packMcmeta.writeText(updated)
            }
        }
    }

    register("validateResourcepack") {
        group = "verification"
        description = "Validates the resource pack contents and pack_format."
        dependsOn("syncResourcepackPackFormat")
        doLast {
            val packMcmeta = resourcepackRoot.file("pack.mcmeta").asFile
            if (!packMcmeta.exists()) {
                throw org.gradle.api.GradleException("resourcepack/pack.mcmeta is missing.")
            }
            val content = packMcmeta.readText()
            val packFormatMatch = Regex("\"pack_format\"\\s*:\\s*(\\d+)").find(content)
            val packFormat = packFormatMatch?.groupValues?.get(1)?.toIntOrNull()
            if (packFormat != null) {
                if (packFormat != resourcepackPackFormatInt) {
                    throw org.gradle.api.GradleException(
                        "resourcepack/pack.mcmeta pack_format is $packFormat, expected $resourcepackPackFormatInt."
                    )
                }
            } else {
                throw org.gradle.api.GradleException("resourcepack/pack.mcmeta missing pack_format.")
            }
            val shadersDir = resourcepackShadersRoot.asFile
            if (!shadersDir.exists()) {
                throw org.gradle.api.GradleException("resourcepack/assets/minecraft/shaders is missing.")
            }
            val pluginShadersDir = pluginShadersRoot.asFile
            if (!pluginShadersDir.exists()) {
                throw org.gradle.api.GradleException("src/main/resources/shaders is missing.")
            }
            fun fileMap(root: java.io.File): Map<String, java.io.File> {
                return root.walkTopDown()
                    .filter { it.isFile }
                    .associateBy { it.relativeTo(root).invariantSeparatorsPath }
            }
            val resourcepackFiles = fileMap(shadersDir)
            val pluginFiles = fileMap(pluginShadersDir)
            val missing = resourcepackFiles.keys - pluginFiles.keys
            val extra = pluginFiles.keys - resourcepackFiles.keys
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    "Shader trees do not match. Missing in plugin: $missing. Extra in plugin: $extra."
                )
            }
            val mismatched = resourcepackFiles.keys.filter { key ->
                !resourcepackFiles.getValue(key).readBytes()
                    .contentEquals(pluginFiles.getValue(key).readBytes())
            }
            if (mismatched.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    "Shader contents differ between resourcepack and plugin resources: $mismatched."
                )
            }
        }
    }

    register<Copy>("syncShaderResources") {
        group = "build"
        description = "Syncs resource pack shaders into src/main/resources/shaders."
        from(resourcepackShadersRoot)
        into(pluginShadersRoot)
        exclude("**/.DS_Store")
    }

    register<Zip>("resourcepackZip") {
        group = "build"
        description = "Builds the VanillaMinimaps resource pack zip."
        dependsOn("validateResourcepack")
        from(resourcepackRoot) {
            exclude("**/.DS_Store")
        }
        archiveBaseName.set("vanillaminimaps-resourcepack")
        archiveVersion.set(project.version.toString())
        destinationDirectory.set(layout.buildDirectory.dir("resourcepack"))
    }
}

tasks.named("build") {
    finalizedBy("shadowJar", "resourcepackZip")
}
