package com.shopflow.reco.repository;

import com.shopflow.reco.domain.ProductNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

/**
 * Neo4j repository exposing the collaborative-filtering queries: co-purchase
 * ("also bought"), personalised recommendations for a customer, and a
 * popularity fallback for cold-start.
 */
public interface RecommendationRepository extends Neo4jRepository<ProductNode, String> {

    /**
     * "Customers who bought {@code productId} also bought…" — other products
     * purchased by customers who bought this one, ranked by co-occurrence.
     */
    @Query("""
            MATCH (p:Product {id: $productId})<-[:BOUGHT]-(c:Customer)-[:BOUGHT]->(rec:Product)
            WHERE rec.id <> $productId
            WITH rec, count(c) AS shoppers
            RETURN rec ORDER BY shoppers DESC LIMIT $limit
            """)
    List<ProductNode> alsoBought(@Param("productId") String productId, @Param("limit") int limit);

    /**
     * Personalised recommendations: products bought by customers who share
     * purchases with this customer, excluding what they already bought.
     */
    @Query("""
            MATCH (me:Customer {id: $customerId})-[:BOUGHT]->(:Product)
                  <-[:BOUGHT]-(other:Customer)-[:BOUGHT]->(rec:Product)
            WHERE NOT (me)-[:BOUGHT]->(rec)
            WITH rec, count(*) AS score
            RETURN rec ORDER BY score DESC LIMIT $limit
            """)
    List<ProductNode> recommendedForCustomer(@Param("customerId") String customerId, @Param("limit") int limit);

    /** Popularity fallback (cold-start): most-purchased products overall. */
    @Query("""
            MATCH (:Customer)-[b:BOUGHT]->(p:Product)
            WITH p, sum(b.count) AS total
            RETURN p ORDER BY total DESC LIMIT $limit
            """)
    List<ProductNode> trending(@Param("limit") int limit);
}
