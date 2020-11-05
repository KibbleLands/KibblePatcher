import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Test {
    static {
        try {
            main();
        } catch (Throwable ignored) {}
    }

    public int i;

    public static void main(String[] args) {
        List<String> list = Arrays.asList(args);
        list.forEach(e -> {

        });
        Test test = new Test();
        M2I m2I = () -> test.i;
        if (args != null) {
            Test2.main(null);
        }
    }

    public static void main() {}

    public static String[] test() {
        List<String> list = new ArrayList<>();
        return list.toArray(new String[0]);
    }

    interface M2I {
        int supply();
    }
}
