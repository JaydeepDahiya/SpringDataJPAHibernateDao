package guru.springframework.jdbc.dao;

import guru.springframework.jdbc.domain.Book;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

@Component
public class BookDaoImpl implements BookDao {
    private final EntityManagerFactory emf;

    public BookDaoImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Book getById(Long id) {
        EntityManager em = getEntityManager();
        Book book = getEntityManager().find(Book.class, id);
        em.close();
        return book;
    }

    /*
    TypedQuery<Book> provides a type-safe JPA query, so query.getSingleResult() returns an Book directly instead of
    an Object that would require a cast.
    */
    @Override
    public Book findBookByTitle(String title) {
        EntityManager em = getEntityManager();
        TypedQuery<Book> query = em.createQuery("SELECT b FROM Book b WHERE b.title= :title", Book.class);
        query.setParameter("title", title);

        Book book = query.getSingleResult(); // throws NoResultException: if No result found for query
        em.close();
        return book;
    }

    @Override
    public Book saveNewBook(Book book) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the transaction required for this database operation.
            em.persist(book);  // Schedule the new book for insertion.
            transaction.commit(); // Commit; JPA flushes pending changes before completing the commit.
            return book;
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
    public Book updateBook(Book book) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the transaction required for this database operation.
            Book merged = em.merge(book);  // Schedule the new book for insertion.
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
    public void deleteBookById(Long id) {
        EntityManager em = getEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            transaction.begin(); // Start the resource-local transaction.

            Book book = em.find(Book.class, id);
            if (book == null) {
                transaction.commit(); // Complete the read transaction cleanly.
                return; // No entity exists for this ID.
            }

            em.remove(book); // Mark the managed entity for deletion.
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
