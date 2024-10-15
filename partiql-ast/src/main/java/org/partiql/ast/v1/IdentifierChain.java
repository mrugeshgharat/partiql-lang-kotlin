package org.partiql.ast.v1;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TODO docs, equals, hashcode
 */
public class IdentifierChain extends AstNode {
    @NotNull
    public Identifier root;

    @Nullable
    public IdentifierChain next;

    public IdentifierChain(@NotNull Identifier root, @Nullable IdentifierChain next) {
        this.root = root;
        this.next = next;
    }

    @NotNull
    @Override
    public Collection<AstNode> children() {
        List<AstNode> kids = new ArrayList<>();
        kids.add(root);
        if (next != null) {
            kids.add(next);
        }
        return kids;
    }

    @Override
    public <R, C> R accept(@NotNull AstVisitor<R, C> visitor, C ctx) {
        return visitor.visitIdentifierChain(this, ctx);
    }
}