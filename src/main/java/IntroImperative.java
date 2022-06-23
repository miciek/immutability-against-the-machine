import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IntroImperative {
    public static int extractYearStart(String show) throws Exception {
        int bracketOpen = show.indexOf('(');
        int dash        = show.indexOf('-');
        if(bracketOpen != -1 && dash > bracketOpen + 1) return Integer.parseInt(show.substring(bracketOpen + 1, dash));
        else throw new Exception();
    }

    public static int extractYearEnd(String show) throws Exception {
        int dash         = show.indexOf('-');
        int bracketClose = show.indexOf(')');
        if(dash != -1 && bracketClose > dash + 1) return Integer.parseInt(show.substring(dash + 1, bracketClose));
        else throw new Exception();
    }

    public static int extractSingleYear(String show) throws Exception {
        int dash         = show.indexOf('-');
        int bracketOpen  = show.indexOf('(');
        int bracketClose = show.indexOf(')');
        if (dash == -1 && bracketOpen != -1 && bracketClose > bracketOpen + 1)
            return Integer.parseInt(show.substring(bracketOpen + 1, bracketClose));
        else throw new Exception();
    }

    public static void main(String[] args) throws Exception {
        // mapping
        List<Integer> xs = Arrays.asList(1, 2, 3, 4, 5);
        List<Integer> result = new ArrayList<>();

        for (Integer x: xs) {
            result.add(x * x);
        }
        assert(result.toString().equals("[1, 4, 9, 16, 25]"));

        // exceptions
        String tvShow = "The Wire (2002-2008)";
        int start = extractYearStart(tvShow); // 2002
        assert(start == 2002);

        int end = extractYearEnd(tvShow); // 2008
        assert(start == 2008);

        extractYearStart("Chernobyl (2019)"); // ???

        Integer year = null;
        try {
            year = extractYearStart(tvShow);
        } catch(Exception e) {
            year = extractSingleYear(tvShow);
        }
    }
}
