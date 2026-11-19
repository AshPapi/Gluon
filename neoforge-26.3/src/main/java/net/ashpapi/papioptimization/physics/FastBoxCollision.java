package net.ashpapi.papioptimization.physics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Branch-optimized Continuous 1D Sweep Collision for Axis-Aligned Bounding Boxes.
 *
 * Mathematically identical to Vanilla Minecraft's VoxelShape.collide for single-box shapes,
 * but replaces axis-cycling, coordinate-list binary searches (findIndex), and 3D voxel grid
 * bitset loops with direct O(1) continuous interval overlap tests.
 *
 * Preserves 100% Vanilla physics parity down to 1.0E-7 floating-point precision.
 */
public final class FastBoxCollision {

    public static final double EPSILON = 1.0E-7;

    private FastBoxCollision() {}

    /**
     * Computes the maximum allowed movement distance for moving bounding box along the given axis
     * when obstructed by a static collision box.
     */
    public static double collideSingleBox(Direction.Axis axis, AABB moving, AABB box, double distance) {
        if (Math.abs(distance) < EPSILON) {
            return 0.0;
        }

        switch (axis) {
            case X -> {
                if (moving.minY < box.maxY - EPSILON && moving.maxY > box.minY + EPSILON &&
                    moving.minZ < box.maxZ - EPSILON && moving.maxZ > box.minZ + EPSILON) {
                    if (distance > 0.0 && moving.maxX <= box.minX + EPSILON) {
                        double diff = box.minX - moving.maxX;
                        if (diff < distance) {
                            distance = diff;
                        }
                    } else if (distance < 0.0 && moving.minX >= box.maxX - EPSILON) {
                        double diff = box.maxX - moving.minX;
                        if (diff > distance) {
                            distance = diff;
                        }
                    }
                }
            }
            case Y -> {
                if (moving.minX < box.maxX - EPSILON && moving.maxX > box.minX + EPSILON &&
                    moving.minZ < box.maxZ - EPSILON && moving.maxZ > box.minZ + EPSILON) {
                    if (distance > 0.0 && moving.maxY <= box.minY + EPSILON) {
                        double diff = box.minY - moving.maxY;
                        if (diff < distance) {
                            distance = diff;
                        }
                    } else if (distance < 0.0 && moving.minY >= box.maxY - EPSILON) {
                        double diff = box.maxY - moving.minY;
                        if (diff > distance) {
                            distance = diff;
                        }
                    }
                }
            }
            case Z -> {
                if (moving.minX < box.maxX - EPSILON && moving.maxX > box.minX + EPSILON &&
                    moving.minY < box.maxY - EPSILON && moving.maxY > box.minY + EPSILON) {
                    if (distance > 0.0 && moving.maxZ <= box.minZ + EPSILON) {
                        double diff = box.minZ - moving.maxZ;
                        if (diff < distance) {
                            distance = diff;
                        }
                    } else if (distance < 0.0 && moving.minZ >= box.maxZ - EPSILON) {
                        double diff = box.maxZ - moving.minZ;
                        if (diff > distance) {
                            distance = diff;
                        }
                    }
                }
            }
        }
        return distance;
    }
}
