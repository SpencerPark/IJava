package io.github.spencerpark.ijava.execution;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BytesURLContext {
    // These are the relevant headers accessed by explicitly defined methods on URLConnection.
    enum ResourceHeaders {
        CONTENT_LENGTH("content-length"),
        LAST_MODIFIED("last-modified"),
        DATE("date"),
        CONTENT_TYPE("content-type");

        private static final Map<String, ResourceHeaders> REVERSE_LOOKUP = Map.of(
                CONTENT_LENGTH.headerName, CONTENT_LENGTH,
                LAST_MODIFIED.headerName, LAST_MODIFIED,
                DATE.headerName, DATE,
                CONTENT_TYPE.headerName, CONTENT_TYPE
        );

        public static ResourceHeaders lookup(String headerName) {
            return REVERSE_LOOKUP.get(headerName);
        }

        private final String headerName;

        ResourceHeaders(String headerName) {
            this.headerName = headerName;
        }

        public String getHeaderName() {
            return this.headerName;
        }
    }

    public static class Resource {
        private final URL location;

        private byte[] data;
        private ZonedDateTime definedAt;

        private Resource(String path, byte[] data) throws MalformedURLException {
            this.location = new URL(null, "bytes://" + path, new BytesURLStreamHandler(this));

            this.data = data;
            this.definedAt = Resource.now();
        }

        public URL getURL() {
            return this.location;
        }

        public void redefine(byte[] data) {
            this.data = data;
            this.definedAt = Resource.now();
        }

        public byte[] getData() {
            return this.data;
        }

        public int getContentLength() {
            return this.data.length;
        }

        public ZonedDateTime getLastModified() {
            return this.definedAt;
        }

        public static ZonedDateTime now() {
            return ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("GMT"));
        }

        public String getHeader(ResourceHeaders header) {
            switch (header) {
                case CONTENT_LENGTH:
                    return Integer.toString(this.getContentLength());
                case LAST_MODIFIED:
                    return DateTimeFormatter.RFC_1123_DATE_TIME.format(this.getLastModified());
                case DATE:
                    return DateTimeFormatter.RFC_1123_DATE_TIME.format(Resource.now());
                case CONTENT_TYPE:
                    return "application/octet-stream";
                default:
                    return null;
            }
        }

        @SuppressWarnings("unchecked")
        public Map<String, List<String>> makeHeaders() {
            return Map.ofEntries(
                    Arrays.stream(ResourceHeaders.values())
                            .map(header -> Map.entry(header.getHeaderName(), List.of(this.getHeader(header))))
                            .toArray(Map.Entry[]::new)
            );
        }
    }

    private static class BytesURLConnection extends URLConnection {
        private final Resource resource;

        protected BytesURLConnection(URL url, Resource resource) {
            super(url);
            this.resource = resource;
        }

        @Override
        public void connect() throws IOException {
            super.connected = true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(this.resource.getData());
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return this.resource.makeHeaders();
        }

        @Override
        public String getHeaderField(String name) {
            return this.resource.getHeader(ResourceHeaders.lookup(name));
        }

        @Override
        public String getHeaderFieldKey(int n) {
            ResourceHeaders[] headers = ResourceHeaders.values();
            return n < headers.length ? headers[n].getHeaderName() : null;
        }

        @Override
        public String getHeaderField(int n) {
            String headerName = this.getHeaderFieldKey(n);
            return headerName != null ? this.getHeaderField(headerName) : null;
        }
    }

    private static class BytesURLStreamHandler extends URLStreamHandler {
        private final Resource resource;

        public BytesURLStreamHandler(Resource resource) {
            this.resource = resource;
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new BytesURLConnection(u, resource);
        }
    }

    private final Map<String, Resource> repository;

    public BytesURLContext() {
        this.repository = new ConcurrentHashMap<>();
    }

    public void define(String path, byte[] data) {
        this.repository.compute(path, (key, value) -> {
            if (value != null) {
                value.redefine(data);
                return value;
            } else {
                try {
                    return new Resource(path, data);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public Resource findResource(String path) {
        return this.repository.get(path);
    }

    public URL lookupResourceLocation(String path) {
        Resource resource = this.repository.get(path);
        return resource == null ? null : resource.getURL();
    }
}
