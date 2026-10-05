package com.querybuilder;

public class FieldCondition {
    private final String function;
    private final String fieldName;
    private final String comparisonOperator;
    private final String paramName;
    private final Object fieldValue;
    private final String logicalOperator;
    private final boolean negated;
    private final boolean groupStart;
    private final boolean groupEnd;
    private final boolean groupNegated;

    public FieldCondition(String function, String fieldName, String comparisonOperator, String paramName, Object fieldValue, String logicalOperator, boolean negated) {
        this.function = function;
        this.fieldName = fieldName;
        this.comparisonOperator = comparisonOperator;
        this.paramName = paramName;
        this.fieldValue = fieldValue;
        this.logicalOperator = logicalOperator;
        this.negated = negated;
        this.groupStart = false;
        this.groupEnd = false;
        this.groupNegated = false;
    }

    private FieldCondition(boolean groupStart, boolean groupNegated) {
        this.function = null;
        this.fieldName = null;
        this.comparisonOperator = null;
        this.paramName = null;
        this.fieldValue = null;
        this.logicalOperator = null;
        this.negated = false;
        this.groupStart = groupStart;
        this.groupEnd = !groupStart;
        this.groupNegated = groupNegated;
    }

    public static FieldCondition groupStart(boolean negated) {
        return new FieldCondition(true, negated);
    }

    public static FieldCondition groupEnd() {
        return new FieldCondition(false, false);
    }

    public String getFunction() { return function; }
    public String getFieldName() { return fieldName; }
    public String getComparisonOperator() { return comparisonOperator; }
    public String getParamName() { return paramName; }
    public Object getFieldValue() { return fieldValue; }
    public String getLogicalOperator() { return logicalOperator; }
    public boolean isNegated() { return negated; }

    public boolean isGroupStart() { return groupStart; }
    public boolean isGroupEnd() { return groupEnd; }
    public boolean isGroupNegated() { return groupNegated; }

    public boolean isInClause() {
        if (comparisonOperator == null) return false;
        String op = comparisonOperator.toUpperCase();
        return " IN ".equals(op) || " NOT IN ".equals(op);
    }
}