package de.fhws.indoor.xmlmapparser;

public class UWBAnchor {
    public String name;
    public String deviceId;
    public String shortDeviceId;
    public MacAddress bleMac;
    public Vec3 position = new Vec3();

    public UWBAnchor(String name, String deviceId, MacAddress bleMac, Vec3 position) {
        this.name = name;
        this.deviceId = deviceId;
        this.shortDeviceId = deviceId.substring(deviceId.length()-4);
        this.bleMac = bleMac;
        this.position = position;
    }

    public boolean seen = false;
}
