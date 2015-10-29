package ch3;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

public class Programch3 {

    public  static RankingService rankingService = new HibernateRankingService();

    public static void main(String[] args) {
        System.out.println("Chapter 3 : Persons and their skills and rankings");

        Session session= SessionUtil.getSession();
        Transaction tx=session.beginTransaction();

        example1SaveThreePersonsAndRanking(session);
        example2ExtractRankings(session);
        example3ChangeRanking(session);
        tx.commit(); // be CAREFUL with transactions, here we perform commit, and in ranking service we also perform
        // beginTransaction and commit !!!, avoid nested transactions !!!!

        System.out.println("The average ranking Of Peter in Java is " + rankingService.getRankingFor("Peter", "Java"));
        System.out.println("Ivan rank Peter's Java skill as : " + rankingService.findRanking("Peter", "Ivan", "Java").getRanking());
        System.out.println(" !!! The best guy in Java is " + rankingService.findBestPersonFor("Java").getName());

        SessionUtil.closeFactory();
    }

    private  static void example1SaveThreePersonsAndRanking(Session session){
    // OLD CODE
 /*       Person andrew = rankingService.savePerson(session, "Andrew");
        Person ivan = rankingService.savePerson(session, "Ivan");
        Person peter = rankingService.savePerson(session, "Peter");
        Skill java = rankingService.saveSkill(session, "Java");

        Ranking andrewRankPeter = new Ranking();
        andrewRankPeter.setObserver(andrew);
        andrewRankPeter.setSubject(peter);
        andrewRankPeter.setRanking(9);
        andrewRankPeter.setSkill(java);

        Ranking ivanRankPeter = new Ranking();
        ivanRankPeter.setObserver(andrew);
        ivanRankPeter.setSubject(peter);
        ivanRankPeter.setRanking(5);
        ivanRankPeter.setSkill(java);


        session.save(andrewRankPeter);
        session.save(ivanRankPeter);*/


        // new CODE

        rankingService.addRanking("Peter", "Andrew", "Java", 9);
        rankingService.addRanking("Peter", "Ivan", "Java", 5);
        rankingService.addRanking("Ivan", "Peter", "Java", 10);

        System.out.println("---------");
    }

    private  static void example2ExtractRankings(Session session){
        Ranking ranking1 = (Ranking) session.get(Ranking.class, 1L); // get Ranking with id=1

        System.out.println("ranking1 = " + ranking1);
        System.out.println(ranking1.getObserver().getName()); // get observer name
        System.out.println(ranking1.getSubject().getName());
        System.out.println("---------");
    }

    private  static void example3ChangeRanking(Session session) {
        Query queryRanking = session.createQuery("from Ranking r");
        Ranking ranking = (Ranking) queryRanking.list().get(0);
        ranking.setRanking(ranking.getRanking() + 1);
        session.save(ranking);
        System.out.println("Adding 1 to Ranking value");
        System.out.println("---------");
    }

}