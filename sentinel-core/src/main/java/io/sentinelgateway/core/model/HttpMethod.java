package io.sentinelgateway.core.model;

public enum HttpMethod {
    GET, HEAD, OPTIONS,      // safe / read
    POST, PUT, PATCH, DELETE; // mutating

    public boolean isMutation() {
        return this == POST || this == PUT || this == PATCH || this == DELETE;
    }
}
