package guru.springframework.jdbc.dao;

import guru.springframework.jdbc.domain.Author;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthorDaoImpl implements AuthorDao {

    private final EntityManagerFactory emf;

    public AuthorDaoImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public List<Author> listAuthorByLastNameLike(String lastName) {
        EntityManager em = getEntityManager();

        try {
            Query query = em.createQuery("SELECT a FROM Author a WHERE a.lastName LIKE :last_name");
            query.setParameter("last_name", lastName + "%");
            return query.getResultList();
        } finally {
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

    /*
    TypedQuery<Author> provides a type-safe JPA query, so query.getSingleResult() returns an Author directly instead of
    an Object that would require a cast.

    The named parameters :first_name and :last_name are bound with setParameter(), helping prevent syntax/projection
    mistakes and keeping the input values separate from JPQL.

    Note: getSingleResult() throws NoResultException when no author matches and NonUniqueResultException if multiple
    authors match. If zero or many results are valid, use getResultList() or getResultStream() instead.
    */
    @Override
    public Author findAuthorByName(String firstName, String lastName) {
        EntityManager em = getEntityManager();
        try {
            TypedQuery<Author> query = em.createQuery("SELECT a FROM Author a " +
                    "WHERE a.firstName = :first_name AND a.lastName = :last_name", Author.class);
            query.setParameter("first_name", firstName);
            query.setParameter("last_name", lastName);
            return query.getSingleResult();
        } finally {
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
            em.close(); // Release the persistence context and its database resources.
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