package de.fhws.xmlmapparser;

public class UWBAnchor {
    public String name;
    public String deviceId;
    public String shortDeviceId;
    public String bleMac;
    public Vec3 position = new Vec3();

    public UWBAnchor(String name, String deviceId, String bleMac, Vec3 position) {
        this.name = name;
        this.deviceId = deviceId;
        this.shortDeviceId = deviceId.substring(deviceId.length()-4);
        this.bleMac = bleMac;
        this.position = position;
    }

    public boolean seen = false;
}
