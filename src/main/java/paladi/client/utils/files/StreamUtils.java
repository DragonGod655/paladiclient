/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.files;

import paladi.client.PaladiClient;
import org.apache.commons.io.IOUtils;

import java.io.*;

public class StreamUtils {
    private StreamUtils() {
    }

    public static void copy(File from, File to) {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            in.transferTo(out);
        } catch (IOException e) {
            PaladiClient.LOG.error("Error copying from file '{}' to file '{}'.", from.getName(), to.getName(), e);
        }
    }

    public static void copy(InputStream in, File to) {
        try (OutputStream out = new FileOutputStream(to)) {
            in.transferTo(out);
        } catch (IOException e) {
            PaladiClient.LOG.error("Error writing to file '{}'.", to.getName());
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
