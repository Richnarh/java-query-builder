package com.querybuilder;

public record JoinCondition(String fieldName, String alias, String joinType) {
    public JoinCondition(String fieldName, String alias, String joinType) {
        this.fieldName = fieldName;
        this.alias = alias;
        this.joinType = joinType != null ? joinType.toUpperCase() : "INNER";
    }
}
