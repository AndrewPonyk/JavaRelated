package com.ap;

import lombok.Builder;


/**
 * @Builder adds all fields constructor and creates a builder for class
 * Also look to @Singular if you will have collections as a field(it would generate add, addAll and clear methods instead of SETTER)
 */
@Builder
public class EntityWithBuilder {
    private Integer id;
    private String name;
}
