package com.my;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringHibernateProgram
{
    public static void main( String[] args )
    {
        System.out.println( "Spring 4 with Hibernate 4" );
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring.xml");
        FilmDAO filmDao = (FilmDAO) context.getBean("filmDao");

        Film titanic = new Film();
        titanic.setName("Titanic");
        titanic.setReleaseYear(1997L);

        filmDao.save(titanic);

        context.close();
    }
}
