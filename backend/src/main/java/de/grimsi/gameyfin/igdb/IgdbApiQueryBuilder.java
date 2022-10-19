package de.grimsi.gameyfin.igdb;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builder for fluent building of igdb api queries.
 */
public class IgdbApiQueryBuilder {
    private final StringBuilder stringBuilder;
    private String search;
    private String fields;
    private String limit;
    private String where;
    private String sort;

    public IgdbApiQueryBuilder() {
        this.stringBuilder = new StringBuilder();
        this.fields = "fields *;";
        this.search = "";
        this.limit = "";
        this.where = "";
        this.sort = "";
    }

    /**
     * Creates a {@link Condition} that are concatenated through the `&` operator.
     * This condition produces `(condition & condition & condition)`.
     *
     * @param conditions multiple conditions
     * @return an {@link AndCondition}
     */
    public static Condition and(Condition... conditions) {
        return new AndCondition(conditions);
    }

    /**
     * Creates a {@link Condition} that are concatenated through the `|` operator.
     * This condition produces `(condition | condition | condition)`.
     *
     * @param conditions multiple conditions
     * @return an {@link OrCondition}
     */
    public static Condition or(Condition... conditions) {
        return new OrCondition(conditions);
    }

    /**
     * Creates a {@link Condition} to look for string values in a list.
     * This condition produces `field = ("val1","val2")`.
     *
     * @param field  a field to search through
     * @param values the string values
     * @return an {@link InCondition}
     */
    public static Condition in(String field, String... values) {
        return new InCondition(field, values);
    }

    /**
     * Creates a {@link Condition} to look for number values in a list.
     * This condition produces `field = (1,2,3)`.
     *
     * @param field  a field to search through
     * @param values the number values
     * @return an {@link InCondition}
     */
    public static Condition in(String field, Number... values) {
        return new InCondition(field, values);
    }

    /**
     * Creates a {@link Condition} to look for values in a collection.
     * This condition produces `field = ("val1","val2")` if a collection of strings is passed.
     * This condition produces `field = (1,2,3)` if a collection of numbers is passed.
     *
     * @param field  a field to search through.
     * @param values a collection of values.
     * @return an {@link InCondition}.
     */
    public static Condition in(String field, Collection<?> values) {
        return new InCondition(field, values.toArray(new Object[0]));
    }

    /**
     * Creates a {@link Condition} to filter for matching string values.
     * This condition produces `field = "value"`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition equal(String field, String value) {
        return new EqualsCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to filter for matching number values.
     * This condition produces `field = 123`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition equal(String field, Number value) {
        return new EqualsCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to filter for non-matching string values.
     * This condition produces `field != "value"`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition not(String field, String value) {
        return new NotCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to filter for non-matching number values.
     * This condition produces `field != 123`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition not(String field, Number value) {
        return new NotCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to check if a value is bigger than the given value.
     * This condition produces `field > 123`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition greater(String field, Number value) {
        return new GreaterThanCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to check if a value is bigger or equal to the given value.
     * This condition produces `field >= 123`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition greaterEquals(String field, Number value) {
        return new GreaterEqualsCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to check if a value is smaller than the given value.
     * This condition produces `field < 123`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition lesser(String field, Number value) {
        return new LessThanCondition(field, value);
    }

    /**
     * Creates a {@link Condition} to check if a value is smaller or equal to the given value.
     * This condition produces `field <= 123`.
     *
     * @param field a field to search through.
     * @param value a value for comparison.
     * @return an {@link InCondition}.
     */
    public static Condition lesserEquals(String field, Number value) {
        return new LesserEqualsCondition(field, value);
    }

    /**
     * Builds the query string.
     *
     * @return an igdb compatible query
     */
    public String build() {
        stringBuilder.append(search);
        stringBuilder.append(fields);
        stringBuilder.append(limit);
        stringBuilder.append(where);
        stringBuilder.append(sort);
        return stringBuilder.toString();
    }

    /**
     * Adds the `search "xyz";` query param.
     *
     * @param searchTerm a term to search for.
     * @return the builder
     */
    public IgdbApiQueryBuilder search(String searchTerm) {
        this.search = "search \"%s\";".formatted(searchTerm);
        return this;
    }

    /**
     * Adds the `fields abc,xyz;` query param.
     *
     * @param fields fields that should be returned (defaults to *).
     * @return the builder
     */
    public IgdbApiQueryBuilder fields(String fields) {
        this.fields = "fields %s;".formatted(fields);
        return this;
    }

    /**
     * Adds the `limit 1234;` query param.
     *
     * @param limit how many results should be returned.
     * @return the builder
     */
    public IgdbApiQueryBuilder limit(int limit) {
        this.limit = "limit %d;".formatted(limit);
        return this;
    }

    /**
     * Adds the `where xyz;` query param.
     *
     * @param condition a {@link Condition} object containing all conditions to filter the igdb db.
     * @return the builder
     */
    public IgdbApiQueryBuilder where(Condition condition) {
        this.where = "where %s;".formatted(condition.build());
        return this;
    }

    /**
     * Adds the `sort xyz asc;` query param.
     *
     * @param field a term to search for.
     * @param order the {@link SortOrder} (either ASC or DESC).
     * @return the builder
     */
    public IgdbApiQueryBuilder sort(String field, SortOrder order) {
        this.sort = "sort %s %s;".formatted(field, order.value);
        return this;
    }

    /**
     * Sort order enum for sorting query result.
     */
    public enum SortOrder {
        ASC("asc"), DESC("desc");

        public final String value;

        SortOrder(String value) {
            this.value = value;
        }
    }

    /**
     * Abstract condition object.
     */
    public abstract static class Condition {
        protected static String wrap(String conditions) {
            return "(%s)".formatted(conditions);
        }

        public abstract String build();
    }

    /**
     * InCondition
     */
    public static class InCondition extends Condition {
        private static final String PATTERN = "%s = (%s)";
        private final String field;
        private final String in;

        public InCondition(String field, Object[] values) {
            this.field = field;
            if (Arrays.stream(values).anyMatch(String.class::isInstance))
                this.in = Arrays.stream(values).map("\"%s\""::formatted).collect(Collectors.joining(","));
            else if (Arrays.stream(values).anyMatch(Number.class::isInstance))
                this.in = Arrays.stream(values).map("%d"::formatted).collect(Collectors.joining(","));
            else this.in = null;
        }

        @Override
        public String build() {
            return PATTERN.formatted(field, in);
        }
    }

    /**
     * NotCondition
     */
    public static class NotCondition extends OperatorCondition {
        private static final String OPERATOR = "!=";

        public NotCondition(String field, String value) {
            super(field, OPERATOR, value);
        }

        public NotCondition(String field, Number value) {
            super(field, OPERATOR, value);
        }
    }

    /**
     * EqualsCondition
     */
    public static class EqualsCondition extends OperatorCondition {

        private static final String OPERATOR = "=";

        public EqualsCondition(String field, String value) {
            super(field, OPERATOR, value);
        }

        public EqualsCondition(String field, Number value) {
            super(field, OPERATOR, value);
        }
    }

    /**
     * GreaterThanCondition
     */
    public static class GreaterThanCondition extends OperatorCondition {

        private static final String OPERATOR = ">";

        public GreaterThanCondition(String field, Number value) {
            super(field, OPERATOR, value);
        }
    }

    /**
     * GreaterEqualsCondition
     */
    public static class GreaterEqualsCondition extends OperatorCondition {

        private static final String OPERATOR = ">=";

        public GreaterEqualsCondition(String field, Number value) {
            super(field, OPERATOR, value);
        }
    }

    /**
     * LessThanCondition
     */
    public static class LessThanCondition extends OperatorCondition {

        private static final String OPERATOR = "<";

        public LessThanCondition(String field, Number value) {
            super(field, OPERATOR, value);
        }
    }

    /**
     * LesserEqualsCondition
     */
    public static class LesserEqualsCondition extends OperatorCondition {

        private static final String OPERATOR = "<=";

        public LesserEqualsCondition(String field, Number value) {
            super(field, OPERATOR, value);
        }
    }

    /**
     * OperatorCondition for inheritance
     */
    public static class OperatorCondition extends Condition {
        private static final String PATTERN = "%s %s %s";
        private static final String ESCAPED_STRING = "\"%s\"";
        private static final String DIGITS = "%s";

        private final String field;
        private final String value;
        private final String operator;

        public OperatorCondition(String field, String operator, String value) {
            this.field = field;
            this.operator = operator;
            this.value = value != null ? ESCAPED_STRING.formatted(value) : null;
        }

        public OperatorCondition(String field, String operator, Number value) {
            this.field = field;
            this.operator = operator;
            this.value = value != null ? DIGITS.formatted(value) : null;
        }

        public OperatorCondition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String build() {
            return PATTERN.formatted(field, operator, value);
        }
    }

    /**
     * AndCondition
     */
    public static class AndCondition extends Condition {
        private static final String AND = " & ";

        private final Condition[] conditions;

        public AndCondition(Condition[] conditions) {
            this.conditions = conditions;
        }

        @Override
        public String build() {
            return wrap(Arrays.stream(conditions).map(Condition::build).collect(Collectors.joining(AND)));
        }
    }

    /**
     * OrCondition
     */
    public static class OrCondition extends Condition {
        private static final String OR = " | ";

        private final Condition[] conditions;

        public OrCondition(Condition[] conditions) {
            this.conditions = conditions;
        }

        @Override
        public String build() {
            return wrap(Arrays.stream(conditions).map(Condition::build).collect(Collectors.joining(OR)));
        }
    }
}
