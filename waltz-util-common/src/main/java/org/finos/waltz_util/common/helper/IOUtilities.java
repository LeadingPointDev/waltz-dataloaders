package org.finos.waltz_util.common.helper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import static java.util.stream.Collectors.toList;
import static org.finos.waltz_util.common.helper.Checks.checkNotNull;


public class IOUtilities {

    public static List<String> readLines(InputStream stream) {
        checkNotNull(stream, "stream must not be null");
        return streamLines(stream).collect(toList());
    }


    public static Stream<String> streamLines(InputStream inputStream) {
        checkNotNull(inputStream, "inputStream must not be null");
        InputStreamReader streamReader = new InputStreamReader(inputStream);
        BufferedReader reader = new BufferedReader(streamReader);
        return reader
                .lines();
    }


    public static void copyStream(InputStream input, OutputStream output)
            throws IOException
    {
        checkNotNull(input, "Input stream cannot be null");
        checkNotNull(output, "Output stream cannot be null");

        byte[] buff = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buff)) != -1) {
            output.write(buff, 0, bytesRead);
        }
    }


    public static String readAsString(InputStream stream) {
        checkNotNull(stream, "stream must not be null");

        return streamLines(stream)
                .collect(Collectors.joining());
    }


    /**
     * Attempts to locate <code>fileName</code> via either:
     * <ul>
     *     <li>root of classpath</li>
     *     <li>directory: <code>${user.home}/.waltz</code></li>
     * </ul>
     * @param fileName file (or path) to be located
     * @return Resource representing the file
     */
    public static Resource getFileResource(String fileName) {
        Resource resource = new ClassPathResource(fileName);
        if (!resource.exists()) {
            String home = System.getProperty("user.home");
            resource = new FileSystemResource(home + "/.waltz/" + fileName);
        }
        return resource;
    }
}

