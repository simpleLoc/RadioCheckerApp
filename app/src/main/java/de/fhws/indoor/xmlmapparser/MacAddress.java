package de.fhws.indoor.xmlmapparser;

import java.util.Arrays;

public class MacAddress {
    static final int MAC_BYTES = 6;
    static final int MAC_STRLENGTH_NOCOLON = 12;
    static final int MAC_STRLENGTH_COLON = MAC_STRLENGTH_NOCOLON + 5;

    private byte[] mac;

    private static final byte[] parseUndelimitedMac(String undelimitedMacStr) {
        byte[] mac = new byte[MAC_BYTES];
        for(int i = 0; i < MAC_BYTES; ++i) {
            mac[i] = (byte) Integer.parseInt(undelimitedMacStr.substring(i * 2, i * 2 + 1), 16);
        }
        return mac;
    }

    MacAddress(String macStr) {
        if(macStr.length() == MAC_STRLENGTH_NOCOLON) { // not colon-separated
            mac = parseUndelimitedMac(macStr);
        } else if(macStr.length() == MAC_STRLENGTH_COLON) { // colon-separated
            mac = parseUndelimitedMac(macStr.replaceAll(":", ""));
        } else {
            throw new IllegalArgumentException("Illegal MAC-Address or unsupported format.");
        }
    }

    @Override
    public String toString() {
        return String.format("%02X%02X%02X%02X%02X%02X", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MacAddress that = (MacAddress) o;
        return Arrays.equals(mac, that.mac);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mac);
    }
}
