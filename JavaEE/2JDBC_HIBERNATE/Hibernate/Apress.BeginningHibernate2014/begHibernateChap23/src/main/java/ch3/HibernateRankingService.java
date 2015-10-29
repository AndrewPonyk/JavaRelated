package ch3;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import java.util.List;
import java.util.Map;

public class HibernateRankingService implements RankingService{
    @Override
    public int getRankingFor(String subject, String skill) {
        Session session = SessionUtil.getSession();
        Transaction tx = session.beginTransaction();

        Query query = session.createQuery("from Ranking r where r.subject.name=:name AND " +
                "r.skill.name=:skill");
        query.setParameter("name", subject);
        query.setParameter("skill", skill);

        int sum = 0, count = 0;
        for(Ranking item : (List<Ranking>)query.list()){
            sum += item.getRanking();
            count++;
        }
        tx.commit();
        session.close();
        return count ==0 ? 0 : sum/count;
    }

    @Override
    public void addRanking(String subject, String observer, String skill, int rankingVal) {
        Session session = SessionUtil.getSession();
        Transaction tx = session.beginTransaction();

        Person subjectPerson = savePerson(session, subject);
        Person observerPerson = savePerson(session, observer);
        Skill skillObj = saveSkill(session, skill);

        Ranking ranking = new Ranking();
        ranking.setObserver(observerPerson);
        ranking.setSubject(subjectPerson);
        ranking.setRanking(rankingVal);
        ranking.setSkill(skillObj);
        session.save(ranking);

        tx.commit();
    }

    @Override
    public void updateRanking(String subject, String observer, String skill, int ranking) {

    }

    @Override
    public void removeRanking(String subject, String observer, String skill) {
        Session session = SessionUtil.getSession();
        Transaction tx = session.beginTransaction();
        Ranking ranking = findRanking(subject, observer, skill);
        if (ranking != null) {
            session.delete(ranking);
        }
        tx.commit();
        session.close();
    }

    @Override
    public Ranking findRanking(String subject, String observer, String skill) {
        Session session = SessionUtil.getSession();
        Transaction tx = session.beginTransaction();
        Query query = session.createQuery("from Ranking r where " +
                "r.subject.name=:subject and " +
                "r.observer.name=:observer and " +
                "r.skill.name=:skill");
        query.setParameter("subject", subject);
        query.setParameter("observer", observer);
        query.setParameter("skill", skill);
        Ranking ranking = (Ranking) query.uniqueResult();
        tx.commit();
        session.close();
        return ranking;
    }

    @Override
    public Map<String, Integer> findRankingsFor(String subject) {
        return null;
    }

    @Override
    public Person findBestPersonFor(String skill) {
        Session session = SessionUtil.getSession();
        Transaction tx = session.beginTransaction();
        Person result = null;

        Query query = session.createQuery("select r.subject.name, avg(r.ranking)" +
                "from Ranking r where " +
                "r.skill.name=:skill " +
                "group by r.subject.name " +
                "order by avg(r.ranking) desc");
        query.setParameter("skill", skill);
        List<Object[]> list = query.list(); // ARRAY INSIDE !!!!!
        if(!list.isEmpty() ){
            return findPerson(session, (String)list.get(0)[0]);
        }

        tx.commit();
        session.close();

        return result;
    }


    public Person findPerson(Session session, String name){
        org.hibernate.Query query = session.createQuery("from Person p where p.name=:name");
        query.setString("name", name);
        Person person = (Person) query.uniqueResult();
        return person;
    }

    public Skill findSkill(Session session, String name){
        org.hibernate.Query query = session.createQuery("from Skill s where s.name=:name");
        query.setString("name", name);
        Skill skill = (Skill) query.uniqueResult();
        return skill;
    }

    public Person savePerson(Session session, String name) {
        Person person = findPerson(session, name);
        if(person==null) {
            person=new Person();
            person.setName(name);
            session.save(person);
        }
        return person;
    }

    public Skill saveSkill(Session session, String name) {
        Skill skill = findSkill(session, name);
        if(skill==null) {
            skill=new Skill();
            skill.setName(name);
            session.save(skill);
        }
        return skill;
    }
}