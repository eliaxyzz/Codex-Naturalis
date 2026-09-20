package it.polimi.ingsw.server.persistence;

import it.polimi.ingsw.server.ServerLog;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the saved games on disk, one JSON file per game.
 * <p>
 * Nothing here is allowed to take a running game down with it: a save that fails is logged and
 * play carries on, and a file that won't load is skipped so one bad save can't stop the server
 * from starting.
 */
public class GameStore {
    private static final Logger LOG = ServerLog.get();

    private final Path directory;

    public GameStore(Path directory) {
        this.directory = directory;
    }

    public Path getDirectory() {
        return directory;
    }

    /**
     * Writes a game down, replacing whatever was there before.
     * @param gameName The game's name, which is also its file name.
     * @param snapshot The game as JSON.
     */
    public void save(String gameName, JSONObject snapshot) {
        try {
            Files.createDirectories(directory);
            //write beside it and move into place, so a crash mid-write can't leave half a game behind
            Path temporary = Files.createTempFile(directory, fileName(gameName), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(snapshot.toJSONString());
            }
            Files.move(temporary, fileFor(gameName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not save the game '" + gameName + "'", e);
        }
    }

    /**
     * Forgets a game: it is over, or nobody is coming back to it.
     * @param gameName The game's name.
     */
    public void delete(String gameName) {
        try {
            Files.deleteIfExists(fileFor(gameName));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete the save of game '" + gameName + "'", e);
        }
    }

    /**
     * Reads every saved game back.
     * @return The saved games as JSON, skipping any file that couldn't be read.
     */
    public List<JSONObject> loadAll() {
        List<JSONObject> snapshots = new ArrayList<>();
        if (!Files.isDirectory(directory)) return snapshots;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                JSONObject snapshot = read(file);
                if (snapshot != null) snapshots.add(snapshot);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not read the saved games", e);
        }
        return snapshots;
    }

    private JSONObject read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object parsed = new JSONParser().parse(reader);
            if (parsed instanceof JSONObject snapshot) return snapshot;
            LOG.warning("Skipping '" + file.getFileName() + "': it isn't a saved game");
        } catch (IOException | ParseException | RuntimeException e) {
            LOG.log(Level.WARNING, "Skipping the unreadable save '" + file.getFileName() + "'", e);
        }
        return null;
    }

    private Path fileFor(String gameName) {
        return directory.resolve(fileName(gameName) + ".json");
    }

    /**
     * Game names come from clients, so they can't be trusted as file names. The hash keeps two
     * names that clean up to the same thing ("a b" and "a_b") in separate files.
     */
    private String fileName(String gameName) {
        return gameName.replaceAll("[^a-zA-Z0-9-_]", "_") + "-" + Integer.toHexString(gameName.hashCode());
    }
}
