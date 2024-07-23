/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *      http://aws.amazon.com/apache2.0/
 *
 *  or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package org.partiql.planner.internal.astPasses

import org.partiql.ast.Statement
import org.partiql.planner.internal.CaseNormalization
import org.partiql.planner.internal.PlannerFlag

/**
 * AST normalization
 */
internal fun Statement.normalize(flags: Set<PlannerFlag>): Statement {
    // could be a fold, but this is nice for setting breakpoints
    var ast = this
    val caseNormalization = flags.firstOrNull { it is CaseNormalization } as? CaseNormalization
    // Order matters here!
    ast = NormalizeIdentifier(caseNormalization).apply(ast)
    ast = NormalizeFromSource.apply(ast)
    ast = NormalizeGroupBy.apply(ast)
    return ast
}