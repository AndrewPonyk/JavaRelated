package restoreip;

import java.util.*;

public class RestoreIpAddresses {

    private final String s;
    private final List<String> solutions = new ArrayList<>();

    public RestoreIpAddresses(String s) {
        if (s == null || s.isEmpty())
            throw new IllegalArgumentException("String must not be empty");
        this.s = s;
    }

    public void solveAndPrint() {
        backtrack(new ArrayList<>(), 0);
        System.out.printf("Found %d valid IP address(es) for \"%s\"%n%n", solutions.size(), s);

        for (int i = 0; i < solutions.size(); i++) {
            System.out.printf("#%d: %s%n", i + 1, solutions.get(i));
        }
    }

    private void backtrack(List<String> segments, int start) {
        if (segments.size() == 4) {
            if (start == s.length()) {
                solutions.add(String.join(".", segments));
            }
            return;
        }
        for (int len = 1; len <= 3 && start + len <= s.length(); len++) {
            String seg = s.substring(start, start + len);
            if (isValidSegment(seg)) {
                segments.add(seg);
                backtrack(segments, start + len);
                segments.remove(segments.size() - 1);
            }
        }
    }

    private boolean isValidSegment(String seg) {
        if (seg.length() > 1 && seg.charAt(0) == '0') return false;
        int val = Integer.parseInt(seg);
        return val <= 255;
    }
}
