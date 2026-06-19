package net.neotowns.data;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neotowns.model.*;
import net.neotowns.model.enums.*;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCHEMA_VERSION = 1;

    private static DataSource dataSource;
    private static ExecutorService asyncWriter;
    private static Path dbPath;
    private static String dbType;

    private DatabaseManager() {}

    public static void init(String type, Path path, String host, String dbName, String user, String password, int poolSize) {
        dbType = type;
        dbPath = path;

        HikariConfig hk = new HikariConfig();

        if ("mysql".equalsIgnoreCase(type)) {
            hk.setJdbcUrl("jdbc:mysql://" + host + "/" + dbName);
            hk.setUsername(user);
            hk.setPassword(password);
            hk.setMaximumPoolSize(poolSize);
        } else {
            Path parent = path.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to create database directory: " + parent, e);
                }
            }
            hk.setJdbcUrl("jdbc:sqlite:" + path.toAbsolutePath());
            hk.setMaximumPoolSize(1);
            hk.addDataSourceProperty("journal_mode", "WAL");
            hk.addDataSourceProperty("synchronous", "NORMAL");
            hk.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");
        }

        dataSource = new HikariDataSource(hk);
        asyncWriter = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NeoTowns-DB-Writer");
            t.setDaemon(true);
            return t;
        });

        runMigrations();
        LOGGER.info("[NeoTowns] Database initialized (type={}, pool={})", type, poolSize);
    }

    public static void shutdown() {
        if (asyncWriter != null) {
            asyncWriter.shutdown();
            try {
                asyncWriter.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource instanceof HikariDataSource hk && !hk.isClosed()) {
            hk.close();
        }
        LOGGER.info("[NeoTowns] Database shut down.");
    }

    public static void async(Runnable task) {
        asyncWriter.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("[NeoTowns] Async DB error", e);
            }
        });
    }

    public static void flush() {
        try {
            asyncWriter.submit(() -> {}).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("[NeoTowns] Flush timed out", e);
        }
    }

    // ── Schema ──────────────────────────────────────────────────────────────

    private static void runMigrations() {
        execute(conn -> {
            createTables(conn);
            int version = readSchemaVersion(conn);
            if (version < SCHEMA_VERSION) {
                migrate(conn, version);
            }
        });
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS towns (
                    id              TEXT PRIMARY KEY,
                    name            TEXT NOT NULL,
                    mayor_uuid      TEXT NOT NULL,
                    state_id        TEXT,
                    treasury_world  TEXT NOT NULL,
                    treasury_x      INTEGER NOT NULL,
                    treasury_y      INTEGER NOT NULL,
                    treasury_z      INTEGER NOT NULL,
                    daily_upkeep    INTEGER NOT NULL DEFAULT 1,
                    resident_tax    INTEGER NOT NULL DEFAULT 0,
                    tax_type        TEXT NOT NULL DEFAULT 'FLAT',
                    max_claims      INTEGER NOT NULL DEFAULT 16,
                    is_open         INTEGER NOT NULL DEFAULT 0,
                    is_pvp          INTEGER NOT NULL DEFAULT 0,
                    is_fire         INTEGER NOT NULL DEFAULT 0,
                    is_mob_spawn    INTEGER NOT NULL DEFAULT 1,
                    motd            TEXT,
                    perms_bitmask   INTEGER NOT NULL DEFAULT 0,
                    founded_day     INTEGER NOT NULL,
                    lead_title      TEXT NOT NULL DEFAULT 'Mayor'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS town_residents (
                    town_id     TEXT NOT NULL REFERENCES towns(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    role        TEXT NOT NULL DEFAULT 'RESIDENT',
                    PRIMARY KEY (town_id, player_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS town_allies (
                    town_id     TEXT NOT NULL REFERENCES towns(id) ON DELETE CASCADE,
                    ally_id     TEXT NOT NULL REFERENCES towns(id) ON DELETE CASCADE,
                    PRIMARY KEY (town_id, ally_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chunk_claims (
                    dimension   TEXT NOT NULL,
                    chunk_x     INTEGER NOT NULL,
                    chunk_z     INTEGER NOT NULL,
                    town_id     TEXT NOT NULL REFERENCES towns(id) ON DELETE CASCADE,
                    plot_type   TEXT NOT NULL DEFAULT 'DEFAULT',
                    owner_uuid  TEXT,
                    sale_price  INTEGER NOT NULL DEFAULT 0,
                    is_embassy  INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (dimension, chunk_x, chunk_z)
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_town ON chunk_claims(town_id)");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS states (
                    id              TEXT PRIMARY KEY,
                    name            TEXT NOT NULL,
                    chancellor_uuid TEXT NOT NULL,
                    nation_id       TEXT,
                    treasury_world  TEXT NOT NULL,
                    treasury_x      INTEGER NOT NULL,
                    treasury_y      INTEGER NOT NULL,
                    treasury_z      INTEGER NOT NULL,
                    state_tax       INTEGER NOT NULL DEFAULT 0,
                    tax_type        TEXT NOT NULL DEFAULT 'FLAT',
                    gov_type        TEXT NOT NULL DEFAULT 'DEMOCRACY',
                    constitution    TEXT,
                    founded_day     INTEGER NOT NULL,
                    lead_title      TEXT NOT NULL DEFAULT 'Chancellor'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS state_laws (
                    state_id    TEXT NOT NULL REFERENCES states(id) ON DELETE CASCADE,
                    law_name    TEXT NOT NULL,
                    law_body    TEXT NOT NULL,
                    PRIMARY KEY (state_id, law_name)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS state_cabinet (
                    state_id    TEXT NOT NULL REFERENCES states(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    role_title  TEXT NOT NULL,
                    PRIMARY KEY (state_id, player_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nations (
                    id              TEXT PRIMARY KEY,
                    name            TEXT NOT NULL,
                    leader_uuid     TEXT NOT NULL,
                    treasury_world  TEXT NOT NULL,
                    treasury_x      INTEGER NOT NULL,
                    treasury_y      INTEGER NOT NULL,
                    treasury_z      INTEGER NOT NULL,
                    nation_tax      INTEGER NOT NULL DEFAULT 0,
                    tax_type        TEXT NOT NULL DEFAULT 'FLAT',
                    gov_type        TEXT NOT NULL DEFAULT 'DEMOCRACY',
                    constitution    TEXT,
                    ideology        TEXT,
                    anthem          TEXT,
                    founded_day     INTEGER NOT NULL,
                    lead_title      TEXT NOT NULL DEFAULT 'Emperor'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_laws (
                    nation_id   TEXT NOT NULL REFERENCES nations(id) ON DELETE CASCADE,
                    law_name    TEXT NOT NULL,
                    law_body    TEXT NOT NULL,
                    PRIMARY KEY (nation_id, law_name)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_cabinet (
                    nation_id   TEXT NOT NULL REFERENCES nations(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    role_title  TEXT NOT NULL,
                    PRIMARY KEY (nation_id, player_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_diplomacy (
                    nation_a    TEXT NOT NULL REFERENCES nations(id) ON DELETE CASCADE,
                    nation_b    TEXT NOT NULL REFERENCES nations(id) ON DELETE CASCADE,
                    status      TEXT NOT NULL DEFAULT 'NEUTRAL',
                    PRIMARY KEY (nation_a, nation_b)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_citizens (
                    nation_id   TEXT NOT NULL REFERENCES nations(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (nation_id, player_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wars (
                    id              TEXT PRIMARY KEY,
                    aggressor_id    TEXT NOT NULL REFERENCES nations(id),
                    defender_id     TEXT NOT NULL REFERENCES nations(id),
                    declared_at     INTEGER NOT NULL,
                    started_at      INTEGER,
                    aggressor_score INTEGER NOT NULL DEFAULT 0,
                    defender_score  INTEGER NOT NULL DEFAULT 0,
                    status          TEXT NOT NULL DEFAULT 'WARNING'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS elections (
                    id          TEXT PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_id   TEXT NOT NULL,
                    opened_at   INTEGER NOT NULL,
                    closes_at   INTEGER NOT NULL,
                    status      TEXT NOT NULL DEFAULT 'OPEN'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS election_votes (
                    election_id     TEXT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
                    voter_uuid      TEXT NOT NULL,
                    candidate_uuid  TEXT NOT NULL,
                    PRIMARY KEY (election_id, voter_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS town_debt (
                    town_id         TEXT PRIMARY KEY REFERENCES towns(id) ON DELETE CASCADE,
                    days_in_debt    INTEGER NOT NULL DEFAULT 0,
                    last_failed_day INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS neotowns_meta (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """);
        }
    }

    private static int readSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM neotowns_meta WHERE key = 'schema_version'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Integer.parseInt(rs.getString("value"));
        }
        return 0;
    }

    private static void migrate(Connection conn, int fromVersion) {
        LOGGER.info("[NeoTowns] Running DB migration {} -> {}", fromVersion, SCHEMA_VERSION);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO neotowns_meta (key, value) VALUES ('schema_version', ?)")) {
            ps.setString(1, String.valueOf(SCHEMA_VERSION));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[NeoTowns] Migration failed", e);
        }
    }

    // ── Connection helper ───────────────────────────────────────────────────

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection conn) throws SQLException;
    }

    private static void execute(SqlConsumer block) {
        try (Connection conn = dataSource.getConnection()) {
            block.accept(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Database error", e);
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection conn) throws SQLException;
    }

    private static <T> T executeWithResult(SqlFunction<T> block) {
        try (Connection conn = dataSource.getConnection()) {
            return block.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Database error", e);
        }
    }

    // ── Backup ──────────────────────────────────────────────────────────────

    public static void backup(Path backupDir) {
        flush();
        try {
            Files.createDirectories(backupDir);
            String timestamp = String.valueOf(Instant.now().getEpochSecond());

            if ("sqlite".equalsIgnoreCase(dbType) && dbPath != null && Files.exists(dbPath)) {
                Path dbBackup = backupDir.resolve("neotowns_" + timestamp + ".db");
                Files.copy(dbPath, dbBackup, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[NeoTowns] DB backup saved: {}", dbBackup);
            }

            jsonExport(backupDir.resolve("neotowns_" + timestamp + ".json"));
        } catch (IOException e) {
            LOGGER.error("[NeoTowns] Backup failed", e);
        }
    }

    private static void jsonExport(Path jsonPath) {
        try {
            execute(conn -> {
                var export = new LinkedHashMap<String, Object>();
                export.put("exported_at", Instant.now().toString());
                export.put("schema_version", SCHEMA_VERSION);
                export.put("towns", loadAllTowns(conn).size());
                export.put("states", loadAllStates(conn).size());
                export.put("nations", loadAllNations(conn).size());

                String json = new GsonBuilder().setPrettyPrinting().create().toJson(export);
            try {
                Files.writeString(jsonPath, json);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("[NeoTowns] JSON export saved: {}", jsonPath);
            });
        } catch (Exception e) {
            LOGGER.error("[NeoTowns] JSON export failed", e);
        }
    }

    // ── Town CRUD ───────────────────────────────────────────────────────────

    public static void saveTown(TownData town) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO towns
                    (id, name, mayor_uuid, state_id, treasury_world, treasury_x, treasury_y, treasury_z,
                     daily_upkeep, resident_tax, tax_type, max_claims, is_open, is_pvp, is_fire, is_mob_spawn,
                     motd, perms_bitmask, founded_day, lead_title)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Mayor')
                """)) {
                ps.setString(1, town.id().toString());
                ps.setString(2, town.name());
                ps.setString(3, town.mayorUUID().toString());
                ps.setString(4, town.stateId() != null ? town.stateId().toString() : null);
                ps.setString(5, town.treasuryWorld().location().toString());
                ps.setInt(6, town.treasuryChestPos().getX());
                ps.setInt(7, town.treasuryChestPos().getY());
                ps.setInt(8, town.treasuryChestPos().getZ());
                ps.setLong(9, town.dailyUpkeepPerChunk());
                ps.setLong(10, town.residentTaxEmeralds());
                ps.setString(11, town.residentTaxType().name());
                ps.setInt(12, town.maxClaims());
                ps.setInt(13, town.isOpen() ? 1 : 0);
                ps.setInt(14, town.isPvpEnabled() ? 1 : 0);
                ps.setInt(15, town.isFireSpread() ? 1 : 0);
                ps.setInt(16, town.isMobSpawn() ? 1 : 0);
                ps.setString(17, town.motd());
                ps.setLong(18, town.perms().toBitmask());
                ps.setLong(19, town.foundedEpochDay());
                ps.executeUpdate();
            }

            // residents
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM town_residents WHERE town_id = ?")) {
                ps.setString(1, town.id().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO town_residents (town_id, player_uuid, role) VALUES (?,?,?)")) {
                for (UUID resident : town.residentUUIDs()) {
                    ps.setString(1, town.id().toString());
                    ps.setString(2, resident.toString());
                    ps.setString(3, town.isMayor(resident) ? "MAYOR"
                        : town.isAssistant(resident) ? "ASSISTANT" : "RESIDENT");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }));
    }

    public static void deleteTown(UUID id) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM towns WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        }));
    }

    public static TownData loadTown(UUID id) {
        return executeWithResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM towns WHERE id = ?")) {
                ps.setString(1, id.toString());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null;
                return mapTown(rs, conn);
            }
        });
    }

    public static List<TownData> loadAllTowns() {
        return executeWithResult(conn -> loadAllTowns(conn));
    }

    private static List<TownData> loadAllTowns(Connection conn) throws SQLException {
        List<TownData> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM towns")) {
            while (rs.next()) {
                result.add(mapTown(rs, conn));
            }
        }
        return result;
    }

    private static TownData mapTown(ResultSet rs, Connection conn) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String stateIdStr = rs.getString("state_id");
        NTId stateId = stateIdStr != null ? NTId.fromString(stateIdStr) : null;

        BlockPos treasuryPos = new BlockPos(
            rs.getInt("treasury_x"),
            rs.getInt("treasury_y"),
            rs.getInt("treasury_z")
        );
        ResourceKey<Level> treasuryWorld = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse(rs.getString("treasury_world"))
        );

        // residents
        Set<UUID> residents = new HashSet<>();
        Set<UUID> assistants = new HashSet<>();
        UUID mayorUuid = UUID.fromString(rs.getString("mayor_uuid"));
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_uuid, role FROM town_residents WHERE town_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet rr = ps.executeQuery();
            while (rr.next()) {
                UUID puid = UUID.fromString(rr.getString("player_uuid"));
                String role = rr.getString("role");
                residents.add(puid);
                if ("ASSISTANT".equals(role)) assistants.add(puid);
            }
        }

        // plots / chunk claims
        Map<String, PlotData> plots = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM chunk_claims WHERE town_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet cr = ps.executeQuery();
            while (cr.next()) {
                PlotData plot = mapPlot(cr, id);
                plots.put(cr.getString("dimension") + "|" + cr.getInt("chunk_x") + "|" + cr.getInt("chunk_z"), plot);
            }
        }

        return new TownData(
            new NTId(id), rs.getString("name"), mayorUuid,
            residents, assistants, stateId,
            treasuryPos, treasuryWorld, 0L,
            rs.getLong("daily_upkeep"), rs.getLong("resident_tax"),
            TaxType.valueOf(rs.getString("tax_type")),
            rs.getInt("max_claims"),
            rs.getInt("is_open") == 1,
            rs.getInt("is_pvp") == 1,
            rs.getInt("is_fire") == 1,
            rs.getInt("is_mob_spawn") == 1,
            plots, rs.getLong("founded_day"),
            rs.getString("motd"),
            TownPerms.fromBitmask(rs.getLong("perms_bitmask"))
        );
    }

    // ── Chunk Claims ────────────────────────────────────────────────────────

    public static void setChunkOwner(String dimension, ChunkPos pos, UUID townId) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO chunk_claims (dimension, chunk_x, chunk_z, town_id)
                VALUES (?,?,?,?)
                """)) {
                ps.setString(1, dimension);
                ps.setInt(2, pos.x);
                ps.setInt(3, pos.z);
                ps.setString(4, townId.toString());
                ps.executeUpdate();
            }
        }));
    }

    public static void clearChunkOwner(String dimension, ChunkPos pos) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM chunk_claims WHERE dimension = ? AND chunk_x = ? AND chunk_z = ?")) {
                ps.setString(1, dimension);
                ps.setInt(2, pos.x);
                ps.setInt(3, pos.z);
                ps.executeUpdate();
            }
        }));
    }

    public static Map<String, UUID> loadAllChunkClaims() {
        return executeWithResult(conn -> {
            Map<String, UUID> result = new HashMap<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM chunk_claims")) {
                while (rs.next()) {
                    String key = rs.getString("dimension") + "|" + rs.getLong("chunk_x") + "|" + rs.getLong("chunk_z");
                    result.put(key, UUID.fromString(rs.getString("town_id")));
                }
            }
            return result;
        });
    }

    private static PlotData mapPlot(ResultSet rs, UUID townId) throws SQLException {
        return new PlotData(
            new ChunkPos(rs.getInt("chunk_x"), rs.getInt("chunk_z")),
            ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(rs.getString("dimension"))
            ),
            new NTId(townId),
            PlotType.valueOf(rs.getString("plot_type")),
            rs.getString("owner_uuid") != null ? UUID.fromString(rs.getString("owner_uuid")) : null,
            rs.getLong("sale_price"),
            rs.getInt("is_embassy") == 1
        );
    }

    // ── Allies ──────────────────────────────────────────────────────────────

    public static void saveAlly(UUID townId, UUID allyId) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO town_allies (town_id, ally_id) VALUES (?,?)")) {
                ps.setString(1, townId.toString());
                ps.setString(2, allyId.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO town_allies (town_id, ally_id) VALUES (?,?)")) {
                ps.setString(1, allyId.toString());
                ps.setString(2, townId.toString());
                ps.executeUpdate();
            }
        }));
    }

    public static void removeAlly(UUID townId, UUID allyId) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM town_allies WHERE (town_id = ? AND ally_id = ?) OR (town_id = ? AND ally_id = ?)")) {
                ps.setString(1, townId.toString());
                ps.setString(2, allyId.toString());
                ps.setString(3, allyId.toString());
                ps.setString(4, townId.toString());
                ps.executeUpdate();
            }
        }));
    }

    // ── State CRUD ──────────────────────────────────────────────────────────

    public static void saveState(StateData state) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO states
                    (id, name, chancellor_uuid, nation_id, treasury_world, treasury_x, treasury_y, treasury_z,
                     state_tax, tax_type, gov_type, constitution, founded_day, lead_title)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'Chancellor')
                """)) {
                ps.setString(1, state.id().toString());
                ps.setString(2, state.name());
                ps.setString(3, state.chancellorUUID().toString());
                ps.setString(4, state.nationId() != null ? state.nationId().toString() : null);
                ps.setString(5, state.treasuryWorld().location().toString());
                ps.setInt(6, state.treasuryChestPos().getX());
                ps.setInt(7, state.treasuryChestPos().getY());
                ps.setInt(8, state.treasuryChestPos().getZ());
                ps.setLong(9, state.stateTaxEmeralds());
                ps.setString(10, state.stateTaxType().name());
                ps.setString(11, state.governmentType().name());
                ps.setString(12, state.constitution());
                ps.setLong(13, state.foundedEpochDay());
                ps.executeUpdate();
            }

            // laws
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM state_laws WHERE state_id = ?")) {
                ps.setString(1, state.id().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO state_laws (state_id, law_name, law_body) VALUES (?,?,?)")) {
                for (var entry : state.laws().entrySet()) {
                    ps.setString(1, state.id().toString());
                    ps.setString(2, entry.getKey());
                    ps.setString(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // cabinet
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM state_cabinet WHERE state_id = ?")) {
                ps.setString(1, state.id().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO state_cabinet (state_id, player_uuid, role_title) VALUES (?,?,?)")) {
                for (var entry : state.cabinet().entrySet()) {
                    ps.setString(1, state.id().toString());
                    ps.setString(2, entry.getKey().toString());
                    ps.setString(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }));
    }

    public static void deleteState(UUID id) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM states WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        }));
    }

    public static StateData loadState(UUID id) {
        return executeWithResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM states WHERE id = ?")) {
                ps.setString(1, id.toString());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null;
                return mapState(rs, conn);
            }
        });
    }

    public static List<StateData> loadAllStates() {
        return executeWithResult(conn -> loadAllStates(conn));
    }

    private static List<StateData> loadAllStates(Connection conn) throws SQLException {
        List<StateData> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM states")) {
            while (rs.next()) result.add(mapState(rs, conn));
        }
        return result;
    }

    private static StateData mapState(ResultSet rs, Connection conn) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));

        // townIds
        Set<NTId> townIds = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM towns WHERE state_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet tr = ps.executeQuery();
            while (tr.next()) townIds.add(NTId.fromString(tr.getString("id")));
        }

        String nationIdStr = rs.getString("nation_id");
        NTId nationId = nationIdStr != null ? NTId.fromString(nationIdStr) : null;

        // laws
        Map<String, String> laws = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT law_name, law_body FROM state_laws WHERE state_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet lr = ps.executeQuery();
            while (lr.next()) laws.put(lr.getString("law_name"), lr.getString("law_body"));
        }

        // cabinet
        Map<UUID, String> cabinet = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_uuid, role_title FROM state_cabinet WHERE state_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet cr = ps.executeQuery();
            while (cr.next()) cabinet.put(UUID.fromString(cr.getString("player_uuid")), cr.getString("role_title"));
        }

        return new StateData(
            new NTId(id), rs.getString("name"),
            UUID.fromString(rs.getString("chancellor_uuid")),
            townIds, nationId,
            new BlockPos(rs.getInt("treasury_x"), rs.getInt("treasury_y"), rs.getInt("treasury_z")),
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(rs.getString("treasury_world"))),
            rs.getLong("state_tax"),
            TaxType.valueOf(rs.getString("tax_type")),
            net.neotowns.model.enums.GovernmentType.valueOf(rs.getString("gov_type")),
            laws, rs.getString("constitution"), cabinet,
            rs.getLong("founded_day")
        );
    }

    // ── Nation CRUD ─────────────────────────────────────────────────────────

    public static void saveNation(NationData nation) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO nations
                    (id, name, leader_uuid, treasury_world, treasury_x, treasury_y, treasury_z,
                     nation_tax, tax_type, gov_type, constitution, ideology, anthem, founded_day, lead_title)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Emperor')
                """)) {
                ps.setString(1, nation.id().toString());
                ps.setString(2, nation.name());
                ps.setString(3, nation.leaderUUID().toString());
                ps.setString(4, nation.treasuryWorld().location().toString());
                ps.setInt(5, nation.treasuryChestPos().getX());
                ps.setInt(6, nation.treasuryChestPos().getY());
                ps.setInt(7, nation.treasuryChestPos().getZ());
                ps.setLong(8, nation.nationTaxEmeralds());
                ps.setString(9, nation.nationTaxType().name());
                ps.setString(10, nation.governmentType().name());
                ps.setString(11, nation.constitution());
                ps.setString(12, nation.ideology());
                ps.setString(13, nation.anthem());
                ps.setLong(14, nation.foundedEpochDay());
                ps.executeUpdate();
            }

            // diplomacy
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM nation_diplomacy WHERE nation_a = ?")) {
                ps.setString(1, nation.id().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nation_diplomacy (nation_a, nation_b, status) VALUES (?,?,?)")) {
                for (var entry : nation.diplomacy().relations().entrySet()) {
                    ps.setString(1, nation.id().toString());
                    ps.setString(2, entry.getKey().toString());
                    ps.setString(3, entry.getValue().name());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // laws
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM nation_laws WHERE nation_id = ?")) {
                ps.setString(1, nation.id().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nation_laws (nation_id, law_name, law_body) VALUES (?,?,?)")) {
                for (var entry : nation.laws().entrySet()) {
                    ps.setString(1, nation.id().toString());
                    ps.setString(2, entry.getKey());
                    ps.setString(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // cabinet
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM nation_cabinet WHERE nation_id = ?")) {
                ps.setString(1, nation.id().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nation_cabinet (nation_id, player_uuid, role_title) VALUES (?,?,?)")) {
                for (var entry : nation.cabinet().entrySet()) {
                    ps.setString(1, nation.id().toString());
                    ps.setString(2, entry.getKey().toString());
                    ps.setString(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }));
    }

    public static void deleteNation(UUID id) {
        async(() -> execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM nations WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        }));
    }

    public static NationData loadNation(UUID id) {
        return executeWithResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM nations WHERE id = ?")) {
                ps.setString(1, id.toString());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null;
                return mapNation(rs, conn);
            }
        });
    }

    public static List<NationData> loadAllNations() {
        return executeWithResult(conn -> loadAllNations(conn));
    }

    private static List<NationData> loadAllNations(Connection conn) throws SQLException {
        List<NationData> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM nations")) {
            while (rs.next()) result.add(mapNation(rs, conn));
        }
        return result;
    }

    private static NationData mapNation(ResultSet rs, Connection conn) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));

        // stateIds
        Set<NTId> stateIds = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM states WHERE nation_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet sr = ps.executeQuery();
            while (sr.next()) stateIds.add(NTId.fromString(sr.getString("id")));
        }

        // diplomacy
        Map<NTId, net.neotowns.model.enums.DiplomacyStatus> relations = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nation_b, status FROM nation_diplomacy WHERE nation_a = ?")) {
            ps.setString(1, id.toString());
            ResultSet dr = ps.executeQuery();
            while (dr.next())
                relations.put(NTId.fromString(dr.getString("nation_b")),
                    net.neotowns.model.enums.DiplomacyStatus.valueOf(dr.getString("status")));
        }

        // laws
        Map<String, String> laws = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT law_name, law_body FROM nation_laws WHERE nation_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet lr = ps.executeQuery();
            while (lr.next()) laws.put(lr.getString("law_name"), lr.getString("law_body"));
        }

        // cabinet
        Map<UUID, String> cabinet = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_uuid, role_title FROM nation_cabinet WHERE nation_id = ?")) {
            ps.setString(1, id.toString());
            ResultSet cr = ps.executeQuery();
            while (cr.next()) cabinet.put(UUID.fromString(cr.getString("player_uuid")), cr.getString("role_title"));
        }

        return new NationData(
            new NTId(id), rs.getString("name"),
            UUID.fromString(rs.getString("leader_uuid")),
            stateIds,
            new BlockPos(rs.getInt("treasury_x"), rs.getInt("treasury_y"), rs.getInt("treasury_z")),
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(rs.getString("treasury_world"))),
            rs.getLong("nation_tax"),
            TaxType.valueOf(rs.getString("tax_type")),
            new DiplomacyMap(relations),
            net.neotowns.model.enums.GovernmentType.valueOf(rs.getString("gov_type")),
            laws, rs.getString("constitution"), cabinet,
            rs.getString("ideology"), rs.getString("anthem"),
            rs.getLong("founded_day")
        );
    }

    // ── Result helper ───────────────────────────────────────────────────────
}
