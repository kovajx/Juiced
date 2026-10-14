package dev.juiced.render;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a few kinds of blocks around you (lit campfires, candles, lanterns, playing jukeboxes, leaves)
 * every two seconds, so effects can be attached to them without searching every frame.
 */
public final class NearbyBlocks {
	public static final int CAMPFIRE = 0;
	public static final int CANDLE = 1;
	public static final int LANTERN = 2;
	public static final int JUKEBOX = 3;
	public static final int LEAVES = 4;

	private static final int KINDS = 5;
	private static final int[] CAPACITY = {16, 32, 32, 8, 96};
	private static final int RADIUS = 12;
	private static final long SCAN_INTERVAL_NANOS = 2_000_000_000L;

	private static final int[][] X = new int[KINDS][];
	private static final int[][] Y = new int[KINDS][];
	private static final int[][] Z = new int[KINDS][];
	/** Extra info per block: the leaf color for leaves. */
	private static final int[][] DATA = new int[KINDS][];
	private static final int[] COUNT = new int[KINDS];

	static {
		for (int k = 0; k < KINDS; k++) {
			X[k] = new int[CAPACITY[k]];
			Y[k] = new int[CAPACITY[k]];
			Z[k] = new int[CAPACITY[k]];
			DATA[k] = new int[CAPACITY[k]];
		}
	}

	private static long lastScan = Long.MIN_VALUE / 4;

	private NearbyBlocks() {
	}

	public static int count(int kind) {
		return COUNT[kind];
	}

	public static int x(int kind, int i) {
		return X[kind][i];
	}

	public static int y(int kind, int i) {
		return Y[kind][i];
	}

	public static int z(int kind, int i) {
		return Z[kind][i];
	}

	public static int data(int kind, int i) {
		return DATA[kind][i];
	}

	/** Called by every effect that needs the lists; the scan itself runs at most every two seconds. */
	public static void update(Minecraft minecraft) {
		long now = System.nanoTime();

		if (now - lastScan < SCAN_INTERVAL_NANOS) {
			return;
		}

		lastScan = now;
		scan(minecraft);
	}

	public static void clear() {
		java.util.Arrays.fill(COUNT, 0);
		lastScan = Long.MIN_VALUE / 4;
	}

	private static void scan(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		java.util.Arrays.fill(COUNT, 0);

		if (level == null || player == null) {
			return;
		}

		int px = (int) Math.floor(player.getX());
		int py = (int) Math.floor(player.getY());
		int pz = (int) Math.floor(player.getZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int leavesSeen = 0;
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				for (int dy = -4; dy <= 9; dy++) {
					pos.set(px + dx, py + dy, pz + dz);
					BlockState state = level.getBlockState(pos);

					if (state.isAir()) {
						continue;
					}

					if (state.is(BlockTags.LEAVES)) {
						// Keep a random sample of the leaves (reservoir sampling)
						leavesSeen++;

						if (COUNT[LEAVES] < CAPACITY[LEAVES]) {
							put(LEAVES, pos, leafColor(state));
						} else {
							int slot = random.nextInt(leavesSeen);

							if (slot < CAPACITY[LEAVES]) {
								X[LEAVES][slot] = pos.getX();
								Y[LEAVES][slot] = pos.getY();
								Z[LEAVES][slot] = pos.getZ();
								DATA[LEAVES][slot] = leafColor(state);
							}
						}
					} else if (state.is(BlockTags.CAMPFIRES)) {
						if (lit(state)) {
							put(CAMPFIRE, pos, state.is(Blocks.SOUL_CAMPFIRE) ? 1 : 0);
						}
					} else if (state.is(BlockTags.CANDLES)) {
						if (lit(state)) {
							put(CANDLE, pos, 0);
						}
					} else if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
						put(LANTERN, pos, state.is(Blocks.SOUL_LANTERN) ? 1 : 0);
					} else if (state.is(Blocks.JUKEBOX)) {
						if (state.hasProperty(BlockStateProperties.HAS_RECORD) && state.getValue(BlockStateProperties.HAS_RECORD)) {
							put(JUKEBOX, pos, 0);
						}
					}
				}
			}
		}
	}

	private static boolean lit(BlockState state) {
		return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
	}

	private static void put(int kind, BlockPos pos, int data) {
		int i = COUNT[kind];

		if (i >= CAPACITY[kind]) {
			return;
		}

		X[kind][i] = pos.getX();
		Y[kind][i] = pos.getY();
		Z[kind][i] = pos.getZ();
		DATA[kind][i] = data;
		COUNT[kind] = i + 1;
	}

	/** A fitting color for a leaf falling from this kind of leaves. */
	private static int leafColor(BlockState state) {
		if (state.is(Blocks.CHERRY_LEAVES)) {
			return 0xF4A6C6;
		}

		if (state.is(Blocks.BIRCH_LEAVES)) {
			return 0x86A84E;
		}

		if (state.is(Blocks.SPRUCE_LEAVES)) {
			return 0x4D6B47;
		}

		if (state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
			return 0x6D9A3A;
		}

		if (state.is(Blocks.PALE_OAK_LEAVES)) {
			return 0x9AA090;
		}

		return 0x5E8F3A;
	}

	/** Height of the first free block above the ground (rain and leaves land there); ignores leaves if asked. */
	public static int surface(ClientLevel level, int x, int z, boolean ignoreLeaves) {
		BlockPos pos = new BlockPos(x, 0, z);
		Heightmap.Types type = ignoreLeaves ? Heightmap.Types.MOTION_BLOCKING_NO_LEAVES : Heightmap.Types.MOTION_BLOCKING;
		return level.getChunkAt(pos).getOrCreateHeightmapUnprimed(type).getFirstAvailable(x & 15, z & 15);
	}
}
