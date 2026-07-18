/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    war
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
}

group = "cherry.mastermeister"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    providedRuntime(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("com.h2database:h2")

    // 実行可能 WAR: 同梱 Tomcat で起動しつつ、外部コンテナへの配備も可能にする。
    // Boot 4 では starter-tomcat を providedRuntime にすると Gradle が推移依存
    // (spring-web 等)ごと lib-provided へ退避してしまうため、Gradle 専用の
    // starter-tomcat-runtime を使う(Boot 4 公式ドキュメントの指定)
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // PBT(PBT-09: jqwik)
    testImplementation(libs.jqwik)

    // 対象 RDBMS の実エンジン結合テスト(Testcontainers)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-mariadb")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("com.mysql:mysql-connector-j")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client")
    testRuntimeOnly("org.postgresql:postgresql")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:all")
    options.encoding = "UTF-8"
}

// 成果物名はサブプロジェクト名(backend)ではなくアプリ名 + バージョンとする
tasks.bootWar {
    archiveBaseName = "mastermeister"
}

// フロントエンドのビルド成果物(frontend/dist)を WAR の静的リソース(static/)として同梱する(D-14)
tasks.processResources {
    dependsOn(":frontend:npmBuild")
    from(rootProject.layout.projectDirectory.dir("frontend/dist")) {
        into("static")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Testcontainers の Docker 検出: DOCKER_HOST 未設定で colima を使っている環境では
    // ソケットの場所を自動設定する(標準の /var/run/docker.sock が存在しないため)
    if (System.getenv("DOCKER_HOST") == null) {
        val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
        if (colimaSocket.exists()) {
            environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
            environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        licenseHeaderFile(rootProject.file("config/spotless/license-header.java"))
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
