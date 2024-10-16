package org.partiql.ast.v1;

import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TODO docs, equals, hashcode
 */
@Builder(builderClassName = "Builder")
public class Exclude extends AstNode {
    @NotNull
    public final List<ExcludePath> excludePaths;

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
