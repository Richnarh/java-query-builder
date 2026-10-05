package com.querybuilder;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Helper for paginated queries using {@link QueryBuilder}.
 * <p>
 * Works with Spring's {@link Pageable} (0-based page numbers).
 * The returned {@link PagedResult} exposes a 1-based page number
 * for convenience in API responses.
 */
public class Pagination<T> {
    private final Class<T> entityClass;
    private final CrudService crudService;

    public Pagination(Class<T> entityClass, CrudService crudService) {
        this.entityClass = entityClass;
        this.crudService = crudService;
    }

    /**
     * Returns a full paged result (content + metadata).
     * <p>
     * Page number inside {@link PagedResult} is 1-based.
     */
    public PagedResult<T> getPaged(Pageable pageable, Consumer<QueryBuilder<T>> builderConsumer) {
        List<T> content = getPage(pageable, builderConsumer);
        long total = getCount(builderConsumer);
        int totalPages = pageable.getPageSize() <= 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());
        return new PagedResult<>(content, pageable.getPageNumber() + 1, pageable.getPageSize(), total, totalPages);
    }

    /**
     * Returns only the content of the requested page.
     * Uses Spring's native 0-based offset.
     */
    public List<T> getPage(Pageable pageable, Consumer<QueryBuilder<T>> builderConsumer) {
        validatePageable(pageable);

        QueryBuilder<T> qb = createQueryBuilder();
        if (builderConsumer != null) {
            builderConsumer.accept(qb);
        }

        applySorting(pageable, qb);
        return qb
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .list();
    }

    public List<T> getPage(Pageable pageable, QueryBuilder<T> builder) {
        validatePageable(pageable);

        QueryBuilder<T> qb = builder != null ? builder : createQueryBuilder();
        applySorting(pageable, qb);

        return qb
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .list();
    }

    public List<T> getSimplePage(Pageable pageable, QueryBuilder<T> qb) {
        validatePageable(pageable);
        applySorting(pageable, qb);

        return qb
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .list();
    }

    public long getCount(Consumer<QueryBuilder<T>> builderConsumer) {
        QueryBuilder<T> qb = createQueryBuilder();

        if (builderConsumer != null) {
            builderConsumer.accept(qb);
        }

        return qb.count();
    }

    /**
     * Maps the content of a {@link PagedResult} to another type.
     */
    public <R> PagedResult<R> map(PagedResult<T> pagedResult, Function<T, R> mapper) {
        List<R> mappedContent = pagedResult.pageResult().stream()
                .map(mapper)
                .toList();
        return new PagedResult<>(mappedContent, pagedResult.page(), pagedResult.pageSize(), pagedResult.count(), pagedResult.totalPages());
    }

    private QueryBuilder<T> createQueryBuilder() {
        return new QueryBuilder<>(crudService.getEm(), entityClass);
    }

    private void applySorting(Pageable pageable, QueryBuilder<T> qb) {
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                if (order.isDescending()) {
                    qb.orderByDesc(order.getProperty());
                } else {
                    qb.orderByAsc(order.getProperty());
                }
            });
        }
    }

    private void validatePageable(Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable must not be null");
        }
        if (pageable.getPageNumber() < 0) {
            throw new IllegalArgumentException("Page number must be >= 0 (0-based)");
        }
        if (pageable.getPageSize() < 1) {
            throw new IllegalArgumentException("Page size must be >= 1");
        }
    }
}