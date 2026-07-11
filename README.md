# JMegamania

A Java port of the Atari 2600 game **Megamania**, built with Swing/Java2D.
Created for learning purposes and licensed under the GNU GPL (see `LICENSE`).

## Requirements
- Java 17+
- Maven

Runs on both Windows and Linux.

## Running

```bash
./run.sh          # Linux / macOS
run.bat           # Windows
```

The scripts build the project and launch the game. To build manually:

```bash
mvn -q package -DskipTests
java -jar target/jmegamania.jar
```

## Gameplay

Faithful to the original Atari 2600 rules:

- Eight attack waves cycle endlessly: hamburgers, cookies, bugs, radial tires,
  diamonds, steam irons, bow ties and space dice. Horizontal waves sweep across
  the MegaSphere (wrapping at the edges); vertical waves descend from the top.
- First loop scoring: 20/30/40/50/60/70/80/90 points per object by wave.
  From the second loop on, every object is worth 90 points.
- The energy bar uses the ROM's exact timing: 83 units, draining one unit every
  32 frames (a full bar lasts about 44 seconds), shown in 20 chunks of four
  units. Running dry costs a blaster. Each unit left when a wave is cleared
  pays one object's point value as a bonus.
- You start with three reserve blasters and earn another every 10,000 points,
  up to six in reserve.
- Missiles are guided: they follow the blaster's horizontal movement
  (game 1 of the original).

Controls: arrow keys to move, space to fire, Enter to restart after game over,
F11 to toggle fullscreen.

## Development

See [AGENTS.md](AGENTS.md) for project layout, build/test commands, and coding conventions.

## References

- https://github.com/PhelipeScript/megamania
- https://github.com/zuppao/megamania
