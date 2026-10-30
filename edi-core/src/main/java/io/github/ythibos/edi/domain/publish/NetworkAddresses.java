package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NetworkAddresses {

    private static final Pattern IPV4 = Pattern.compile("(?<![\\w.])((?:\\d{1,3}\\.){3}\\d{1,3})(?!\\.?\\d)(?![\\w-])");
    private static final Pattern IPV6 = Pattern.compile("(?<![\\w:])((?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4})(?![\\w:])", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_AND_PORT = Pattern.compile(
            "(?<![\\w.-])((?:[a-z0-9-]+\\.)*[a-z][a-z0-9-]*):(\\d{2,5})(?![\\w])", Pattern.CASE_INSENSITIVE);
    private static final List<String> IPV4_DOCUMENTATION_RANGES = List.of("192.0.2.", "198.51.100.", "203.0.113.");
    private static final String IPV6_DOCUMENTATION_RANGE = "2001:db8:";
    private static final Set<String> FILE_EXTENSIONS = Set.of("java", "kt", "kts", "ts", "tsx", "js", "mjs", "cjs", "md", "py", "rb", "go", "rs", "c",
            "h", "cpp", "sh", "bash", "yml", "yaml", "json", "xml", "toml", "properties", "sql", "txt", "html", "css", "scss", "gradle", "log", "conf");

    private NetworkAddresses() {
    }

    static Optional<String> foundIn(String text) {
        if (anyMatch(IPV4, text, NetworkAddresses::isAnIpv4AddressOutsideDocumentation)
                || anyMatch(IPV6, text, NetworkAddresses::isAnIpv6AddressOutsideDocumentation)) {
            return Optional.of("an IP address outside the documentation ranges");
        }
        if (anyMatch(HOST_AND_PORT, text, NetworkAddresses::namesAHost)) {
            return Optional.of("a host and its port");
        }
        return Optional.empty();
    }

    private static boolean anyMatch(Pattern pattern, String text, Predicate<Matcher> found) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (found.test(matcher)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnIpv4AddressOutsideDocumentation(Matcher address) {
        String written = address.group(1);
        boolean octets = List.of(written.split("\\.")).stream().allMatch(octet -> Integer.parseInt(octet) <= 255);
        return octets && IPV4_DOCUMENTATION_RANGES.stream().noneMatch(written::startsWith);
    }

    private static boolean isAnIpv6AddressOutsideDocumentation(Matcher address) {
        String written = address.group(1).toLowerCase(Locale.ROOT);
        boolean shaped = written.contains("::") || written.chars().filter(character -> character == ':').count() == 7;
        boolean hexadecimal = written.chars().anyMatch(Character::isDigit) && written.replace(":", "").length() > 1;
        return shaped && hexadecimal && !written.startsWith(IPV6_DOCUMENTATION_RANGE);
    }

    private static boolean namesAHost(Matcher hostAndPort) {
        String host = hostAndPort.group(1).toLowerCase(Locale.ROOT);
        String lastLabel = host.substring(host.lastIndexOf('.') + 1);
        return !(host.contains(".") && FILE_EXTENSIONS.contains(lastLabel));
    }
}
