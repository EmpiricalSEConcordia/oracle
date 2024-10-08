package com.github.javaparser.printer.lexicalpreservation.changes;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.printer.concretesyntaxmodel.CsmConditional;

import java.util.Optional;

public class PropertyChange implements Change {
    private ObservableProperty property;
    private Object oldValue;
    private Object newValue;

    public ObservableProperty getProperty() {
        return property;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public PropertyChange(ObservableProperty property, Object oldValue, Object newValue) {
        this.property = property;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @Override
    public boolean evaluate(CsmConditional csmConditional, Node node) {
        switch (csmConditional.getCondition()) {
            case FLAG:
                if (csmConditional.getProperty() == property) {
                    return (Boolean) newValue;
                }
                return (Boolean) csmConditional.getProperty().singleValueFor(node);
            case IS_NOT_EMPTY:
                if (csmConditional.getProperty() == property) {
                    return newValue != null && !((NodeList) newValue).isEmpty();
                }
                return !csmConditional.getProperty().isNullOrEmpty(node);
            case IS_PRESENT:
                if (csmConditional.getProperty() == property) {
                    if (newValue == null) {
                        return false;
                    }
                    if (newValue instanceof Optional) {
                        return ((Optional)newValue).isPresent();
                    }
                    return true;
                }
                return !csmConditional.getProperty().isNullOrEmpty(node);
            default:
                throw new UnsupportedOperationException("" + csmConditional.getProperty() + " " + csmConditional.getCondition());
        }
    }

    @Override
    public Object getValue(ObservableProperty property, Node node) {
        if (property == this.property) {
            return newValue;
        } else {
            return property.getValue(node);
        }
    }
}
