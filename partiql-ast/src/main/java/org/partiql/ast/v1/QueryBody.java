package org.partiql.ast.v1;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.partiql.ast.v1.expr.Expr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class QueryBody extends AstNode {
    public static class SFW extends QueryBody {
        @NotNull
        public Select select;

        @Nullable
        public Exclude exclude;

        @NotNull
        public From from;

        @Nullable
        public Let let;

        @Nullable
        public Expr where;

        @Nullable
        public GroupBy groupBy;

        @Nullable
        public Expr having;

        public SFW(@NotNull Select select, @Nullable Exclude exclude, @NotNull From from,
        @Nullable Let let, @Nullable Expr where, @Nullable GroupBy groupBy, @Nullable Expr having) {
        this.select = select;
        this.exclude = exclude;
        this.from = from;
        this.let = let;
        this.where = where;
        this.groupBy = groupBy;
        this.having = having;
    }

        @NotNull
        @Override
        public Collection<AstNode> children() {
            List<AstNode> kids = new ArrayList<>();
            kids.add(select);
            if (exclude != null) kids.add(exclude);
            kids.add(from);
            if (let != null) kids.add(let);
            if (where != null) kids.add(where);
            if (groupBy != null) kids.add(groupBy);
            if (having != null) kids.add(having);
            return kids;
        }

        @Override
        public <R, C> R accept(@NotNull AstVisitor<R, C> visitor, C ctx) {
            return visitor.visitQueryBodySFW(this, ctx);
        }
    }

    public static class SetOp extends QueryBody {
        @NotNull
        public org.partiql.ast.v1.SetOp type;

        public boolean isOuter;

        @NotNull
        public Expr lhs;

        @NotNull
        public Expr rhs;

        public SetOp(@NotNull org.partiql.ast.v1.SetOp type, boolean isOuter, @NotNull Expr lhs, @NotNull Expr rhs) {
        this.type = type;
        this.isOuter = isOuter;
        this.lhs = lhs;
        this.rhs = rhs;
    }

        @NotNull
        @Override
        public Collection<AstNode> children() {
            List<AstNode> kids = new ArrayList<>();
            kids.add(type);
            kids.add(lhs);
            kids.add(rhs);
            return kids;
        }

        @Override
        public <R, C> R accept(@NotNull AstVisitor<R, C> visitor, C ctx) {
            return visitor.visitQueryBodySetOp(this, ctx);
        }
    }
}
