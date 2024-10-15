package org.partiql.ast.v1;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * TODO docs, equals, hashcode
 */
public class SelectStar extends Select {
    @Nullable
    public SetQuantifier setq;

    public SelectStar(@Nullable SetQuantifier setq) {
        this.setq = setq;
    }

    @NotNull
    @Override
    public Collection<AstNode> children() {
        return Collections.emptyList();
    }

    @Override
    public <R, C> R accept(@NotNull AstVisitor<R, C> visitor, C ctx) {
        return visitor.visitSelectStar(this, ctx);
    }
}