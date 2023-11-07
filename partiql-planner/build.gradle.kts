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
 *
 */

plugins {
    id(Plugins.conventions)
    id(Plugins.library)
    id(Plugins.testFixtures)
}

dependencies {
    api(project(":partiql-plan"))
    api(project(":partiql-types"))
    implementation(project(":partiql-ast"))
    implementation(project(":partiql-spi"))
    implementation(Deps.dotlin)
    implementation(Deps.ionElement)
    // Test
    testImplementation(Deps.kotlinxHTML)
    testImplementation(project(":partiql-parser"))
    testImplementation(project(":plugins:partiql-local"))
    // Test Fixtures
    testFixturesImplementation(project(":partiql-spi"))
}

tasks.processTestResources {
    from("src/testFixtures/resources")
}
