# AGENTS.md

Java (Swing/Java2D) port of Atari 2600 Megamania. Must run on Windows and Linux.

## Build / Run / Test
- Build:  `mvn -q package -DskipTests`
- Run:    `./run.sh`  (Windows: `run.bat`) — builds then launches the jar
- Test:   `mvn test`  (JUnit 5)
- Java 17, Maven. Main class: `com.jmegamania.Main`.

## Layout
- `com.jmegamania`          — Main, GameWindow, GamePanel (game loop)
- `com.jmegamania.engine`   — Sprites, Sound (asset loading)
- `com.jmegamania.entities` — Player, Enemy, EnemyFormation, Bullet, EnemyShot
- `src/main/resources/sprites`, `.../sfx` — PNG sprites and WAV audio

## Conventions
- Gameplay must match the original Atari 2600 Megamania (movement, damage, lives, spawn/death).
- Use the real sprite/sfx assets in resources; don't hardcode placeholder graphics.
- Keep it cross-platform: no OS-specific paths or APIs.

## References
- https://github.com/PhelipeScript/megamania
- https://github.com/zuppao/megamania
