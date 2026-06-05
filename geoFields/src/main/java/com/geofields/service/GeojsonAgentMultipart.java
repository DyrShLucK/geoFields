package com.geofields.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class GeojsonAgentMultipart {

    private static final String CRLF = "\r\n";

    private GeojsonAgentMultipart() {
    }

    static byte[] buildFilePart(String boundary, String fieldName, String filename, String contentType, byte[] fileBytes)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 256);
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                + sanitizeFilename(filename) + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write((CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String sanitizeFilename(String filename) {
        return filename.replace("\\", "_").replace("\"", "_");
    }
}
