package sh.variiuz.worldagent.util;

import org.bukkit.Location;
import org.bukkit.World;

public final class Region {

    public final World world;
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public Region(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public int edgeX() {
        return maxX - minX + 1;
    }

    public int edgeY() {
        return maxY - minY + 1;
    }

    public int edgeZ() {
        return maxZ - minZ + 1;
    }

    public Location center() {
        return new Location(world,
                (minX + maxX) / 2.0 + 0.5,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0 + 0.5);
    }
}
