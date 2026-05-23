package io.sentinelgateway.proxy;

/**
 * Runtime configuration parsed from CLI args.
 * Format: --key=value
 */
public record ProxyConfig(int port, String upstream, String policyFile) {

    public static ProxyConfig fromArgs(String[] args) {
        int port = 8080;
        String upstream = "http://localhost:9090";
        String policyFile = null;

        for (String arg : args) {
            if (arg.startsWith("--port="))       port = Integer.parseInt(arg.substring(7));
            else if (arg.startsWith("--upstream="))  upstream = arg.substring(11);
            else if (arg.startsWith("--policy="))    policyFile = arg.substring(9);
        }

        return new ProxyConfig(port, upstream, policyFile);
    }
}
