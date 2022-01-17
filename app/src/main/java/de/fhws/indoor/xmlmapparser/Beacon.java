package de.fhws.indoor.xmlmapparser;

public class Beacon {
    public String name;
    public String mac;
    public String major;
    public String minor;
    public String uuid;
    public Vec3 position = new Vec3();
    public RadioModel mdl = new RadioModel();

    public boolean seen = false;
}
