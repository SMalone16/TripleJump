package org.pawling.triplejump;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class TripleJumpPlugin extends JavaPlugin implements Listener {

    private static final int EXTRA_JUMPS_PER_AIRBORNE_SEQUENCE = 2;

    private final Map<UUID, JumpState> states = new HashMap<>();
    private BukkitTask monitorTask;

    private double secondJumpVelocity;
    private double thirdJumpVelocity;
    private double horizontalMultiplier;
    private int rearmDelayTicks;
    private int particleCount;
    private boolean playSound;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(this, this);
        monitorTask = getServer().getScheduler().runTaskTimer(this, this::monitorPlayers, 1L, 1L);

        getLogger().info("TripleJump enabled: two extra jumps, reset on landing.");
    }

    @Override
    public void onDisable() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }

        for (Player player : getServer().getOnlinePlayers()) {
            JumpState state = states.get(player.getUniqueId());
            revokePluginFlight(player, state);
        }
        states.clear();
    }

    private void loadSettings() {
        secondJumpVelocity = getConfig().getDouble("second-jump-velocity", 0.55);
        thirdJumpVelocity = getConfig().getDouble("third-jump-velocity", 0.62);
        horizontalMultiplier = getConfig().getDouble("horizontal-multiplier", 1.05);
        rearmDelayTicks = Math.max(1, getConfig().getInt("rearm-delay-ticks", 4));
        particleCount = Math.max(0, getConfig().getInt("particle-count", 10));
        playSound = getConfig().getBoolean("play-sound", true);
    }

    private void monitorPlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            JumpState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new JumpState());

            if (!isEligible(player) || player.isDead()) {
                revokePluginFlight(player, state);
                state.reset();
                continue;
            }

            boolean onGround = player.isOnGround();

            if (onGround) {
                revokePluginFlight(player, state);
                state.extraJumpsRemaining = EXTRA_JUMPS_PER_AIRBORNE_SEQUENCE;
                state.wasOnGround = true;
                state.rearmTicksRemaining = 0;
                continue;
            }

            if (state.wasOnGround) {
                state.wasOnGround = false;
            }

            if (state.rearmTicksRemaining > 0) {
                state.rearmTicksRemaining--;
                continue;
            }

            if (state.extraJumpsRemaining > 0 && !state.flightGrantedByPlugin && !player.getAllowFlight()) {
                player.setAllowFlight(true);
                state.flightGrantedByPlugin = true;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        JumpState state = states.get(player.getUniqueId());

        if (state == null || !isEligible(player) || !state.flightGrantedByPlugin || player.isOnGround()) {
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        state.flightGrantedByPlugin = false;

        if (state.extraJumpsRemaining <= 0) {
            return;
        }

        int jumpNumber = (EXTRA_JUMPS_PER_AIRBORNE_SEQUENCE - state.extraJumpsRemaining) + 2;
        double verticalVelocity = jumpNumber == 2 ? secondJumpVelocity : thirdJumpVelocity;

        Vector velocity = player.getVelocity();
        velocity.setX(velocity.getX() * horizontalMultiplier);
        velocity.setZ(velocity.getZ() * horizontalMultiplier);
        velocity.setY(verticalVelocity);
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);

        state.extraJumpsRemaining--;
        state.rearmTicksRemaining = state.extraJumpsRemaining > 0 ? rearmDelayTicks : 0;

        giveFeedback(player, jumpNumber);
    }

    private void giveFeedback(Player player, int jumpNumber) {
        if (particleCount > 0) {
            player.getWorld().spawnParticle(
                    Particle.CLOUD,
                    player.getLocation().add(0.0, 0.15, 0.0),
                    particleCount,
                    0.25,
                    0.05,
                    0.25,
                    0.02
            );
        }

        if (playSound) {
            float pitch = jumpNumber == 2 ? 1.15f : 1.45f;
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.65f, pitch);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        states.put(event.getPlayer().getUniqueId(), new JumpState());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        JumpState state = states.remove(player.getUniqueId());
        revokePluginFlight(player, state);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, () -> {
            JumpState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new JumpState());
            revokePluginFlight(player, state);
            state.reset();
        });
    }

    private boolean isEligible(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private void revokePluginFlight(Player player, JumpState state) {
        if (state != null && state.flightGrantedByPlugin) {
            player.setFlying(false);
            player.setAllowFlight(false);
            state.flightGrantedByPlugin = false;
        }
    }

    private static final class JumpState {
        private int extraJumpsRemaining = EXTRA_JUMPS_PER_AIRBORNE_SEQUENCE;
        private boolean wasOnGround = true;
        private boolean flightGrantedByPlugin = false;
        private int rearmTicksRemaining = 0;

        private void reset() {
            extraJumpsRemaining = EXTRA_JUMPS_PER_AIRBORNE_SEQUENCE;
            wasOnGround = true;
            flightGrantedByPlugin = false;
            rearmTicksRemaining = 0;
        }
    }
}
