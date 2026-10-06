# QueryBuilder & Pagination

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![JPA](https://img.shields.io/badge/JPA-Jakarta-blue)](https://jakarta.ee/specifications/persistence/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A powerful, fluent **JPQL / Native SQL query builder** for Spring Data JPA / Hibernate, plus a convenient **Pagination** helper.

Write complex queries with a clean, chainable API instead of string concatenation or Criteria API boilerplate.

```java
List<User> users = new QueryBuilder<>(em, User.class)
    .where("status", "ACTIVE")
    .andWhereLike("email", "%@company.com")
    .orderByDesc("createdDate")
    .limit(20)
    .list();
```

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [QueryBuilder](#querybuilder)
  - [Creating a Builder](#creating-a-builder)
  - [Basic Queries](#basic-queries)
  - [Conditions](#conditions)
  - [Logical Operators & Grouping](#logical-operators--grouping)
  - [Joins](#joins)
  - [Ordering, Limit & Offset](#ordering-limit--offset)
  - [Aggregates & Group By / Having](#aggregates--group-by--having)
  - [Custom Select](#custom-select)
  - [Native SQL](#native-sql)
  - [Delete Operations](#delete-operations)
  - [Debugging](#debugging)
- [Pagination](#pagination)
- [CrudService](#crudservice)
- [Complete Examples](#complete-examples)
- [Best Practices](#best-practices)
- [Limitations](#limitations)
- [Package Structure](#package-structure)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature                        | Supported |
|--------------------------------|-----------|
| Fluent chainable API           | ✅        |
| `WHERE` / `AND` / `OR` / `NOT` | ✅        |
| Nested condition groups        | ✅        |
| Joins (`INNER`, `LEFT`, …)     | ✅        |
| Aggregates (`COUNT`, `SUM`…)   | ✅        |
| `GROUP BY` + `HAVING`          | ✅        |
| Custom `SELECT` projections    | ✅        |
| Native SQL + parameter binding | ✅        |
| Safe deletes (no conditions = error) | ✅  |
| Pagination with Spring `Pageable` | ✅     |
| Batch save helpers             | ✅        |
| Debug / print generated query  | ✅        |

---

## Requirements

- **Java 17+**
- **Spring Boot 3.x** (or Spring Framework 6.x)
- **Jakarta Persistence** (JPA) / Hibernate 6.x
- Optional: Spring Data Commons (for `Pageable` and `Sort`)

---

## Installation

This library is currently distributed as **source code**. Follow the steps below to add it to your Spring Boot project.

### 1. Copy the source files

Create a package in your project (recommended: `com.yourcompany.persistence` or `com.yourcompany.query`) and copy these files:

| Class                 | Required | Description                              |
|-----------------------|----------|------------------------------------------|
| `QueryBuilder.java`   | ✅ Yes   | Core fluent query builder                |
| `FieldCondition.java` | ✅ Yes   | Internal condition representation        |
| `JoinCondition.java`  | ✅ Yes   | Join definition record                   |
| `Pagination.java`     | Optional | Helper for paged results                 |
| `PagedResult.java`    | Optional | DTO returned by `Pagination`             |
| `CrudService.java`    | Optional | Generic CRUD + QueryBuilder integration  |

### 2. Add required dependencies

Make sure your `pom.xml` (Maven) or `build.gradle` (Gradle) already includes:

**Maven**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**Gradle**
```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
```

No extra third-party libraries are required.

### 3. Register `CrudService` (optional but recommended)

If you use `CrudService`, make sure it is picked up by Spring component scanning:

```java
@Service
@Transactional(readOnly = true)
public class CrudService {
    // ... existing code
}
```

Or create a configuration class:

```java
@Configuration
public class PersistenceConfig {
    // CrudService is already annotated with @Service,
    // so it will be auto-detected if the package is scanned.
}
```

### 4. Verify EntityManager injection

```java
@Autowired
private EntityManager em;   // or use CrudService.getEm()
```

### Future (Maven Central)

Once published, installation will become:

```xml
<dependency>
    <groupId>com.yourcompany</groupId>
    <artifactId>query-builder</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Quick Start

### 1. Inject EntityManager (or use CrudService)

```java
@Autowired
private EntityManager em;
```

### 2. Write your first query

```java
List<Product> products = new QueryBuilder<>(em, Product.class)
    .where("active", true)
    .andWhere("price", ">=", 100)
    .orderByAsc("name")
    .list();
```

### 3. Prefer CrudService (recommended)

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final CrudService crudService;

    ProductService(CrudService crudService){
        crudService = crudService;
    }

    public List<Product> findActive() {
        return crudService.findBy(
            new QueryBuilder<>(crudService.getEm(), Product.class)
                .where("active", true)
                .orderByDesc("createdDate")
        );
    }
}
```

---

## QueryBuilder

### Creating a Builder

```java
QueryBuilder<User> qb = new QueryBuilder<>(entityManager, User.class);

// Optional: change the main alias (default is "e")
qb.withAlias("u");
```

### Basic Queries

```java
// List all
List<User> all = qb.list();

// Single result (or null)
User user = qb.where("email", "john@example.com").execute();

// Count
long total = qb.where("status", "ACTIVE").count();

// Exists-style check
boolean exists = qb.where("email", email).count() > 0;
```

### Conditions

#### Equality & Comparison Operators

```java
.where("status", "ACTIVE")                 // status = 'ACTIVE'
.where("age", ">=", 18)
.where("price", "<", 99.99)
.where("createdDate", ">", LocalDateTime.now().minusDays(30))
.where("score", "<>", 0)                   // not equal
```

#### Null Checks

```java
.whereIsNull("deletedAt")
.whereIsNotNull("emailVerifiedAt")
```

#### LIKE / NOT LIKE

```java
.whereLike("name", "%John%")
.whereNotLike("email", "%@temp-mail.com")
.whereLikeIgnoreCase("name", "john")       // LOWER(name) LIKE '%john%'
```

#### IN / NOT IN

```java
.whereIn("status", List.of("ACTIVE", "PENDING", "REVIEW"))
.whereNotIn("role", Set.of("ADMIN", "SUPER_ADMIN"))
```

#### BETWEEN

```java
.whereBetween("price", 50, 200)
.andWhereBetween("createdDate", start, end)

// Convenient date range (inclusive start, exclusive end of next day)
.andWhereDateBetween("createdDate",
    LocalDate.of(2025, 1, 1),
    LocalDate.of(2025, 12, 31))
```

#### Function-based Conditions

```java
.whereFunction("LOWER", "email", "=", "john@example.com")
.whereFunction("YEAR", "createdDate", "=", 2025)
.whereFunction("LENGTH", "name", ">", 5)

.whereIgnoreCase("username", "JohnDoe")
.whereTrim("code", " ABC ")
.whereTrimIgnoreCase("code", "abc")
```

#### Shorthand helpers

```java
.andWhere(...)          .orWhere(...)
.andWhereLike(...)      .orWhereLike(...)
.andWhereIn(...)        .orWhereIn(...)
.andWhereIsNull(...)    .orWhereIsNull(...)
.andWhereBetween(...)   .orWhereBetween(...)
.andWhereIgnoreCase(...) 
.andWhereLikeIgnoreCase(...)
```

### Logical Operators & Grouping

```java
// Default is AND
.where("status", "ACTIVE")
.andWhere("age", ">=", 18)

// Explicit OR
.where("status", "ACTIVE")
.orWhere("status", "PENDING")

// Nested groups with begin() / end()
.begin()
    .where("status", "ACTIVE")
    .orWhere("status", "PENDING")
.end()
.andWhere("age", ">=", 18)

// NOT
.not().where("status", "DELETED")

// NOT with a group
.not().begin()
    .where("role", "ADMIN")
    .orWhere("role", "SUPERUSER")
.end()
```

### Joins

```java
new QueryBuilder<>(em, Order.class)
    .join("customer", "c")                 // INNER JOIN Order.customer c
    .join("items", "i", "LEFT")            // LEFT JOIN Order.items i
    .where("c.email", "john@example.com")
    .andWhere("i.quantity", ">", 1)
    .list();
```

Supported join types: `INNER` (default), `LEFT`, `RIGHT`, `FULL` (database-dependent).

You can also change the root alias:

```java
.withAlias("o")
.join("customer", "c")
```

### Ordering, Limit & Offset

```java
.orderByAsc("createdDate")
.orderByDesc("price")

// Pagination helpers
.limit(20)                     // setMaxResults(20)
.offset(40)                    // setFirstResult(40)
.limit(40, 20)                 // offset + max
.setFirstResult(40)
.setMaxResults(20)
```

> **Note**: Currently only one `ORDER BY` column is kept (the last call wins). Multi-column sorting is planned.

### Aggregates & Group By / Having

```java
// Simple count (recommended)
long count = new QueryBuilder<>(em, User.class)
    .where("status", "ACTIVE")
    .count();

// Explicit aggregate selects
qb.selectCount()                              // COUNT(e) AS cnt
  .selectCount("id")
  .selectCountDistinct("email")
  .selectSum("amount", "total")
  .selectAvg("amount", "avgAmount")
  .selectMin("price")
  .selectMax("price")
  .selectAggregate("SUM", "amount", "total");

// GROUP BY + HAVING
new QueryBuilder<>(em, Order.class)
    .select("customerId")
    .selectSum("amount", "total")
    .groupBy("customerId")
    .having("total", ">", 1000)
    .andHavingFunction("COUNT", "id", ">", 5)
    .list();
```

### Custom Select

```java
// Specific fields only
qb.select("id", "name", "email");

// Distinct
qb.selectDistinct("status");

// Add expressions
qb.addSelect("COUNT(e)", "cnt")
  .addSelect("YEAR(e.createdDate)", "year")
  .addSelect("c.name", "customerName");   // after a join
```

### Native SQL

```java
List<Object[]> rows = new QueryBuilder<>(em, User.class)
    .nativeSql("""
        SELECT u.id, u.name, COUNT(o.id) AS order_count
        FROM users u
        LEFT JOIN orders o ON o.user_id = u.id
        WHERE u.status = :status
        GROUP BY u.id, u.name
        HAVING COUNT(o.id) > :minOrders
        ORDER BY order_count DESC
        """)
    .param("status", "ACTIVE")
    .param("minOrders", 3)
    .list();
```

Parameter helpers:

```java
.param("name", value)
.params(Map.of("a", 1, "b", 2))
.paramIn("ids", List.of(1L, 2L, 3L))
.paramNotIn("excluded", Set.of("A", "B"))
.clearParams()
```

### Delete Operations

```java
// Safe delete – throws if no WHERE conditions are present
int deleted = new QueryBuilder<>(em, User.class)
    .where("status", "INACTIVE")
    .andWhere("lastLogin", "<", cutoff)
    .delete();

// Delete everything (use with extreme caution)
int all = new QueryBuilder<>(em, TempData.class).deleteAll();
```

> **Important**: `DELETE` does **not** support joins. If you need to delete based on related entities, fetch the IDs first or use a subquery in native SQL.

### Debugging

```java
qb.printQry();   // logs the full JPQL/SQL + all bound parameters
```

Example log output:

```
=== JPQL SELECT ===
SELECT e FROM User e WHERE e.status = :param1 AND LOWER(e.email) LIKE :param2 ORDER BY e.createdDate DESC
=== PARAMETERS ===
  :param1 = ACTIVE
  :param2 = %@company.com
===================
```

---

## Pagination

`Pagination` makes it easy to work with Spring’s `Pageable`.

```java
Pagination<User> pagination = new Pagination<>(User.class, crudService);

// Most convenient – pass a Consumer
PagedResult<User> result = pagination.getPaged(pageable, qb -> {
    qb.where("status", "ACTIVE")
      .andWhereLike("name", "%" + searchTerm + "%");
});

// Or reuse an existing QueryBuilder
QueryBuilder<User> qb = new QueryBuilder<>(em, User.class)
    .where("status", "ACTIVE");
List<User> pageContent = pagination.getPage(pageable, qb);

// Map entities → DTOs
PagedResult<UserDto> dtoPage = pagination.map(result, UserDto::fromEntity);
```

### PagedResult

```java
public record PagedResult<T>(
    List<T> pageResult,   // content of the current page
    int page,             // current page (1-based)
    int pageSize,
    long count,           // total elements
    int totalPages
) {
    public static <T> PagedResult<T> of(List<T> content, int page, int pageSize, long totalElements) { ... }

    public boolean hasNext()      { return page < totalPages; }
    public boolean hasPrevious()  { return page > 1; }
    public boolean isEmpty()      { return pageResult == null || pageResult.isEmpty(); }
}
```

### Page numbering

- Spring’s `Pageable` is **0-based** (`page = 0` is the first page).
- All query methods (`getPage`, `getSimplePage`) correctly use `pageable.getOffset()`.
- The `page` field inside `PagedResult` is returned as **1-based** for convenience in API responses.

| Spring Pageable | Offset used | PagedResult.page |
|-----------------|-------------|------------------|
| page = 0        | 0           | 1                |
| page = 1        | pageSize    | 2                |
| page = 2        | 2×pageSize  | 3                |

### Sorting behaviour

- If `Pageable` contains a `Sort`, it is applied automatically.
- If no sort is provided, the builder defaults to `ORDER BY createdDate ASC`.  
  (Change or remove this default in `Pagination` if your entities do not have a `createdDate` field.)

---

## CrudService

A thin, generic service that pairs perfectly with `QueryBuilder`.  
It provides common CRUD operations without requiring a repository per entity.

### Find operations

```java
// By primary key
Optional<User> opt = crudService.findById(User.class, 1L);
User user = crudService.getById(User.class, 1L);          // throws EntityNotFoundException if not found

// Using QueryBuilder
List<User> list = crudService.findBy(queryBuilder);
Optional<User> one = crudService.findOneBy(queryBuilder);
User required = crudService.getOneBy(queryBuilder);       // throws if not found

long count = crudService.countBy(queryBuilder);
```

### Save & Update

`save` automatically decides between `persist` (new entity) and `merge` (existing entity) based on whether the identifier is `null`.

```java
// Create a new entity (id == null → persist)
User newUser = new User();
newUser.setEmail("john@example.com");
newUser.setStatus("ACTIVE");
User saved = crudService.save(newUser);          // returns the managed instance

// Update an existing entity (id != null → merge)
User existing = crudService.getById(User.class, 42L);
existing.setStatus("INACTIVE");
User updated = crudService.save(existing);

// Save multiple entities
List<User> users = List.of(user1, user2, user3);
List<User> savedUsers = crudService.saveAll(users);
```

#### Batch save (recommended for large collections)

```java
// Flushes and clears the persistence context every N entities
// to keep memory usage low
int savedCount = crudService.batchSave(largeList, 50);   // batch size = 50

// Example: import 10 000 records safely
List<Product> products = loadFromCsv(...);
int imported = crudService.batchSave(products, 100);
```

### Delete operations

```java
// By id
crudService.deleteById(User.class, 42L);

// By entity instance
crudService.delete(user);

// Multiple entities
crudService.deleteAll(List.of(user1, user2));

// Using QueryBuilder (bulk delete)
int deleted = crudService.deleteBy(
    new QueryBuilder<>(crudService.getEm(), User.class)
        .where("status", "INACTIVE")
        .andWhere("lastLogin", "<", cutoffDate)
);
```

### Other helpers

```java
crudService.refresh(entity);      // reload from database
crudService.detach(entity);       // remove from persistence context
crudService.flush();              // force flush
crudService.flushAndClear();      // flush + clear
```

### Full example – Service layer

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final CrudService crudService;

    public UserService(CrudService crudService){
      this.crudService = crudService;
    }
    public User create(CreateUserRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setName(request.name());
        user.setStatus("ACTIVE");
        return crudService.save(user);
    }

    public User updateStatus(Long id, String status) {
        User user = crudService.getById(User.class, id);
        user.setStatus(status);
        return crudService.save(user);
    }

    public List<User> findActiveUsers() {
        return crudService.findBy(
            new QueryBuilder<>(crudService.getEm(), User.class)
                .where("status", "ACTIVE")
                .orderByDesc("createdDate")
        );
    }

    public void deactivateInactiveUsers(LocalDateTime cutoff) {
        crudService.deleteBy(
            new QueryBuilder<>(crudService.getEm(), User.class)
                .where("status", "INACTIVE")
                .andWhere("lastLogin", "<", cutoff)
        );
    }
}
```

---

## Complete Examples

### Complex search with groups and joins

```java
List<Order> orders = new QueryBuilder<>(em, Order.class)
    .withAlias("o")
    .join("customer", "c")
    .join("items", "i", "LEFT")
    .begin()
        .where("o.status", "PAID")
        .orWhere("o.status", "SHIPPED")
    .end()
    .andWhere("c.country", "US")
    .andWhere("i.quantity", ">=", 1)
    .orderByDesc("o.createdDate")
    .limit(50)
    .list();
```

### Aggregation report

```java
List<Object[]> report = new QueryBuilder<>(em, Order.class)
    .select("customerId")
    .selectSum("amount", "totalAmount")
    .selectCount("id", "orderCount")
    .groupBy("customerId")
    .having("totalAmount", ">", 5000)
    .orderByDesc("totalAmount")
    .list();
```

### Native SQL with pagination

```java
QueryBuilder<User> qb = new QueryBuilder<>(em, User.class)
    .nativeSql("""
        SELECT * FROM users
        WHERE status = :status
        AND created_at >= :from
        ORDER BY created_at DESC
        """)
    .param("status", "ACTIVE")
    .param("from", LocalDateTime.now().minusMonths(1));

List<User> page = pagination.getSimplePage(pageable, qb);
```

### Soft-delete style update via QueryBuilder (delete example)

```java
int removed = new QueryBuilder<>(em, User.class)
    .where("status", "INACTIVE")
    .andWhereIsNull("deletedAt")
    .andWhere("lastLogin", "<", LocalDateTime.now().minusYears(2))
    .delete();
```

---

## Best Practices

1. **Create a new `QueryBuilder` instance per query** – it is not thread-safe.
2. Prefer the **fluent API** over raw JPQL for readability and safety.
3. Always use **`printQry()`** while developing complex queries.
4. Never call **`deleteAll()`** in production code unless you intentionally want to wipe a table.
5. For large datasets always apply **`limit` / pagination**.
6. When joining, filter on the joined alias (`c.email`) rather than deep paths when possible.
7. Keep entity field names in sync with the strings you pass to the builder.

---

## Limitations

- Only **one** `ORDER BY` column is currently supported.
- `DELETE` statements **do not support joins**.
- Nested groups are supported in `WHERE` but not yet in `HAVING`.
- Complex sub-queries should be written as native SQL or executed as separate queries.
- The builder does not validate field names against the entity metamodel (runtime error if wrong).

---

## Package Structure

Suggested layout:

```
com.yourcompany.persistence
├── QueryBuilder.java
├── FieldCondition.java
├── JoinCondition.java
├── Pagination.java
├── CrudService.java
└── dto
    └── PagedResult.java
```

---

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

Please keep the fluent style consistent and add tests for new functionality.

---

## License

Distributed under the **MIT License**. See `LICENSE` for more information.

```
MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

**Happy querying!** 🚀  
If you find this useful, please star the repository.
```
