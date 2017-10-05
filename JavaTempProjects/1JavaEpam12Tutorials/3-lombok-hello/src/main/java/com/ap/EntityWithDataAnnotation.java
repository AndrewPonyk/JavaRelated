package com.ap;

import lombok.Data;
import lombok.NonNull;
import lombok.Singular;

import java.util.List;

/**
 * @Data
All together now: A shortcut for @ToString, @EqualsAndHashCode, @Getter on all fields, @Setter on all non-final fields, and @RequiredArgsConstructor!

 @RequiredArgsConstructor generates a constructor with 1 parameter for each field that requires special handling. All non-initialized final fields get a parameter, as well as any fields that are marked as @NonNull that aren't initialized where they are declared.
 */
@Data
public class EntityWithDataAnnotation {
    @NonNull/*this field will be included in constructor, because notnull is present*/
    private Integer id;
    private String name;
    private List<String> parents;
}
