package guru.springframework.jdbc.dao;

import guru.springframework.jdbc.domain.Book;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.*;
import java.util.List;

@Component
public class BookDaoImpl implements BookDao {
    private final EntityManagerFactory emf;

    public BookDaoImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Book findBookByTitleNamedQuery(String title) {
        EntityManager em = getEntityManager();
        try {
            TypedQuery<Book> query = em.createNamedQuery("find_by_title", Book.class);
            query.setParameter("title", title);
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    @Override
    public Book findByISBN(String isbn) {
        EntityManager em = getEntityManager();

        try {
            // TypedQuery ensures the query results are Book entities.
            TypedQuery<Book> query = em.createQuery("SELECT b FROM Book b WHERE b.isbn = :isbn", Book.class);
            // Bind the ISBN separately from the JPQL query text.
            query.setParameter("isbn", isbn);
            // Expects exactly one match; throws if no book or multiple books match.
            return query.getSingleResult();
        } finally {
            // Close the persistence context whether the query succeeds or fails.
            em.close();
        }
    }

    @Override
    public Book getById(Long id) {
        EntityManager em = getEntityManager();
        try {
            return em.find(Book.class, id);
        } finally {
            em.close();
        }
    }

    /*
    TypedQuery<Book> provides a type-safe JPA query, so query.getSingleResult() returns an Book directly instead of
    an Object that would require a cast.

    Note: getSingleResult() throws NoResultException when no book matches and NonUniqueResultException if multiple
    books match. If zero or many results are valid, use getResultList() or getResultStream() instead.
    */
    @Override
    public Book findBookByTitle(String title) {
        EntityManager em = getEntityManager();
        TypedQuery<Book> query = em
                .createQuery("SELECT b FROM Book b where b.title = :title", Book.class);
        query.setParameter("title", title);
        Book book = query.getSingleResult();
        em.close();
        return book;
    }

    @Override
    public Book findBookByTitleCriteria(String title) {
        EntityManager em = getEntityManager();

        try {
            // Build a type-safe Criteria query rooted at the Book entity.
            CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
            CriteriaQuery<Book> criteriaQuery = criteriaBuilder.createQuery(Book.class);
            Root<Book> root = criteriaQuery.from(Book.class);

            // Define a parameterized condition matching the book title.
            ParameterExpression<String> titleParam = criteriaBuilder.parameter(String.class);
            Predicate titlePredicate = criteriaBuilder.equal(root.get("title"), titleParam);
            criteriaQuery.select(root).where(titlePredicate);

            // Bind the title value and require exactly one matching book.
            TypedQuery<Book> typedQuery = em.createQuery(criteriaQuery);
            typedQuery.setParameter(titleParam, title);

            return typedQuery.getSingleResult();
        } finally {
            // Close the persistence context whether the query succeeds or fails.
            em.close();
        }
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

    @Override
    public List<Book> findAll() {
        EntityManager em = getEntityManager();

        try {
            TypedQuery<Book> query = em.createNamedQuery("find_all_books", Book.class);

            return query.getResultList();
        } finally {
            em.close();
        }
    }

    private EntityManager getEntityManager() {
        return emf.createEntityManager();
    }
}
