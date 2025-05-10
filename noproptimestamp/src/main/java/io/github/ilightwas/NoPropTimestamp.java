package io.github.ilightwas;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoPropTimestamp extends JavaPlugin {
    private static int MAX_LINE_INDEX = 3; // Only check the first 3 Lines

    @Override
    public void onEnable() {
        getLogger().info("NoPropTimestamp enabled");
        final Path propFile = getServer().getWorldContainer().toPath().resolve("server.properties");
        BukkitRunnable removeTimeStamp = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    List<String> lines = Files.readAllLines(propFile);
                    Optional<Integer> timestampIndex = getTimestampLineIndex(lines);
                    if (timestampIndex.isPresent())
                        removeTimeStamp(lines, timestampIndex.get().intValue(), propFile);
                    else
                        getLogger().info("Could not find the timestamp");
                } catch (IOException e) {
                    getLogger().warning("Failed to rewrite server.properties: " + e.getMessage());
                }
            }
        };
        removeTimeStamp.runTaskAsynchronously(this);

    }

    private Optional<Integer> getTimestampLineIndex(List<String> lines) {
        int lineIndex = 0;
        Pattern pattern = Pattern.compile("^#\\w{3} \\w{3} \\d{1,2} .*");

        while (lineIndex < MAX_LINE_INDEX) {
            Matcher matcher = pattern.matcher(lines.get(lineIndex));
            if (matcher.matches()) {
                return Optional.of(Integer.valueOf(lineIndex));
            }
            ++lineIndex;
        }

        return Optional.empty();
    }

    private void removeTimeStamp(List<String> lines, int removeIndex, Path propFile) throws IOException {
        lines.remove(removeIndex);

        Path tempFile = Files.createTempFile(
                propFile.getParent(),
                propFile.getFileName().toString(),
                ".tmp");

        Files.write(tempFile, lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tempFile, propFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        getLogger().info("Stripped server.properties timestamp");
    }
}