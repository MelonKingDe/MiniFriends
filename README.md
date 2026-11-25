# Minifriends

Client-side Fabric mod for Minecraft 26.2. Mini versions of players follow you around as a cosmetic.

## Usage

- Type `/minifriends` in chat to open the settings screen.
- Enter a player name per row (the skin is loaded from that name), toggle the nametag on/off, add up to 5 friends.
- Settings are saved to `config/minifriends.json`.

## Build

Requires Java 25.

```
gradle wrapper   # once, creates gradlew + wrapper jar
./gradlew build
```

The jar ends up in `build/libs/`. GitHub Actions builds every push (see `.github/workflows/build.yml`).
