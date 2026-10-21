package br.com.bomgas.bina;

import java.net.URI;
import java.net.URISyntaxException;

/** Immutable destination: an in-flight request never follows a later environment change. */
public final class EnvironmentConfig {
    public static final String PRODUCTION_URL = "https://bina.fernandojunior.com.br";
    public final boolean local;
    public final String address;
    public final int port;
    private final String baseUrl;

    private EnvironmentConfig(boolean local, String address, int port, String baseUrl) {
        this.local = local;
        this.address = address;
        this.port = port;
        this.baseUrl = baseUrl;
    }

    public static EnvironmentConfig production() {
        return new EnvironmentConfig(false, "", 443, PRODUCTION_URL);
    }

    public static EnvironmentConfig local(String address, String portText) {
        String host = address == null ? "" : address.trim();
        int port;
        try {
            if (portText == null || !portText.trim().matches("[0-9]{1,5}")) throw new NumberFormatException();
            port = Integer.parseInt(portText.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Informe uma porta entre 1 e 65535");
        }
        String scheme = "http";
        if (host.startsWith("http://") || host.startsWith("https://")) {
            int separator = host.indexOf("://");
            scheme = host.substring(0, separator);
            host = host.substring(separator + 3);
        }
        try {
            URI uri = new URI(scheme + "://" + host + ":" + port);
            if (host.isEmpty() || uri.getHost() == null || uri.getUserInfo() != null
                    || !uri.getRawPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() != port) throw new URISyntaxException(host, "host inválido");
            // A port belongs in its own field; colons are only accepted in bracketed IPv6.
            if (host.contains(":") && !(host.startsWith("[") && host.endsWith("]")))
                throw new URISyntaxException(host, "porta duplicada");
            return new EnvironmentConfig(true, (scheme.equals("https") ? "https://" : "") + host,
                    port, uri.toASCIIString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Informe um endereço válido, sem caminho ou porta");
        }
    }

    public String baseUrl() { return baseUrl; }
    public String eventsUrl() { return baseUrl + "/api/eventos"; }
    public String sourcesUrl() { return baseUrl + "/api/origens"; }
    public String label() { return local ? "Local · " + baseUrl : "Produção"; }
}
