package org.finos.waltz_util.common.helper;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.Checks.checkNotNull;
import static org.finos.waltz_util.common.helper.Checks.checkTrue;

public class ArrayUtilities {


    /**
     * @throws IllegalArgumentException If either <code>ts</code>
     * or <code>check</code> is null
     * @param ts  array of elements of type <code>T</code>
     * @param check  predicate function
     * @param <T>  type if the elements being checked
     * @return <code>true</code> iff all elements in <code>ts</code> pass
     * predicate function <code>check</code>
     */
    public static <T> boolean all(T[] ts, Predicate<T> check) {
        checkNotNull(ts, "Array must be provided");
        checkNotNull(check, "Predicate must be provided");

        for (T t : ts) {
            if (! check.test(t)) return false;
        }
        return true;
    }


    public static <T> boolean isEmpty(T[] arr) {
        if (arr == null) return true;
        return arr.length == 0;
    }


    public static int sum(int[] arr) {
        checkNotNull(arr, "arr cannot be null");
        int total = 0;
        for (int value : arr) {
            total += value;
        }
        return total;
    }


    public static <T> T last(T[] arr) {
        checkNotNull(arr, "array cannot be null");
        checkTrue(arr.length > 0, "array must not be empty");
        return arr[arr.length - 1];
    }


    public static <T> T[] initial(T[] arr) {
        checkNotNull(arr, "array cannot be null");
        checkTrue(arr.length > 0, "array must not be empty");
        return Arrays.copyOf(arr, arr.length - 1);
    }


    @SuppressWarnings("unchecked")
    public static <A, B> B[] map(A[] arr, Function<A, B> mapper) {
        checkNotNull(arr, "array cannot be null");
        checkNotNull(mapper, "mapper cannot be null");
        return (B[]) Stream
                .of(arr)
                .map(mapper)
                .toArray();
    }


    public static <T> T idx(T[] arr, int idx, T dflt) {
        try {
            return arr[idx];
        } catch (ArrayIndexOutOfBoundsException aioobe) {
            return dflt;
        }
    }

}