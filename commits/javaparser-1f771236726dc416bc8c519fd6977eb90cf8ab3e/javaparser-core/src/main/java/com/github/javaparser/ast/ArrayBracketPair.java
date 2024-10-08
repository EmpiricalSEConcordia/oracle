package com.github.javaparser.ast;

import com.github.javaparser.Range;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.observing.ObservableProperty;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.util.Arrays;
import java.util.List;

import static com.github.javaparser.utils.Utils.assertNotNull;

/**
 * In, for example, <code>int[] a[];</code> there are two ArrayBracketPair objects,
 * one for the [] after int, one for the [] after a.
 */
public class ArrayBracketPair extends Node implements NodeWithAnnotations<ArrayBracketPair> {
    private NodeList<AnnotationExpr> annotations = new NodeList<>();

    public ArrayBracketPair() {
        this(null, new NodeList<>());
    }

    public ArrayBracketPair(NodeList<AnnotationExpr> annotations) {
        this(null, annotations);
    }

    public ArrayBracketPair(Range range, NodeList<AnnotationExpr> annotations) {
        super(range);
        setAnnotations(annotations);
    }

    @Override public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override public <A> void accept(final VoidVisitor<A> v, final A arg) {
		v.visit(this, arg);
    }

    public NodeList<AnnotationExpr> getAnnotations() {
        return annotations;
    }

    public ArrayBracketPair setAnnotations(NodeList<AnnotationExpr> annotations) {
        notifyPropertyChange(ObservableProperty.ANNOTATIONS, this.annotations, annotations);
        setAsParentNodeOf(annotations);
        this.annotations = assertNotNull(annotations);
        return this;
    }

    @Override
    public List<NodeList<?>> getNodeLists() {
        return Arrays.asList(annotations);
    }
}
