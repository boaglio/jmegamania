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

## Development

See [AGENTS.md](AGENTS.md) for project layout, build/test commands, and coding conventions.

## References

- https://github.com/PhelipeScript/megamania
- https://github.com/zuppao/megamania
