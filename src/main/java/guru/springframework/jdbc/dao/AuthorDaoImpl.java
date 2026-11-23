package guru.springframework.jdbc.dao;

import guru.springframework.jdbc.domain.Author;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

/**
 * Created by jt on 8/28/21.
 */
@Component
public class AuthorDaoImpl implements AuthorDao {

    private final EntityManagerFactory emf;

    public AuthorDaoImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Author getById(Long id) {
        return getEntityManager().find(Author.class, id);
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
        TypedQuery<Author> query = getEntityManager().createQuery("SELECT a FROM Author a " +
                "WHERE a.firstName = :first_name and a.lastName = :last_name", Author.class);
        query.setParameter("first_name", firstName);
        query.setParameter("last_name", lastName);

        return query.getSingleResult(); // doesn't throw NoResultException if No result found then returns null
    }

    /*
    flush() synchronizes the persistence context with the database: it makes JPA send pending SQL now instead of waiting
    until commit. Here, it causes INSERT to run before commit(), so database errors (such as a constraint violation) can
    surface at that point. It does not commit; transaction can still be rolled back afterward. If you remove it, commit()
    normally triggers flush automatically, so an explicit flush is only needed when you want SQL executed before commit.

    A transaction groups the insert into one atomic unit: the change is committed if the operation succeeds, or can be
    rolled back if it fails. JPA requires a transaction for persist() when using a resource-local EntityManager like
    this one. Be sure to roll back on exceptions and close the EntityManager when finished.
     */
    @Override
    public Author saveNewAuthor(Author author) {
        EntityManager em = getEntityManager();
        em.getTransaction().begin(); // Start a transaction for the database changes.
        em.persist(author);          // Mark the author as new so JPA will insert it.
        //em.flush();                // Send pending SQL to database before committing. NOTE: flush require transaction
        em.getTransaction().commit();// Commit the insert transaction.
        // In this method, commit() automatically flushes pending changes, so the explicit em.flush() is redundant. Keep
        // it only if you specifically want the SQL executed before the commit—for example, to surface database errors
        // earlier. The ID should generally be available after commit(), depending on entity’s ID-generation strategy.
        return author;
    }

    @Override
    public Author updateAuthor(Author author) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the transaction required for this database operation.
            Author merged = em.merge(author); // Copy the detached author's state into a managed entity.
            transaction.commit(); // Commit; JPA flushes pending updates before completing the commit.
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













