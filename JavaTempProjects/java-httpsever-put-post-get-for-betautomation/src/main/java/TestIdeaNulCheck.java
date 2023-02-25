import org.w3c.dom.ls.LSOutput;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TestIdeaNulCheck {
    public static void main(String[] args) {
        List<String> list =Arrays.asList("1", "111", "11111", null);

        final List<String> collect = list.stream().filter(Objects::nonNull).map(elem -> {
            if (elem.length() < 1) return null;
            return elem;
        }).map(elem -> elem.length() + "test").collect(Collectors.toList());

        System.out.println(collect);
    }

}
