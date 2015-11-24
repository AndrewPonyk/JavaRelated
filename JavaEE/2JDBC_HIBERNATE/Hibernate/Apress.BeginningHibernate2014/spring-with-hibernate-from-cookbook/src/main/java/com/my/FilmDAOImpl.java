package com.my;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;

public class FilmDAOImpl implements FilmDAO{
    private SessionFactory sessionFactory;
    @Override
    public void save(Film film) {
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();
        session.saveOrUpdate(film);
        tx.commit();
        session.close();
    }

    @Override
    public List<Film> getAll() {
        Session session = sessionFactory.openSession();
        List<Film> allFilms = session.createQuery("from Film").list();
        session.close();
        return allFilms;
    }

    @Override
    public Film getById(Long filmId) {
        Session session = sessionFactory.openSession();
        Film film = (Film) session.get(Film.class, filmId);
        session.close();
        return film;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
}
