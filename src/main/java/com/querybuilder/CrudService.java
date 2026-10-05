package com.querybuilder;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CrudService {
    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init(){
        setEm(em);
    }

    @Transactional
    public <T> T save(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        Object id = em.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .getIdentifier(entity);

        if (id == null) {
            em.persist(entity);
        } else {
            entity = em.merge(entity);
        }

        em.flush();
        return entity;
    }

    @Transactional
    public <T> List<T> saveAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        List<T> result = new ArrayList<>();
        int batchSize = 100;

        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<T> batch = entities.stream()
                    .skip(i)
                    .limit(end - i)
                    .toList();

            for (T entity : batch) {
                result.add(save(entity));
            }

            if (i + batchSize < entities.size()) {
                em.clear();
            }
        }
        return result;
    }

    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        return Optional.ofNullable(em.find(entityClass, id));
    }

    public <T, ID> T getById(Class<T> entityClass, ID id) {
        return findById(entityClass, id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Entity " + entityClass.getSimpleName() + " not found with id: " + id));
    }

    public <T> List<T> findAll(Class<T> entityClass) {
        String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
        return em.createQuery(jpql, entityClass).getResultList();
    }

    @Transactional
    public <T, ID> void deleteById(Class<T> entityClass, ID id) {
        T entity = getById(entityClass, id);
        em.remove(em.contains(entity) ? entity : em.merge(entity));
    }

    @Transactional
    public <T> void delete(T entity) {
        if (entity == null) return;
        if (!em.contains(entity)) {
            entity = em.merge(entity);
        }
        em.remove(entity);
    }

    @Transactional
    public <T> void deleteAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        entities.forEach(this::delete);
    }

    public <T> List<T> findBy(QueryBuilder<T> builder) {
        return builder.list();
    }

    public <T> Optional<T> findOneBy(QueryBuilder<T> builder) {
        return Optional.ofNullable(builder.execute());
    }

    public <T> T getOneBy(QueryBuilder<T> builder) {
        return findOneBy(builder)
                .orElseThrow(() -> new EntityNotFoundException("No entity found matching the query"));
    }

    public <T> long countBy(QueryBuilder<T> builder) {
        return builder.count();
    }

    @Transactional
    public <T> int deleteBy(QueryBuilder<T> builder) {
        return builder.delete();
    }

    public <T> void refresh(T entity) {
        if (entity != null) {
            em.refresh(entity);
        }
    }

    public <T> void detach(T entity) {
        if (entity != null) {
            em.detach(entity);
        }
    }

    @Transactional
    public void flush() {
        em.flush();
    }

    @Transactional
    public void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Transactional
    public <T> int batchSave(Collection<T> entities, int batchSize) {
        if (entities == null || entities.isEmpty()) return 0;

        int count = 0;
        for (T entity : entities) {
            save(entity);
            count++;
            if (count % batchSize == 0) {
                em.flush();
                em.clear();
            }
        }
        em.flush();
        em.clear();
        return count;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }
}
