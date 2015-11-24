package com.my;


import java.util.List;

public interface FilmDAO {
    public void save(Film film);
    public List<Film> getAll();
    public Film getById(Long filmId);
}
