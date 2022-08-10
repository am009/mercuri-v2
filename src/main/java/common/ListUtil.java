package common;

import java.util.List;

public class ListUtil {

    public static <T> T last(List<T> list) {
        assert list.size() > 0;
        return list.get(list.size() - 1);
    }
}
