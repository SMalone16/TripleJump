# TripleJump

A lightweight Paper plugin for the Pawling Minecraft Mod Lab classroom server.

## What it does

Players in Survival or Adventure mode get **two extra mid-air jumps** before they must touch the ground to reset.

1. First jump: normal Minecraft jump.
2. Second jump: use the client's flight-toggle/double-jump input while airborne.
3. Third jump: use the same input again after the short re-arm delay.
4. Landing resets the jump counter.

The plugin never leaves the player in real flight mode. It temporarily enables the vanilla flight-toggle input, cancels the flight event, and turns that input into a controlled upward velocity boost. This keeps the mechanic server-side and compatible with the classroom Eaglercraft/ViaVersion stack.

## Build

Requires Java 21 and Maven.

```bash
mvn clean package
```

Output:

```text
target/TripleJump-1.0.0.jar
```

The GitHub Actions workflow also publishes the latest classroom-ready build to:

```text
dist/TripleJump-1.0.0.jar
```

## Configuration

`config.yml` exposes the second/third jump strength, horizontal carry, re-arm delay, particle count, and sound toggle.
