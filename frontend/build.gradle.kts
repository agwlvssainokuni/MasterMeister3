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

import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node.gradle)
}

group = "cherry.mastermeister"
version = "0.1.0-SNAPSHOT"

node {
    download = true
    version = libs.versions.node.get()
    // Node.js の配布物は frontend/.node 配下に取得(gitignore 済み)
    workDir = file(".node")
    // 依存は package-lock.json に完全固定(NFR-U1-01)
    npmInstallCommand = "ci"
}

// npm run build: 型検査(tsc)→ ESLint → ライセンスヘッダー検査 → vite build
val npmBuild = tasks.register<NpmTask>("npmBuild") {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "build")
    inputs.dir("src")
    inputs.file("index.html")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    outputs.dir("dist")
}

val npmTest = tasks.register<NpmTask>("npmTest") {
    dependsOn(tasks.npmInstall)
    args = listOf("test")
}

tasks.assemble {
    dependsOn(npmBuild)
}

tasks.check {
    dependsOn(npmTest)
}

tasks.clean {
    delete("dist")
}
