package edu.utdallas.seers.lasso.ast;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

/**
 * Used where a {@link TypeSolver} is required by the JavaParser API, but type resolution beyond
 * the currently parsed compilation unit is not necessary.
 */
class DummyTypeSolver implements TypeSolver {
    @Override
    public TypeSolver getParent() {
        return null;
    }

    @Override
    public void setParent(TypeSolver parent) {
        throw new IllegalStateException("Not supported for intra-compilation-unit resolution");
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        throw new IllegalStateException("Not supported for intra-compilation-unit resolution");
    }
}
