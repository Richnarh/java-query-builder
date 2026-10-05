package com.querybuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class QueryBuilder<T> {
    private static final Logger log = LoggerFactory.getLogger(QueryBuilder.class);
    private final EntityManager entityManager;
    private final Class<T> clazz;
    private final List<FieldCondition> conditions = new ArrayList<>();
    private final List<JoinCondition> joins = new ArrayList<>();
    private final List<String> groupByFields = new ArrayList<>();
    private final List<FieldCondition> havingConditions = new ArrayList<>();
    private final Map<String, Object> extraParams = new LinkedHashMap<>();
    private final List<SelectItem> selectItems = new ArrayList<>();
    private boolean selectDistinct = false;
    private String pendingHavingLogicalOperator;

    private String orderByField;
    private String orderByDirection;
    private Integer firstResult;
    private Integer maxResults;
    private String nativeSqlQuery;
    private String mainAlias = "e";
    private String pendingLogicalOperator;
    private int paramCounter = 1;
    private boolean deleteMode = false;

    private record SelectItem(String expression, String alias) {
    }

    private final Deque<Boolean> notStack = new ArrayDeque<>();
    private boolean currentNot = false;

    public QueryBuilder(EntityManager entityManager, Class<T> clazz) {
        this.entityManager = entityManager;
        this.clazz = clazz;
        this.notStack.push(false);
    }

    public QueryBuilder<T> select(String... fields) {
        selectItems.clear();
        selectDistinct = false;
        if (fields != null) {
            for (String field : fields) {
                if (field != null && !field.isBlank()) {
                    selectItems.add(new SelectItem(normalizeSelectExpression(field.trim()), null));
                }
            }
        }
        return this;
    }

    public QueryBuilder<T> selectDistinct(String... fields) {
        select(fields);
        this.selectDistinct = true;
        return this;
    }

    public QueryBuilder<T> addSelect(String expression) {
        return addSelect(expression, null);
    }

    public QueryBuilder<T> addSelect(String expression, String alias) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Select expression cannot be blank");
        }
        selectItems.add(new SelectItem(normalizeSelectExpression(expression.trim()), alias));
        return this;
    }

    public QueryBuilder<T> and() { this.pendingLogicalOperator = "AND"; return this; }
    public QueryBuilder<T> or()  { this.pendingLogicalOperator = "OR";  return this; }
    public QueryBuilder<T> not() { this.currentNot = true; return this; }

    public QueryBuilder<T> begin() {
        conditions.add(FieldCondition.groupStart(currentNot));
        notStack.push(currentNot);
        currentNot = false;
        return this;
    }

    public QueryBuilder<T> end() {
        if (notStack.size() <= 1) throw new IllegalStateException("Mismatched .end()");
        conditions.add(FieldCondition.groupEnd());
        notStack.pop();
        currentNot = Boolean.TRUE.equals(notStack.peek());
        return this;
    }

    public QueryBuilder<T> whereIsNull(String field) {
        String logicalOp = determineLogicalOperator();
        conditions.add(new FieldCondition(null, field, " IS NULL", null, null, logicalOp, currentNot));
        resetPendingState();
        return this;
    }

    public QueryBuilder<T> whereIsNotNull(String field) {
        String logicalOp = determineLogicalOperator();
        conditions.add(new FieldCondition(null, field, " IS NOT NULL", null, null, logicalOp, currentNot));
        resetPendingState();
        return this;
    }

    public QueryBuilder<T> selectCount() {
        return addSelect("COUNT(" + mainAlias + ")", "cnt");
    }

    public QueryBuilder<T> selectCount(String field) {
        return selectCount(field, "cnt");
    }

    public QueryBuilder<T> selectCount(String field, String alias) {
        return addSelect("COUNT(" + qualify(field) + ")", alias);
    }

    public QueryBuilder<T> selectCountDistinct(String field) {
        return selectCountDistinct(field, "cnt");
    }

    public QueryBuilder<T> selectCountDistinct(String field, String alias) {
        return addSelect("COUNT(DISTINCT " + qualify(field) + ")", alias);
    }

    public QueryBuilder<T> selectSum(String field) {
        return selectSum(field, null);
    }

    public QueryBuilder<T> selectSum(String field, String alias) {
        return addSelect("SUM(" + qualify(field) + ")", alias);
    }

    public QueryBuilder<T> selectAvg(String field) {
        return selectAvg(field, null);
    }

    public QueryBuilder<T> selectAvg(String field, String alias) {
        return addSelect("AVG(" + qualify(field) + ")", alias);
    }

    public QueryBuilder<T> selectMin(String field) {
        return selectMin(field, null);
    }

    public QueryBuilder<T> selectMin(String field, String alias) {
        return addSelect("MIN(" + qualify(field) + ")", alias);
    }

    public QueryBuilder<T> selectMax(String field) {
        return selectMax(field, null);
    }

    public QueryBuilder<T> selectMax(String field, String alias) {
        return addSelect("MAX(" + qualify(field) + ")", alias);
    }

    /** Generic aggregate */
    public QueryBuilder<T> selectAggregate(String function, String field) {
        return selectAggregate(function, field, null);
    }

    public QueryBuilder<T> selectAggregate(String function, String field, String alias) {
        return addSelect(function.toUpperCase() + "(" + qualify(field) + ")", alias);
    }
    public QueryBuilder<T> andWhere(String field, Object value) { return and().where(field, value); }
    public QueryBuilder<T> andWhere(String field, String op, Object value){ return and().where(field, op, value); }
    public QueryBuilder<T> orWhere(String field, Object value) { return or().where(field, value); }
    public QueryBuilder<T> orWhere(String field, String op, Object value){ return or().where(field, op, value); }
    public QueryBuilder<T> whereLike(String field, String pattern){ return where(field, " LIKE ", pattern); }
    public QueryBuilder<T> whereNotLike(String field, String pattern) { return where(field, " NOT LIKE ", pattern); }
    public QueryBuilder<T> whereIn(String field, Collection<?> values) { return where(field, " IN ", values); }
    public QueryBuilder<T> whereNotIn(String field, Collection<?> values) { return where(field, " NOT IN ", values); }
    public QueryBuilder<T> andWhereNull(String field) { return andWhereIsNull(field); }
    public QueryBuilder<T> orWhereNull(String field) { return orWhereIsNull(field); }
    public QueryBuilder<T> andWhereNotNull(String field) { return andWhereIsNotNull(field); }
    public QueryBuilder<T> orWhereNotNull(String field) { return orWhereIsNotNull(field); }
    public QueryBuilder<T> andWhereFunction(String function, String field, String operator, Object value) { return and().whereFunction(function, field, operator, value); }
    public QueryBuilder<T> andWhereIsNull(String field) { return and().whereIsNull(field); }
    public QueryBuilder<T> orWhereIsNull(String field) {return or().whereIsNull(field);}
    public QueryBuilder<T> andWhereIsNotNull(String field) { return and().whereIsNotNull(field);}
    public QueryBuilder<T> orWhereIsNotNull(String field) { return or().whereIsNotNull(field); }
    public QueryBuilder<T> andWhereIn(String field, Collection<?> values) {
        return and().whereIn(field, values);
    }
    public QueryBuilder<T> orWhereIn(String field, Collection<?> values) {
        return or().whereIn(field, values);
    }
    public QueryBuilder<T> andWhereNotIn(String field, Collection<?> values) {
        return and().whereNotIn(field, values);
    }
    public QueryBuilder<T> orWhereNotIn(String field, Collection<?> values) {
        return or().whereNotIn(field, values);
    }
    public QueryBuilder<T> andWhereLike(String field, String pattern) {
        return and().whereLike(field, pattern);
    }
    public QueryBuilder<T> orWhereLike(String field, String pattern) {
        return or().whereLike(field, pattern);
    }
    public QueryBuilder<T> andWhereNotLike(String field, String pattern) {
        return and().whereNotLike(field, pattern);
    }
    public QueryBuilder<T> orWhereNotLike(String field, String pattern) {
        return or().whereNotLike(field, pattern);
    }
    public QueryBuilder<T> andWhereLikeIgnoreCase(String field, String pattern) {
        return and().whereFunction("LOWER", field, " LIKE ", "%" + pattern.toLowerCase().trim() + "%");
    }
    public QueryBuilder<T> orWhereLikeIgnoreCase(String field, String pattern) {
        return or().whereFunction("LOWER", field, " LIKE ", "%" + pattern.toLowerCase().trim() + "%");
    }
    public QueryBuilder<T> orWhereFunction(String function, String field, String operator, Object value) {
        return or().whereFunction(function, field, operator, value);
    }
    public QueryBuilder<T> andWhereFunction(String function, String field, Object value) {
        return and().whereFunction(function, field, " = ", value);
    }
    public QueryBuilder<T> orWhereFunction(String function, String field, Object value) {
        return or().whereFunction(function, field, " = ", value);
    }
    public QueryBuilder<T> whereIgnoreCase(String field, String value) {
        if (value == null) return whereIsNull(field);
        return whereFunction("LOWER", field, "=", value.toLowerCase());
    }
    public QueryBuilder<T> andWhereIgnoreCase(String field, String value) {
        return and().whereIgnoreCase(field, value);
    }
    public QueryBuilder<T> orWhereIgnoreCase(String field, String value) {
        return or().whereIgnoreCase(field, value);
    }
    public QueryBuilder<T> whereTrim(String field, String value) {
        if (value == null) return whereIsNull(field);
        return whereFunction("TRIM", field, "=", value.trim());
    }
    public QueryBuilder<T> andWhereTrim(String field, String value) {
        return and().whereTrim(field, value);
    }
    public QueryBuilder<T> whereTrimIgnoreCase(String field, String value) {
        if (value == null) return whereIsNull(field);
        return whereFunction("LOWER(TRIM", field, ")", value.trim().toLowerCase());
    }
    public QueryBuilder<T> andWhereTrimIgnoreCase(String field, String value) {
        return and().whereTrimIgnoreCase(field, value);
    }
    public QueryBuilder<T> orWhereTrimIgnoreCase(String field, String value) {
        return or().whereTrimIgnoreCase(field, value);
    }
    public QueryBuilder<T> where(String field, Object value) {
        return where(field, " = ", value);
    }
    public QueryBuilder<T> andWhereBetween(String field, Object start, Object end) {
        return and().whereBetween(field, start, end);
    }
    public QueryBuilder<T> orWhereBetween(String field, Object start, Object end) {
        return or().whereBetween(field, start, end);
    }
    public QueryBuilder<T> andWhereBetweenInclusiveStart(String field, Object start, Object endExclusive) {
        return and().where(field, ">=", start)
                .andWhere(field, "<", endExclusive);
    }
    public QueryBuilder<T> andWhereDateBetween(String field, java.time.LocalDate start, java.time.LocalDate end) {
        return andWhereBetween(field, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }
    public QueryBuilder<T> whereBetween(String field, Object start, Object end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("BETWEEN requires non-null start and end values");
        }

        String logicalOp = determineLogicalOperator();

        String paramStart = "param" + paramCounter++;
        conditions.add(new FieldCondition(null, field, " >= ", paramStart, start, logicalOp, currentNot));

        String paramEnd = "param" + paramCounter++;
        conditions.add(new FieldCondition(null, field, " <= ", paramEnd, end, "AND", false));

        resetPendingState();
        return this;
    }
    public QueryBuilder<T> where(String field, String operator, Object value) {
        String logicalOp = determineLogicalOperator();
        String paramName = "param" + paramCounter++;

        conditions.add(new FieldCondition(
                null, field, operator, paramName, value, logicalOp, currentNot
        ));

        resetPendingState();
        return this;
    }
    public QueryBuilder<T> whereFunction(String function, String field, String operator, Object value) {
        String logicalOp = determineLogicalOperator();
        String paramName = "param" + paramCounter++;

        conditions.add(new FieldCondition(
                function, field, operator, paramName, value, logicalOp, currentNot
        ));

        resetPendingState();
        return this;
    }

    public int delete() {
        this.deleteMode = true;
        try {
            return done();
        } finally {
            this.deleteMode = false;
        }
    }

    private int done() {
        if (conditions.isEmpty() && nativeSqlQuery == null) {
            throw new IllegalStateException("Refusing to delete without conditions");
        }
        if (!joins.isEmpty()) {
            throw new IllegalStateException(
                    "DELETE does not support joins. Remove joins or rewrite the condition with a subquery.");
        }

        Query query;
        if (nativeSqlQuery != null) {
            query = entityManager.createNativeQuery(nativeSqlQuery);
        } else {
            query = buildDeleteQuery();
        }
        setParameters(query);
        return query.executeUpdate();
    }

    public int deleteAll() {
        String jpql = "DELETE FROM " + clazz.getSimpleName() + " " + mainAlias;
        Query query = entityManager.createQuery(jpql);
        return query.executeUpdate();
    }

    public QueryBuilder<T> withAlias(String alias) { this.mainAlias = alias; return this; }
    public QueryBuilder<T> join(String field, String alias) { return join(field, alias, "INNER"); }
    public QueryBuilder<T> join(String field, String alias, String type){ joins.add(new JoinCondition(field, alias, type.toUpperCase())); return this; }
    public QueryBuilder<T> orderByAsc(String field) { this.orderByField = field; this.orderByDirection = "ASC"; return this; }
    public QueryBuilder<T> orderByDesc(String field) { this.orderByField = field; this.orderByDirection = "DESC"; return this; }
    public QueryBuilder<T> setFirstResult(int value) { this.firstResult = value >= 0 ? value : null; return this; }
    public QueryBuilder<T> setMaxResults(int value) { this.maxResults = value > 0 ? value : null; return this; }
    public QueryBuilder<T> limit(int maxResults) { return setMaxResults(maxResults); }
    public QueryBuilder<T> limit(int offset, int maxResults) { setFirstResult(offset);setMaxResults(maxResults); return this;}
    public QueryBuilder<T> offset(int value) { return setFirstResult(value);}
    public QueryBuilder<T> nativeQuery(String sql) { this.nativeSqlQuery = sql; return this; }
    public QueryBuilder<T> groupBy(String... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("groupBy requires at least one field");
        }
        for (String field : fields) {
            if (field != null && !field.isBlank()) {
                groupByFields.add(field.trim());
            }
        }
        return this;
    }

    public QueryBuilder<T> having(String field, Object value) {
        return having(field, " = ", value);
    }

    public QueryBuilder<T> having(String field, String operator, Object value) {
        String logicalOp = determineHavingLogicalOperator();
        String paramName = "param" + paramCounter++;

        havingConditions.add(new FieldCondition(null, field, operator, paramName, value, logicalOp, false));
        pendingHavingLogicalOperator = null;
        return this;
    }

    public QueryBuilder<T> havingFunction(String function, String field, String operator, Object value) {
        String logicalOp = determineHavingLogicalOperator();
        String paramName = "param" + paramCounter++;

        havingConditions.add(new FieldCondition(function, field, operator, paramName, value, logicalOp, false));
        pendingHavingLogicalOperator = null;
        return this;
    }

    public QueryBuilder<T> havingIsNull(String field) {
        String logicalOp = determineHavingLogicalOperator();
        havingConditions.add(new FieldCondition(null, field, " IS NULL", null, null, logicalOp, false));
        pendingHavingLogicalOperator = null;
        return this;
    }

    public QueryBuilder<T> havingIsNotNull(String field) {
        String logicalOp = determineHavingLogicalOperator();
        havingConditions.add(new FieldCondition(null, field, " IS NOT NULL", null, null, logicalOp, false));
        pendingHavingLogicalOperator = null;
        return this;
    }

    public QueryBuilder<T> andHaving(String field, Object value) {
        pendingHavingLogicalOperator = "AND";
        return having(field, value);
    }

    public QueryBuilder<T> andHaving(String field, String operator, Object value) {
        pendingHavingLogicalOperator = "AND";
        return having(field, operator, value);
    }

    public QueryBuilder<T> orHaving(String field, Object value) {
        pendingHavingLogicalOperator = "OR";
        return having(field, value);
    }

    public QueryBuilder<T> orHaving(String field, String operator, Object value) {
        pendingHavingLogicalOperator = "OR";
        return having(field, operator, value);
    }

    public QueryBuilder<T> andHavingFunction(String function, String field, String operator, Object value) {
        pendingHavingLogicalOperator = "AND";
        return havingFunction(function, field, operator, value);
    }

    public QueryBuilder<T> orHavingFunction(String function, String field, String operator, Object value) {
        pendingHavingLogicalOperator = "OR";
        return havingFunction(function, field, operator, value);
    }

    public QueryBuilder<T> andHavingIsNull(String field) {
        pendingHavingLogicalOperator = "AND";
        return havingIsNull(field);
    }

    public QueryBuilder<T> orHavingIsNull(String field) {
        pendingHavingLogicalOperator = "OR";
        return havingIsNull(field);
    }

    public QueryBuilder<T> andHavingIsNotNull(String field) {
        pendingHavingLogicalOperator = "AND";
        return havingIsNotNull(field);
    }

    public QueryBuilder<T> orHavingIsNotNull(String field) {
        pendingHavingLogicalOperator = "OR";
        return havingIsNotNull(field);
    }

    public QueryBuilder<T> nativeSql(String sql) {
        this.nativeSqlQuery = sql;
        return this;
    }

    public QueryBuilder<T> param(String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be null or blank");
        }
        String cleanName = name.startsWith(":") ? name.substring(1) : name;
        extraParams.put(cleanName, value);
        return this;
    }

    public QueryBuilder<T> params(Map<String, Object> parameters) {
        if (parameters != null) {
            parameters.forEach(this::param);
        }
        return this;
    }

    public QueryBuilder<T> clearParams() {
        extraParams.clear();
        return this;
    }

    // these are mainly for native SQL parameter binding.
    public QueryBuilder<T> paramIn(String name, Collection<?> values) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be null or blank");
        }
        if (values == null) {
            throw new IllegalArgumentException("Collection for IN clause cannot be null");
        }
        String cleanName = name.startsWith(":") ? name.substring(1) : name;

        // Empty collections are dangerous in SQL (IN () is invalid)
        if (values.isEmpty()) {
            // Bind a value that will never match instead of generating invalid SQL
            extraParams.put(cleanName, List.of("__EMPTY_IN_CLAUSE__"));
        } else {
            extraParams.put(cleanName, values);
        }
        return this;
    }

    public QueryBuilder<T> paramIn(String name, Object... values) {
        if (values == null || values.length == 0) {
            return paramIn(name, List.of());
        }
        return paramIn(name, Arrays.asList(values));
    }

    public QueryBuilder<T> paramNotIn(String name, Collection<?> values) {
        return paramIn(name, values);
    }

    public QueryBuilder<T> paramNotIn(String name, Object... values) {
        return paramIn(name, values);
    }

    @SuppressWarnings("unchecked")
    public List<T> list() {
        if (deleteMode) throw new IllegalStateException("Cannot call .list() in delete mode");
        Query query = nativeSqlQuery != null ? buildNativeQuery() : buildQuery();
        applyPagination(query);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public T execute() {
        if (deleteMode) throw new IllegalStateException("Cannot call .execute() in delete mode");
        Query query = nativeSqlQuery != null ? buildNativeQuery() : buildQuery();
        applyPagination(query);
        return (T) query.getResultList().stream().findFirst().orElse(null);
    }

    public long count() {
        if (deleteMode) throw new IllegalStateException("Cannot call .count() in delete mode");
        Query query = nativeSqlQuery != null ? buildNativeCountQuery() : buildCountQuery();
        return (long) query.getSingleResult();
    }

    public QueryBuilder<T> printQry() {
        if (nativeSqlQuery != null) {
            log.debug("=== NATIVE SQL ===");
            log.debug(nativeSqlQuery);
        } else {
            String type = deleteMode ? "DELETE" : "SELECT";
            StringBuilder jpql = new StringBuilder();

            if (deleteMode) {
                jpql.append("DELETE FROM ");
            } else {
                jpql.append("SELECT ");
                if (selectDistinct) jpql.append("DISTINCT ");

                if (!selectItems.isEmpty()) {
                    for (int i = 0; i < selectItems.size(); i++) {
                        if (i > 0) jpql.append(", ");
                        SelectItem item = selectItems.get(i);
                        jpql.append(item.expression);
                        if (item.alias != null && !item.alias.isBlank()) {
                            jpql.append(" AS ").append(item.alias);
                        }
                    }
                } else {
                    jpql.append(mainAlias);
                }
                jpql.append(" FROM ");
            }

            jpql.append(clazz.getSimpleName()).append(" ").append(mainAlias);
            if (!deleteMode) {
                appendJoins(jpql);
            }
            appendWhereClause(jpql);

            if (!deleteMode) {
                appendGroupBy(jpql);
                appendHavingClause(jpql);
            }

            if (!deleteMode && orderByField != null) {
                jpql.append(" ORDER BY ");
                if (orderByField.contains("(") || orderByField.contains(".")) {
                    jpql.append(orderByField);
                } else {
                    jpql.append(mainAlias).append(".").append(orderByField);
                }
                jpql.append(" ").append(orderByDirection);
            }

            log.debug("=== JPQL {}", type + " ===");
            log.debug(jpql.toString());
        }

        log.debug("=== PARAMETERS ===");
        boolean hasParams = false;

        for (FieldCondition c : conditions) {
            if (c.getParamName() != null) {
                log.debug("  : {}", c.getParamName() + " = " + c.getFieldValue());
                hasParams = true;
            }
        }
        for (FieldCondition c : havingConditions) {
            if (c.getParamName() != null) {
                log.debug("  : {}", c.getParamName() + " = " + c.getFieldValue());
                hasParams = true;
            }
        }

        for (Map.Entry<String, Object> entry : extraParams.entrySet()) {
            log.debug("  : {}", entry.getKey() + " = " + entry.getValue());
            hasParams = true;
        }

        if (!hasParams) {
            log.debug("  (none)");
        }
        log.debug("===================");

        return this;
    }

    private Query buildQuery() {
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT ");
        if (selectDistinct) jpql.append("DISTINCT ");

        if (!selectItems.isEmpty()) {
            for (int i = 0; i < selectItems.size(); i++) {
                if (i > 0) jpql.append(", ");
                SelectItem item = selectItems.get(i);
                jpql.append(item.expression);
                if (item.alias != null && !item.alias.isBlank()) {
                    jpql.append(" AS ").append(item.alias);
                }
            }
        } else {
            jpql.append(mainAlias);
        }

        jpql.append(" FROM ").append(clazz.getSimpleName()).append(" ").append(mainAlias);
        appendJoins(jpql);
        appendWhereClause(jpql);
        appendGroupBy(jpql);
        appendHavingClause(jpql);
        appendOrderBy(jpql);

        Query query = selectItems.isEmpty()
                ? entityManager.createQuery(jpql.toString(), clazz)
                : entityManager.createQuery(jpql.toString());

        setParameters(query);
        return query;
    }

    private Query buildCountQuery() {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(").append(mainAlias)
                .append(") FROM ").append(clazz.getSimpleName()).append(" ").append(mainAlias);
        appendJoins(jpql);
        appendWhereClause(jpql);
        Query query = entityManager.createQuery(jpql.toString());
        setParameters(query);
        return query;
    }

    private Query buildDeleteQuery() {
        StringBuilder jpql = new StringBuilder("DELETE FROM ")
                .append(clazz.getSimpleName()).append(" ").append(mainAlias);

        StringBuilder where = new StringBuilder();
        appendWhereClause(where);

        if (where.isEmpty()) {
            throw new IllegalStateException("DELETE must have a WHERE clause");
        }
        jpql.append(where);

        return entityManager.createQuery(jpql.toString());
    }

    private Query buildNativeQuery() {
        Query query = entityManager.createNativeQuery(nativeSqlQuery);
        setParameters(query);
        return query;
    }

    private Query buildNativeCountQuery() {
        String sql = "SELECT COUNT(*) FROM (" + nativeSqlQuery + ") tmp";
        Query query = entityManager.createNativeQuery(sql);
        setParameters(query);
        return query;
    }

    private void appendJoins(StringBuilder sb) {
        joins.forEach(j -> sb.append(" ")
                .append(j.joinType()).append(" JOIN ")
                .append(mainAlias).append(".").append(j.fieldName())
                .append(" ").append(j.alias()));
    }

    private void appendWhereClause(StringBuilder sb) {
        if (conditions.isEmpty()) return;
        sb.append(" WHERE ");
        boolean expectOp = false;

        for (FieldCondition c : conditions) {
            if (c.isGroupStart()) {
                if (expectOp) sb.append(" ").append(getLastLogicalOperator()).append(" ");
                if (c.isGroupNegated()) sb.append("NOT ");
                sb.append("(");
                expectOp = false;
                continue;
            }
            if (c.isGroupEnd()) {
                sb.append(")");
                expectOp = true;
                continue;
            }

            if (expectOp) {
                String op = c.getLogicalOperator() != null ? c.getLogicalOperator() : "AND";
                sb.append(" ").append(op).append(" ");
            }

            String condition = getCondition(c);

            sb.append(condition);
            expectOp = true;
        }
    }

    private String getCondition(FieldCondition c) {
        String expr = qualify(c.getFieldName());

        if (c.getFunction() != null) {
            expr = c.getFunction().toUpperCase() + "(" + expr + ")";
        }

        String operator = c.getComparisonOperator();
        String condition;

        if (c.getParamName() == null) {
            condition = expr + operator;
        } else {
            String paramRef = ":" + c.getParamName();
            if (c.isInClause()) {
                paramRef = "(" + paramRef + ")";
            }
            condition = expr + operator + paramRef;
        }

        if (c.isNegated()) {
            String op = operator == null ? "" : operator.trim();

            if ("IS NULL".equalsIgnoreCase(op)) {
                condition = expr + " IS NOT NULL";
            } else if ("IS NOT NULL".equalsIgnoreCase(op)) {
                condition = expr + " IS NULL";
            } else if ("IN".equalsIgnoreCase(op)) {
                condition = expr + " NOT IN " + (c.getParamName() != null ? "(:" + c.getParamName() + ")" : "");
            } else if ("NOT IN".equalsIgnoreCase(op)) {
                condition = expr + " IN " + (c.getParamName() != null ? "(:" + c.getParamName() + ")" : "");
            } else {
                condition = "NOT (" + condition + ")";
            }
        }
        return condition;
    }

    private void appendOrderBy(StringBuilder sb) {
        if (orderByField != null && orderByDirection != null) {
            sb.append(" ORDER BY ").append(mainAlias).append(".").append(orderByField)
                    .append(" ").append(orderByDirection);
        }
    }
    private void appendGroupBy(StringBuilder sb) {
        if (groupByFields.isEmpty()) return;
        sb.append(" GROUP BY ");
        for (int i = 0; i < groupByFields.size(); i++) {
            if (i > 0) sb.append(", ");
            String field = groupByFields.get(i);
            if (field.contains(".") || field.contains("(")) {
                sb.append(field);
            } else {
                sb.append(mainAlias).append(".").append(field);
            }
        }
    }

    private void appendHavingClause(StringBuilder sb) {
        if (havingConditions.isEmpty()) return;
        sb.append(" HAVING ");
        boolean expectOp = false;

        for (FieldCondition c : havingConditions) {
            if (expectOp) {
                String op = c.getLogicalOperator() != null ? c.getLogicalOperator() : "AND";
                sb.append(" ").append(op).append(" ");
            }
            sb.append(getCondition(c));
            expectOp = true;
        }
    }

    private void setParameters(Query query) {
        for (FieldCondition c : conditions) {
            if (c.getParamName() != null) {
                query.setParameter(c.getParamName(), c.getFieldValue());
            }
        }
        for (FieldCondition c : havingConditions) {
            if (c.getParamName() != null) {
                query.setParameter(c.getParamName(), c.getFieldValue());
            }
        }

        for (Map.Entry<String, Object> entry : extraParams.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private void applyPagination(Query query) {
        if (firstResult != null) query.setFirstResult(firstResult);
        if (maxResults != null) query.setMaxResults(maxResults);
    }

    private String determineLogicalOperator() {
        if (conditions.isEmpty() || isLastConditionGroupMarker()) return null;
        return pendingLogicalOperator != null ? pendingLogicalOperator : "AND";
    }

    private String determineHavingLogicalOperator() {
        if (havingConditions.isEmpty()) return null;
        return pendingHavingLogicalOperator != null ? pendingHavingLogicalOperator : "AND";
    }

    private String qualify(String field) {
        if (field == null) return null;
        field = field.trim();

        if (field.startsWith(mainAlias + ".") || field.contains("(") || field.contains(" ")) {
            return field;
        }

        int dot = field.indexOf('.');
        if (dot > 0) {
            String possibleAlias = field.substring(0, dot);
            boolean isJoinAlias = joins.stream()
                    .anyMatch(j -> j.alias().equals(possibleAlias));
            if (isJoinAlias) {
                return field;
            }
        }
        return mainAlias + "." + field;
    }

    private String normalizeSelectExpression(String expr) {
        if (expr.contains("(") || expr.contains(".")) {
            return expr;
        }
        return mainAlias + "." + expr;
    }
    private boolean isLastConditionGroupMarker() {
        if (conditions.isEmpty()) return true;
        FieldCondition last = conditions.getLast();
        return last.isGroupStart() || last.isGroupEnd();
    }

    private String getLastLogicalOperator() {
        for (int i = conditions.size() - 1; i >= 0; i--) {
            FieldCondition c = conditions.get(i);
            if (!c.isGroupStart() && !c.isGroupEnd() && c.getLogicalOperator() != null) {
                return c.getLogicalOperator();
            }
        }
        return "AND";
    }

    private void resetPendingState() {
        pendingLogicalOperator = null;
        currentNot = false;
    }
}