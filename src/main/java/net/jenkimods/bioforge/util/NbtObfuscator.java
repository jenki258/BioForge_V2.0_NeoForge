package net.jenkimods.bioforge.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class NbtObfuscator {
    private NbtObfuscator() {}

    private static final String KEY_SALT_HI  = "bf_s0";
    private static final String KEY_SALT_LO  = "bf_s1";
    private static final String KEY_FLAG     = "bf_f";
    private static final String KEY_PAYLOAD  = "bf_p";
    private static final String KEY_CHECKSUM = "bf_c";

    private static final String KEY_INF_SALT_HI  = "bf_is0";
    private static final String KEY_INF_SALT_LO  = "bf_is1";
    private static final String KEY_INF_FLAG     = "bf_if";
    private static final String KEY_INF_PAYLOAD  = "bf_ip";
    private static final String KEY_INF_CHECKSUM = "bf_ic";
    private static final String KEY_NAMED_ROOT = "bf_o";

    private static final long XOR_MASK_A = 0x42694666F726765L;
    private static final long XOR_MASK_B = 0x426C6F6F64480000L;

    public static void write(CompoundTag tag, int amount, String type, String source, UUID subjectUUID) {
        UUID salt  = UUID.randomUUID();
        long rawHi = salt.getMostSignificantBits();
        long rawLo = salt.getLeastSignificantBits();
        writeInternal(tag, amount + "|" + type + "|" + source + "|" + (subjectUUID != null ? subjectUUID : ""),
                rawHi, rawLo, KEY_SALT_HI, KEY_SALT_LO, KEY_FLAG, KEY_PAYLOAD, KEY_CHECKSUM);
    }

    @Nullable
    public static ObfuscatedData read(CompoundTag tag) {
        if (!hasData(tag)) return null;
        long rawHi = tag.getLong(KEY_SALT_HI) ^ XOR_MASK_A;
        long rawLo = tag.getLong(KEY_SALT_LO) ^ XOR_MASK_B;
        byte[] payload = tag.getByteArray(KEY_PAYLOAD);
        if (payload.length == 0) return null;
        if (computeChecksum(payload, rawHi) != tag.getInt(KEY_CHECKSUM)) return null;
        byte[] decrypted = xorEncrypt(payload, rawHi, rawLo);
        String raw = new String(decrypted, StandardCharsets.UTF_8);
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 3) return null;
        try {
            int amount = Integer.parseInt(parts[0]);
            String type = parts[1];
            String source = parts[2];
            UUID uuid = (parts.length == 4 && !parts[3].isEmpty()) ? UUID.fromString(parts[3]) : null;
            return new ObfuscatedData(amount, type, source, uuid);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasData(CompoundTag tag) {
        if (!tag.contains(KEY_FLAG) || !tag.contains(KEY_SALT_HI)) return false;
        long rawHi = tag.getLong(KEY_SALT_HI) ^ XOR_MASK_A;
        return tag.getInt(KEY_FLAG) == (int)(1L ^ (rawHi & 0xFFFFFFFFL));
    }

    public static void clear(CompoundTag tag) {
        tag.remove(KEY_SALT_HI); tag.remove(KEY_SALT_LO);
        tag.remove(KEY_PAYLOAD); tag.remove(KEY_CHECKSUM); tag.remove(KEY_FLAG);
    }

    public record ObfuscatedData(int amount, String typeName, String sourceName, UUID subjectUUID) {}

    public static void writeInfection(CompoundTag tag, String strain) {
        UUID salt = UUID.randomUUID();
        long rawHi = salt.getMostSignificantBits();
        long rawLo = salt.getLeastSignificantBits();
        writeInternal(tag, strain, rawHi, rawLo, KEY_INF_SALT_HI, KEY_INF_SALT_LO, KEY_INF_FLAG, KEY_INF_PAYLOAD, KEY_INF_CHECKSUM);
    }

    @Nullable
    public static String readInfection(CompoundTag tag) {
        if (!hasInfectionData(tag)) return null;
        long rawHi = tag.getLong(KEY_INF_SALT_HI) ^ XOR_MASK_A;
        long rawLo = tag.getLong(KEY_INF_SALT_LO) ^ XOR_MASK_B;
        byte[] payload = tag.getByteArray(KEY_INF_PAYLOAD);
        if (payload.length == 0) return null;
        if (computeChecksum(payload, rawHi) != tag.getInt(KEY_INF_CHECKSUM)) return null;
        byte[] decrypted = xorEncrypt(payload, rawHi, rawLo);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static boolean hasInfectionData(CompoundTag tag) {
        if (!tag.contains(KEY_INF_FLAG) || !tag.contains(KEY_INF_SALT_HI)) return false;
        long rawHi = tag.getLong(KEY_INF_SALT_HI) ^ XOR_MASK_A;
        return tag.getInt(KEY_INF_FLAG) == (int)(1L ^ (rawHi & 0xFFFFFFFFL));
    }

    public static void clearInfection(CompoundTag tag) {
        tag.remove(KEY_INF_SALT_HI); tag.remove(KEY_INF_SALT_LO);
        tag.remove(KEY_INF_PAYLOAD); tag.remove(KEY_INF_CHECKSUM); tag.remove(KEY_INF_FLAG);
    }

    public static void writeString(CompoundTag tag, String payload) {
        UUID salt = UUID.randomUUID();
        long rawHi = salt.getMostSignificantBits();
        long rawLo = salt.getLeastSignificantBits();
        writeInternal(tag, payload, rawHi, rawLo, KEY_SALT_HI, KEY_SALT_LO, KEY_FLAG, KEY_PAYLOAD, KEY_CHECKSUM);
    }





    public static void writeString(CompoundTag tag, String channel, String payload) {
        if (tag == null || channel == null || channel.isBlank()) return;
        CompoundTag channels = tag.contains(KEY_NAMED_ROOT, Tag.TAG_COMPOUND)
                ? tag.getCompound(KEY_NAMED_ROOT) : new CompoundTag();
        CompoundTag encoded = new CompoundTag();
        UUID salt = UUID.randomUUID();
        writeInternal(encoded, payload == null ? "" : payload,
                salt.getMostSignificantBits(), salt.getLeastSignificantBits(),
                KEY_SALT_HI, KEY_SALT_LO, KEY_FLAG, KEY_PAYLOAD, KEY_CHECKSUM);
        channels.put(channel, encoded);
        tag.put(KEY_NAMED_ROOT, channels);
    }

    public static void writeStringDeterministic(CompoundTag tag, String channel,
                                                String payload) {
        if (tag == null || channel == null || channel.isBlank()) return;
        String safePayload = payload == null ? "" : payload;
        CompoundTag channels = tag.contains(KEY_NAMED_ROOT, Tag.TAG_COMPOUND)
                ? tag.getCompound(KEY_NAMED_ROOT) : new CompoundTag();
        CompoundTag encoded = new CompoundTag();
        UUID salt = deriveSalt(channel + '|' + safePayload);
        writeInternal(encoded, safePayload,
                salt.getMostSignificantBits(), salt.getLeastSignificantBits(),
                KEY_SALT_HI, KEY_SALT_LO, KEY_FLAG, KEY_PAYLOAD, KEY_CHECKSUM);
        channels.put(channel, encoded);
        tag.put(KEY_NAMED_ROOT, channels);
    }

    @Nullable
    public static String readString(CompoundTag tag, String channel) {
        if (tag == null || channel == null || channel.isBlank()
                || !tag.contains(KEY_NAMED_ROOT, Tag.TAG_COMPOUND)) return null;
        CompoundTag channels = tag.getCompound(KEY_NAMED_ROOT);
        if (!channels.contains(channel, Tag.TAG_COMPOUND)) return null;
        return readString(channels.getCompound(channel));
    }

    public static boolean hasData(CompoundTag tag, String channel) {
        return readString(tag, channel) != null;
    }

    public static void clear(CompoundTag tag, String channel) {
        if (tag == null || !tag.contains(KEY_NAMED_ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag channels = tag.getCompound(KEY_NAMED_ROOT);
        channels.remove(channel);
        if (channels.isEmpty()) tag.remove(KEY_NAMED_ROOT);
        else tag.put(KEY_NAMED_ROOT, channels);
    }

    public static void writeCompound(CompoundTag tag, String channel, CompoundTag payload) {
        writeString(tag, channel, payload == null ? "{}" : payload.toString());
    }

    public static void writeCompoundDeterministic(CompoundTag tag, String channel,
                                                  CompoundTag payload) {
        writeStringDeterministic(tag, channel,
                payload == null ? "{}" : payload.toString());
    }

    @Nullable
    public static CompoundTag readCompound(CompoundTag tag, String channel) {
        String payload = readString(tag, channel);
        if (payload == null || payload.isBlank()) return null;
        try {
            return TagParser.parseTag(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void writeStringDeterministic(CompoundTag tag, String payload) {
        UUID salt = deriveSalt(payload);
        long rawHi = salt.getMostSignificantBits();
        long rawLo = salt.getLeastSignificantBits();
        writeInternal(tag, payload, rawHi, rawLo, KEY_SALT_HI, KEY_SALT_LO, KEY_FLAG, KEY_PAYLOAD, KEY_CHECKSUM);
    }

    @Nullable
    public static String readString(CompoundTag tag) {
        if (!hasData(tag)) return null;
        long rawHi = tag.getLong(KEY_SALT_HI) ^ XOR_MASK_A;
        long rawLo = tag.getLong(KEY_SALT_LO) ^ XOR_MASK_B;
        byte[] payload = tag.getByteArray(KEY_PAYLOAD);
        if (payload.length == 0) return null;
        if (computeChecksum(payload, rawHi) != tag.getInt(KEY_CHECKSUM)) return null;
        byte[] decrypted = xorEncrypt(payload, rawHi, rawLo);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static void write(ItemStack stack, int amount, String type, String source,
                             UUID subjectUUID) {
        StackData.update(stack, tag -> write(tag, amount, type, source, subjectUUID));
    }

    @Nullable
    public static ObfuscatedData read(ItemStack stack) {
        return read(StackData.copy(stack));
    }

    public static boolean hasData(ItemStack stack) {
        return hasData(StackData.copy(stack));
    }

    public static void clear(ItemStack stack) {
        StackData.update(stack, NbtObfuscator::clear);
    }

    public static void writeInfection(ItemStack stack, String strain) {
        StackData.update(stack, tag -> writeInfection(tag, strain));
    }

    @Nullable
    public static String readInfection(ItemStack stack) {
        return readInfection(StackData.copy(stack));
    }

    public static boolean hasInfectionData(ItemStack stack) {
        return hasInfectionData(StackData.copy(stack));
    }

    public static void clearInfection(ItemStack stack) {
        StackData.update(stack, NbtObfuscator::clearInfection);
    }

    public static void writeString(ItemStack stack, String payload) {
        StackData.update(stack, tag -> writeString(tag, payload));
    }

    public static void writeString(ItemStack stack, String channel, String payload) {
        StackData.update(stack, tag -> writeString(tag, channel, payload));
    }

    public static void writeStringDeterministic(ItemStack stack, String channel,
                                                String payload) {
        StackData.update(stack, tag -> writeStringDeterministic(tag, channel, payload));
    }

    @Nullable
    public static String readString(ItemStack stack, String channel) {
        return readString(StackData.copy(stack), channel);
    }

    public static boolean hasData(ItemStack stack, String channel) {
        return hasData(StackData.copy(stack), channel);
    }

    public static void clear(ItemStack stack, String channel) {
        StackData.update(stack, tag -> clear(tag, channel));
    }

    public static void writeCompound(ItemStack stack, String channel, CompoundTag payload) {
        StackData.update(stack, tag -> writeCompound(tag, channel, payload));
    }

    public static void writeCompoundDeterministic(ItemStack stack, String channel,
                                                  CompoundTag payload) {
        StackData.update(stack, tag -> writeCompoundDeterministic(tag, channel, payload));
    }

    @Nullable
    public static CompoundTag readCompound(ItemStack stack, String channel) {
        return readCompound(StackData.copy(stack), channel);
    }

    public static void writeStringDeterministic(ItemStack stack, String payload) {
        StackData.update(stack, tag -> writeStringDeterministic(tag, payload));
    }

    @Nullable
    public static String readString(ItemStack stack) {
        return readString(StackData.copy(stack));
    }

    private static void writeInternal(CompoundTag tag, String payload, long rawHi, long rawLo,
                                      String keySaltHi, String keySaltLo, String keyFlag,
                                      String keyPayload, String keyChecksum) {
        long saltHi = rawHi ^ XOR_MASK_A;
        long saltLo = rawLo ^ XOR_MASK_B;
        byte[] encrypted = xorEncrypt(payload.getBytes(StandardCharsets.UTF_8), rawHi, rawLo);
        int checksum = computeChecksum(encrypted, rawHi);
        int flag     = (int)(1L ^ (rawHi & 0xFFFFFFFFL));

        tag.putLong(keySaltHi, saltHi);
        tag.putLong(keySaltLo, saltLo);
        tag.putByteArray(keyPayload, encrypted);
        tag.putInt(keyChecksum, checksum);
        tag.putInt(keyFlag, flag);
    }

    private static UUID deriveSalt(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xff);
                lsb = (lsb << 8) | (hash[i + 8] & 0xff);
            }
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException e) {
            return UUID.nameUUIDFromBytes(plaintext.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] xorEncrypt(byte[] data, long keyHi, long keyLo) {
        byte[] result    = new byte[data.length];
        byte[] keyStream = expandKey(keyHi, keyLo, data.length);
        for (int i = 0; i < data.length; i++) result[i] = (byte)(data[i] ^ keyStream[i]);
        return result;
    }

    private static byte[] expandKey(long keyHi, long keyLo, int length) {
        byte[] stream = new byte[length];
        long   state  = keyHi ^ (keyLo << 13) ^ (keyHi >>> 7);
        for (int i = 0; i < length; i++) {
            state ^= (state << 21);
            state ^= (state >>> 35);
            state ^= (state << 4);
            stream[i] = (byte)((state ^ (i * 0x9E3779B9L)) & 0xFF);
        }
        return stream;
    }

    private static int computeChecksum(byte[] data, long key) {
        int sum1 = (int)(key & 0xFFFF);
        int sum2 = (int)((key >> 16) & 0xFFFF);
        for (byte b : data) {
            sum1 = (sum1 + (b & 0xFF)) % 65521;
            sum2 = (sum2 + sum1) % 65521;
        }
        return (sum2 << 16) | sum1;
    }
}
