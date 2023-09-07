/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *      http://aws.amazon.com/apache2.0/
 *
 *  or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 *  language governing permissions and limitations under the License.
 */
package org.partiql.lang.eval.visitors

import com.amazon.ionelement.api.MetaContainer
import com.amazon.ionelement.api.emptyMetaContainer
import org.partiql.errors.ErrorCode
import org.partiql.lang.ast.IsGroupAttributeReferenceMeta
import org.partiql.lang.ast.UniqueNameMeta
import org.partiql.lang.domains.PartiqlAst
import org.partiql.lang.domains.metaContainerOf
import org.partiql.lang.domains.toId
import org.partiql.lang.eval.errNoContext

/** Desugars `SELECT *` by, for example,
 *  transforming  `SELECT * FROM A as x, B as y at i`
 *            to  `SELECT x.*, y.*, i FROM A as x, B as y at i`
 *  and transforming `SELECT * FROM ... GROUP BY E as x, D as y GROUP as g`
 *                to `SELECT x, y, g FROM ... GROUP BY E as x, D as y GROUP as g`
 *
 *  Requires that [FromSourceAliasVisitorTransform] and [GroupByItemAliasVisitorTransform]
 *  have already been applied.
 */
class SelectStarVisitorTransform : VisitorTransformBase() {

    override fun transformExprSelect(node: PartiqlAst.Expr.Select): PartiqlAst.Expr {
        val transformedExpr = super.transformExprSelect(node) as PartiqlAst.Expr.Select

        val projection = transformedExpr.project

        // Check if SELECT * is being used.
        if (projection is PartiqlAst.Projection.ProjectStar) {
            when (transformedExpr.group) {
                null -> { // No group by
                    val fromSourceAliases = extractAliases(transformedExpr.from)

                    val newProjection =
                        PartiqlAst.build {
                            projectList(
                                fromSourceAliases.map { aliases ->
                                    // We are concatenating 3 lists here
                                    listOf(createProjectAll(aliases.asAlias)) +
                                        (aliases.atAlias?.let { listOf(createProjectExpr(it)) } ?: emptyList()) +
                                        (aliases.byAlias?.let { listOf(createProjectExpr(it)) } ?: emptyList())
                                }.flatten(),
                                transformedExpr.metas
                            )
                        }
                    return transformedExpr.copy(project = newProjection)
                }
                else -> { // With group by
                    val selectListItemsFromGroupBy = transformedExpr.group!!.keyList.keys.map {
                        val asName = it.asAlias
                            ?: errNoContext(
                                "GroupByItem has no AS-alias--GroupByItemAliasVisitorTransform must be executed before SelectStarVisitorTransform",
                                errorCode = ErrorCode.SEMANTIC_MISSING_AS_NAME,
                                internal = true
                            )

                        // We need to take the unique name of each grouping field key only because we need to handle
                        // the case when multiple grouping fields are assigned the same name (which is currently allowed)
                        val uniqueNameMeta = asName.metas[UniqueNameMeta.TAG] as? UniqueNameMeta?
                            ?: error("UniqueNameMeta not found--normally, this is added by GroupByItemAliasVisitorTransform")
                        val uniqueNameId = PartiqlAst.build { defnid(uniqueNameMeta.uniqueName, delimited()) }

                        val metas = it.metas + metaContainerOf(IsGroupAttributeReferenceMeta.instance)
                        createProjectExpr(uniqueNameId, asName, metas)
                    }

                    val groupNameItem = transformedExpr.group!!.groupAsAlias.let {
                        if (it != null) {
                            val metas = it.metas + metaContainerOf(IsGroupAttributeReferenceMeta.instance)
                            listOf(createProjectExpr(it, metas = metas))
                        } else emptyList()
                    }

                    val newProjection = PartiqlAst.build { projectList(selectListItemsFromGroupBy + groupNameItem, metas = transformMetas(projection.metas)) }

                    return transformedExpr.copy(project = newProjection)
                }
            }
        }
        return transformedExpr
    }

    private fun createProjectAll(name: PartiqlAst.Defnid) =
        PartiqlAst.build {
            projectAll(vr(name.toId(), unqualified(), emptyMetaContainer()))
        }

    private fun createProjectExpr(
        variableName: PartiqlAst.Defnid,
        asAlias: PartiqlAst.Defnid = variableName,
        metas: MetaContainer = emptyMetaContainer()
    ) =
        PartiqlAst.build {
            // SQL-ids Use of delimited() in the prior code above might explain why some of the tests in SelectStarVisitorTransformTests
            // had the same variable both delimited and not, such as `"f"` and `f`, in the same test.
            // Now, the kind of the variable is carried through from its definition site and does not need to be invented here, at a reference site.
            projectExpr(vr(variableName.toId(), unqualified(), metas), asAlias)
        }

    private class FromSourceAliases(val asAlias: PartiqlAst.Defnid, val atAlias: PartiqlAst.Defnid?, val byAlias: PartiqlAst.Defnid?)

    /** Extracts all the FROM source/unpivot aliases without recursing into any nested queries */
    private fun extractAliases(fromSource: PartiqlAst.FromSource): List<FromSourceAliases> {
        val aliases = mutableListOf<FromSourceAliases>()
        val visitor = object : PartiqlAst.Visitor() {
            override fun visitFromSourceScan(node: PartiqlAst.FromSource.Scan) {
                aliases.add(
                    FromSourceAliases(
                        node.asAlias
                            ?: error("FromSourceAliasVisitorTransform must be executed before SelectStarVisitorTransform"),
                        node.atAlias,
                        node.byAlias
                    )
                )
            }

            override fun visitFromSourceUnpivot(node: PartiqlAst.FromSource.Unpivot) {
                aliases.add(
                    FromSourceAliases(
                        node.asAlias
                            ?: error("FromSourceAliasVisitorTransform must be executed before SelectStarVisitorTransform"),
                        node.atAlias,
                        node.byAlias
                    )
                )
            }

            /** We do not want to recurse into the nested select query */
            override fun walkExprSelect(node: PartiqlAst.Expr.Select) {
                return
            }
        }
        visitor.walkFromSource(fromSource)
        return aliases
    }
}
