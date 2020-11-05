import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

public class SplitTest {
    private static final String[] lines = new String[]{
            "12 34",
            "12 34  56 ",
            "/tp Fox2Code 10000 64 -10000"};
    private static String line;
    private static final int RUNS = 1000000;// 000000;
    private static final int PASSES = 3;// 000000;
    private static final int WAIT = 1000;// 000000;

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < lines.length; i++) {
            line = lines[i];
            System.gc();
            Thread.sleep(WAIT);
            testSplit();
            /*Thread.sleep(10);
            testSplitPattern();
            Thread.sleep(10);
            testSplitTokenizer();
            Thread.sleep(10);
            testSplitIndexOf2();*/
            System.gc();
            Thread.sleep(WAIT);
            testSplitX();
            //Thread.sleep(10);
            //testSplitIndexOfList();
            System.gc();
            Thread.sleep(WAIT);
            testSplitTemp();
            System.gc();
            Thread.sleep(WAIT);
            testSplitTempThreadLocal();
            System.out.println("------------------");
        }
    }

    public static void testSplit() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        for (int i = 0; i < RUNS; i++) {
            String[] st = line.split(" ");
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("Split: " + (System.currentTimeMillis() - start) + "ms("+Arrays.toString(elem)+")");
    }

    public static void testSplitPattern() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        Pattern pattern = Pattern.compile(" ");
        for (int i = 0; i < RUNS; i++) {
            String[] st = pattern.split(line, 0);
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("SplitPattern: " + (System.currentTimeMillis() - start) + "ms("+Arrays.toString(elem)+")");
    }

    public static void testSplitTokenizer() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        for (int i = 0; i < RUNS; i++) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            String[] st = new String[tokenizer.countTokens()];
            for (int index = 0; index < st.length; index++) {
                st[index] = tokenizer.nextToken();
            }
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("SplitTokenizer: " + (System.currentTimeMillis() - start) + "ms ("+ Arrays.toString(elem) +")");
    }

    public static void testSplitIndexOf2() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        for (int i = 0; i < RUNS; i++) {
            int len = line.length();
            //noinspection ConstantConditions because it's for testing
            if (len == 0) {
                continue;
            }
            int index = len;
            int elems = 1;
            while (index-->0) {
                if (line.charAt(index) == ' ') {
                    elems++;
                }
            }
            //noinspection ConstantConditions because it's for testing
            if (line.charAt(len - 1) == ' ') elems--;
            if (elems == 1) {
                continue;
            }
            String[] st = new String[elems];
            index = 0;
            elems = 0;
            int prev = 0;
            while (index < len) {
                if (line.charAt(index) == ' ') {
                    st[elems] = line.substring(prev, index);
                    prev = index + 1;
                    elems++;
                }
                index++;
            }
            if (elems != st.length) {
                st[elems] = line.substring(prev);
            }
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("SplitIndexOf2: " + (System.currentTimeMillis() - start) + "ms ("+ Arrays.toString(elem) +")");
    }

    public static void testSplitX() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        for (int i = 0; i < RUNS; i++) {
            // START
            char[] chars = line.toCharArray();
            int len = chars.length;
            if (len == 0) {
                continue;
            }
            int index = len;
            int elems = chars[len - 1] == ' ' ? 0 : 1;
            while (index-->0) {
                if (chars[index] == ' ') {
                    elems++;
                }
            }
            if (elems == 1) {
                continue;
            }
            String[] st = new String[elems];
            elems = 0;
            int prev = 0;
            while (++index < len) {
                if (chars[index] == ' ') {
                    st[elems++] = new String(chars, prev, index-prev);
                    prev = index + 1;
                }
            }
            if (elems != st.length) {
                st[elems] = new String(chars, prev, len-prev);
            }
            // END
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("SplitX: " + (System.currentTimeMillis() - start) + "ms ("+ Arrays.toString(elem) +")");
    }

    public static void testSplitTemp() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        for (int r = 0; r < RUNS; r++) {
            String[] temp = new String[(line.length() / 2) + 2];
            int wordCount = 0;
            int i = 0;
            int j = line.indexOf(' '); // first substring

            while (j >= 0)
            {
                temp[wordCount++] = line.substring(i, j);
                i = j + 1;
                j = line.indexOf(' ', i); // rest of substrings
            }

            if (line.charAt(line.length() - 1) != ' ') {
                temp[wordCount++] = line.substring(i); // last substring
            }

            String[] st = new String[wordCount];
            System.arraycopy(temp, 0, st, 0, wordCount);
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("SplitTemp: " + (System.currentTimeMillis() - start) + "ms ("+ Arrays.toString(elem) +")");
    }

    private static final int LIMIT = 32;// 000000;
    private static final int MIN = 8;

    public static void testSplitTempThreadLocal() {
        long start = System.currentTimeMillis();
        ThreadLocal<String[]> threadLocal = new ThreadLocal<>();
        String[] elem = null;
        for (int r = 0; r < RUNS; r++) {
            String[] temp;
            if (line.length() < LIMIT && line.length() > MIN) {
                temp = threadLocal.get();
                if (temp == null) {
                    threadLocal.set(temp = new String[LIMIT]);
                }
            } else {
                temp = new String[(line.length() / 2) + 2];
            }
            int wordCount = 0;
            int i = 0;
            int j = line.indexOf(' '); // first substring

            while (j >= 0)
            {
                temp[wordCount++] = line.substring(i, j);
                i = j + 1;
                j = line.indexOf(' ', i); // rest of substrings
            }

            if (line.charAt(line.length() - 1) != ' ') {
                temp[wordCount++] = line.substring(i); // last substring
            }

            String[] st = new String[wordCount];
            System.arraycopy(temp, 0, st, 0, wordCount);
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("SplitTemp(ThreadLocal): " + (System.currentTimeMillis() - start) + "ms ("+ Arrays.toString(elem) +")");
    }

    private static final String[] ARRAY_CACHE = new String[0];

    public static void testSplitIndexOfList() {
        long start = System.currentTimeMillis();
        String[] elem = null;
        ThreadLocal<List<String>> cache = new ThreadLocal<>();
        for (int i = 0; i < RUNS; i++) {
            //noinspection ConstantConditions because it's for testing
            if (line.isEmpty()) continue;
            List<String> list = cache.get();
            if (list == null) {
                cache.set(list = new ArrayList<>(16));
            } else {
                list.clear();
            }
            int prev = 0;
            int index;
            while ((index = line.indexOf(' ', prev)) != -1) {
                list.add(line.substring(prev, index));
                prev = index + 1;
            }
            if (prev != line.length()) {
                list.add(line.substring(prev, line.length() - 1));
            }
            String[] st = list.toArray(ARRAY_CACHE);
            //int x = Integer.parseInt(st[0]);
            //int y = Integer.parseInt(st[1]);
            elem = st;
        }
        System.out.println("IndexOfList: " + (System.currentTimeMillis() - start) + "ms ("+ Arrays.toString(elem) +")");
    }
}
