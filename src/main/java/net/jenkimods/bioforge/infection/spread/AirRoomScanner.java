package net.jenkimods.bioforge.infection.spread;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class AirRoomScanner {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, LevelCache> CACHE = new WeakHashMap<>();

    private AirRoomScanner() {}

    public static Room scan(ServerLevel level, BlockPos requestedStart) {
        BlockPos start = findAirCell(level, requestedStart);
        long now = level.getGameTime();
        LevelCache levelCache = CACHE.computeIfAbsent(level, ignored -> new LevelCache());
        CachedRoom cached = levelCache.roomsByCell.get(start.asLong());
        if (cached != null && cached.expiresAt() >= now) return cached.room();

        int radius = BioForgeServerConfig.airRoomMaxRadius();
        int maxVolume = BioForgeServerConfig.airRoomMaxVolume();
        LongOpenHashSet cells = new LongOpenHashSet(Math.min(maxVolume, 4096));
        LongOpenHashSet visited = new LongOpenHashSet(Math.min(maxVolume * 2, 8192));
        LongArrayFIFOQueue pending = new LongArrayFIFOQueue(Math.min(maxVolume, 4096));
        long startPacked = start.asLong();
        pending.enqueue(startPacked);
        visited.add(startPacked);

        int minX = start.getX();
        int minY = start.getY();
        int minZ = start.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        boolean outdoors = false;
        long anchor = startPacked;

        while (!pending.isEmpty() && cells.size() < maxVolume) {
            long packed = pending.dequeueLong();
            BlockPos pos = BlockPos.of(packed);
            if (Math.abs(pos.getX() - start.getX()) > radius
                    || Math.abs(pos.getY() - start.getY()) > radius
                    || Math.abs(pos.getZ() - start.getZ()) > radius) {
                outdoors = true;
                continue;
            }
            if (!isAirPassable(level, pos)) continue;
            cells.add(packed);
            if (packed < anchor) anchor = packed;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            if (level.canSeeSky(pos)) outdoors = true;
            for (Direction direction : DIRECTIONS) {
                long next = BlockPos.offset(packed, direction);
                if (visited.add(next)) pending.enqueue(next);
            }
        }
        if (!pending.isEmpty() || cells.size() >= maxVolume) outdoors = true;

        long signature = anchor ^ ((long) cells.size() << 32);
        Room room = new Room(signature, cells,
                new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D),
                outdoors);
        long expiry = now + BioForgeServerConfig.airRoomCacheTicks();
        CachedRoom cacheEntry = new CachedRoom(room, expiry);
        for (long cell : room.cellArray) levelCache.roomsByCell.put(cell, cacheEntry);
        if (levelCache.roomsByCell.size() > maxVolume * 16) {
            maintain(level, now);
        }
        return room;
    }

    public static void maintain(ServerLevel level, long gameTime) {
        LevelCache cache = CACHE.get(level);
        if (cache == null || gameTime - cache.lastCleanup < 200L) return;
        cache.lastCleanup = gameTime;
        cache.roomsByCell.long2ObjectEntrySet().removeIf(
                entry -> entry.getValue().expiresAt() < gameTime);
        if (cache.roomsByCell.isEmpty()) CACHE.remove(level);
    }

    public static void invalidate(ServerLevel level) {
        CACHE.remove(level);
    }

    private static BlockPos findAirCell(ServerLevel level, BlockPos requested) {
        if (isAirPassable(level, requested)) return requested.immutable();
        if (isAirPassable(level, requested.above())) return requested.above().immutable();
        if (isAirPassable(level, requested.below())) return requested.below().immutable();
        return requested.immutable();
    }

    private static boolean isAirPassable(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        if (state.isAir()) return true;
        if (state.hasProperty(BlockStateProperties.OPEN)
                && Boolean.TRUE.equals(state.getValue(BlockStateProperties.OPEN))) return true;
        return state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    public static final class Room {
        private final long signature;
        private final LongOpenHashSet cellLookup;
        private final long[] cellArray;
        private final Set<Long> cellsView;
        private final AABB bounds;
        private final boolean outdoors;

        private Room(long signature, LongOpenHashSet cells, AABB bounds, boolean outdoors) {
            this.signature = signature;
            this.cellLookup = cells;
            this.cellArray = cells.toLongArray();
            this.cellsView = Collections.unmodifiableSet(cells);
            this.bounds = bounds;
            this.outdoors = outdoors;
        }

        public Room(long signature, Set<Long> cells, AABB bounds, boolean outdoors) {
            this(signature, new LongOpenHashSet(cells), bounds, outdoors);
        }

        public long signature() { return signature; }
        public Set<Long> cells() { return cellsView; }
        public AABB bounds() { return bounds; }
        public boolean outdoors() { return outdoors; }
        public int cellCount() { return cellArray.length; }
        public long cellAt(int index) { return cellArray[index]; }

        public boolean contains(BlockPos pos) {
            return cellLookup.contains(pos.asLong());
        }
    }

    private static final class LevelCache {
        private final Long2ObjectOpenHashMap<CachedRoom> roomsByCell =
                new Long2ObjectOpenHashMap<>();
        private long lastCleanup = -200L;
    }

    private record CachedRoom(Room room, long expiresAt) {}
}
