package io.github.xiao232ming.oritechaddonsone.forge;

/**
 * Implemented by the atomic forge so its screen can read how much the lasers aiming at it accelerate it.
 * <p>
 * The screen only sees the synced copy, which the forge refreshes right before it sends its GUI data (see
 * {@code AtomicForgeBlockEntityMixin}).
 */
public interface ForgeLaserSpeedupHost {

    /**
     * Processing speed of the forge relative to a single laser without any speed plugin: 0 without a laser,
     * 1 for one plain laser, 2 for one laser with a speed plugin, and so on.
     */
    float oritechaddonsone$laserSpeedup();
}
