package vogellapack;


import java.util.regex.Pattern;

/**
 * A collection of utilities relating to InetAddresses.
 */
public class IpAddressValidator {

    private IpAddressValidator() {
    }
    
    private static final Pattern IPV4_PATTERN = 
        Pattern.compile(
                "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    private static final Pattern IPV6_STD_PATTERN = 
        Pattern.compile(
                "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");

    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = 
        Pattern.compile(
                "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    public static boolean isIPv4Address(final String input) {
        return IPV4_PATTERN.matcher(input).matches();
    }

    public static boolean isIPv6StdAddress(final String input) {
        return IPV6_STD_PATTERN.matcher(input).matches();
    }
    
    public static boolean isIPv6HexCompressedAddress(final String input) {
        return IPV6_HEX_COMPRESSED_PATTERN.matcher(input).matches();
    }
    
    public static boolean isIPv6Address(final String input) {
        return isIPv6StdAddress(input) || isIPv6HexCompressedAddress(input); 
    }
    
    public static void main(String[] args) {
		System.out.println(isIPv4Address("1.233.3.233"));
		System.out.println(isIPv6Address("2001:0db8:85a3:0042:1000:8a2e:0370:7334"));
	}
    
    
    /*
     * IPv6 addresses are represented as eight groups of four hexadecimal digits
     * separated by colons, for example 2001:0db8:85a3:0042:1000:8a2e:0370:7334,
		but methods of abbreviation of this full notation exist.
     * 
     * */
}