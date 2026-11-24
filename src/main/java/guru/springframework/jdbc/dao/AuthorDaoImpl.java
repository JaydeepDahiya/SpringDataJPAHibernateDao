package guru.springframework.jdbc.dao;

import guru.springframework.jdbc.domain.Author;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthorDaoImpl implements AuthorDao {

    private final EntityManagerFactory emf;

    public AuthorDaoImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Author findAuthorByNameNamedQuery(String firstName, String lastName) {
        EntityManager em = getEntityManager();
        try {
            // Look up the named query declared on Author; the query returns a type-safe Author result.
            TypedQuery<Author> query = em.createNamedQuery("find_by_name", Author.class);
            // Supply values for the named parameters defined in the JPQL.
            query.setParameter("first_name", firstName);
            query.setParameter("last_name", lastName);
            // Expects exactly one match; throws if none or multiple authors match.
            return query.getSingleResult();
        } finally {
            // Release the persistence context whether the query succeeds or fails.
            em.close();
        }
    }

    @Override
    public List<Author> findAll() {
        EntityManager em = getEntityManager();

        try {
            // Use the named query declared for Author; getResultList returns an empty list when there are no matches.
            TypedQuery<Author> query = em.createNamedQuery("author_find_all", Author.class);
            return query.getResultList();
        } finally {
            // Release the persistence context whether the query succeeds or fails.
            em.close();
        }
    }

    @Override
    public List<Author> listAuthorByLastNameLike(String lastName) {
        EntityManager em = getEntityManager();

        try {
            // JPQL queries the Author entity; the named parameter keeps the search value separate from the query.
            Query query = em.createQuery("SELECT a FROM Author a WHERE a.lastName LIKE :last_name");
            // Append % to match last names that begin with the supplied value.
            query.setParameter("last_name", lastName + "%");
            // Execute the query and return all matching authors.
            return query.getResultList();
        } finally {
            // Release the persistence context whether the query succeeds or fails.
            em.close();
        }
    }

    @Override
    public Author getById(Long id) {
        EntityManager em = getEntityManager();
        try {
            return em.find(Author.class, id);
        } finally {
            em.close();
        }
    }

    @Override
    public Author findAuthorByName(String firstName, String lastName) {
        EntityManager em = getEntityManager();
        try {
            // Create a type-safe JPQL query matching both the author's first and last names.
            TypedQuery<Author> query = em.createQuery("SELECT a FROM Author a " +
                    "WHERE a.firstName = :first_name AND a.lastName = :last_name", Author.class);
            // Bind the search values separately from the query text.
            query.setParameter("first_name", firstName);
            query.setParameter("last_name", lastName);
            // Expects exactly one match; throws if none or more than one is found.
            return query.getSingleResult();
        } finally {
            // Release the persistence context whether the query succeeds or fails.
            em.close();
        }
    }

    @Override
    public Author findAuthorByNameCriteria(String firstName, String lastName) {
        EntityManager em = getEntityManager();

        try {
            // Create a Criteria query that returns Author entities.
            CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
            CriteriaQuery<Author> criteriaQuery = criteriaBuilder.createQuery(Author.class);
            Root<Author> root = criteriaQuery.from(Author.class);

            // Match both supplied names; the Criteria API handles values separately from query structure.
            criteriaQuery.select(root).where(
                    criteriaBuilder.equal(root.get("firstName"), firstName),
                    criteriaBuilder.equal(root.get("lastName"), lastName));

            // Expects exactly one match; throws if none or multiple authors match.
            return em.createQuery(criteriaQuery).getSingleResult();
        } finally {
            // Release the persistence context whether the query succeeds or fails.
            em.close();
        }
    }

    @Override
    public Author saveNewAuthor(Author author) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the transaction required for this database operation.
            em.persist(author);  // Schedule the new author for insertion.
            transaction.commit(); // Commit; JPA flushes pending changes before completing the commit.
            return author;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                try {
                    transaction.rollback(); // Undo uncommitted changes if the operation failed.
                } catch (RuntimeException rollbackException) {
                    // Preserve the original failure while retaining any rollback failure for diagnosis.
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        } finally {
            em.close(); // Release the persistence context and its database resources.
        }
    }

    @Override
    public Author updateAuthor(Author author) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the transaction required for this database operation.
            Author merged = em.merge(author);  // Schedule the new author for insertion.
            transaction.commit(); // Commit; JPA flushes pending changes before completing the commit.
            return merged;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                try {
                    transaction.rollback(); // Undo uncommitted changes if the operation failed.
                } catch (RuntimeException rollbackException) {
                    // Preserve the original failure while retaining any rollback failure for diagnosis.
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        } finally {
            em.close();
        }
    }

    @Override
    public void deleteAuthorById(Long id) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the resource-local transaction.

            Author author = em.find(Author.class, id);
            if (author == null) {
                transaction.commit(); // Complete the read transaction cleanly.
                return; // No entity exists for this ID.
            }

            em.remove(author); // Mark the managed entity for deletion.
            em.flush(); // Execute the DELETE before the transaction completes.
            // This can be useful for detecting constraint or database errors before the commit call. It is otherwise
            // redundant because commit() automatically flushes pending changes.
            transaction.commit();
        } catch (RuntimeException exception) {
            if (transaction.isActive()) { // Roll back only if the transaction is still active.
                try {
                    transaction.rollback(); // Undo uncommitted changes.
                } catch (RuntimeException rollbackException) {
                    // Preserve the rollback failure without replacing the original operation error.
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        } finally {
            em.close(); // Release the persistence context and its resources.
        }
    }

    private EntityManager getEntityManager() {
        return emf.createEntityManager();
    }
}