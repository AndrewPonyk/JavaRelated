package com.shopflow.reco.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Graph node representing a product. Edges {@code (:Customer)-[:BOUGHT]->(:Product)}
 * are created by the order-event consumer; recommendations are derived by
 * traversing shared {@code BOUGHT} relationships.
 */
@Node("Product")
public class ProductNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("category")
    private String category;

    public ProductNode() {
    }

    public ProductNode(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }
}
