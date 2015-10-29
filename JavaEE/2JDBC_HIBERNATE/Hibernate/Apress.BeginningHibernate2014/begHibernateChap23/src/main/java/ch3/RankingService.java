package ch3;


import org.hibernate.Session;

import java.util.Map;

public interface RankingService {

    int getRankingFor(String subject, String skill);

    void addRanking(String subject, String observer, String skill, int ranking);

    void updateRanking(String subject, String observer, String skill, int ranking);

    void removeRanking(String subject, String observer, String skill);

    Ranking findRanking(String subject, String observer, String skill);

    Map<String, Integer> findRankingsFor(String subject);

    Person findBestPersonFor(String skill);

    Person savePerson(Session session, String name);

    Skill saveSkill(Session session, String skill);
}