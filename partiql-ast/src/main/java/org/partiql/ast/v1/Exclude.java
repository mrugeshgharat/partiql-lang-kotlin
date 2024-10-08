package org.partiql.ast.v1;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TODO docs, equals, hashcode
 */
public class Exclude extends AstNode {
    @NotNull
    public List<ExcludePath> excludePaths;

    public Exclude(@NotNull List<ExcludePath> excludePaths) {
        this.excludePaths = excludePaths;
    }

    @NotNull
    @Override
    public Collection<AstNode> children() {
        return new ArrayList<>(excludePaths);
    }

    @Override
    public <R, C> R accept(@NotNull AstVisitor<R, C> visitor, C ctx) {
        return visitor.visitExclude(this, ctx);
    }
}
